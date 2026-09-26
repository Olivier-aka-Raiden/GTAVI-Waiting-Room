package com.gtavi.news;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.quarkus.redis.datasource.RedisDataSource;
import io.quarkus.redis.datasource.sortedset.ZRangeArgs;
import io.quarkus.redis.datasource.value.SetArgs;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import java.time.OffsetDateTime;
import java.util.*;

/** Durable discovery queue, evidence cache, and public news/product projections. */
@ApplicationScoped
public class NewsRepository {
    @Inject RedisDataSource redis;
    @Inject ObjectMapper json;
    @ConfigProperty(name="gtavi.redis.key-prefix") String prefix;
    private String key(String suffix) { return prefix + ":news:" + suffix; }
    public JsonNode read(String suffix) {
        String raw = redis.value(String.class).get(key(suffix));
        try { return raw == null ? null : json.readTree(raw); }
        catch (Exception e) { throw new IllegalStateException("Invalid news record " + suffix, e); }
    }
    public void write(String suffix, JsonNode value) { redis.value(String.class).set(key(suffix), value.toString()); }
    public void delete(String suffix) { redis.key().del(key(suffix)); }
    public void cache(String suffix, JsonNode value) {
        redis.value(String.class).set(key(suffix), value.toString(), new SetArgs().ex(2592000));
    }
    public void discover(String url) {
        if (!OfficialUrlPolicy.crawlable(url)) return;
        String id = OfficialPage.identity(url);
        if (redis.value(String.class).get(key("ignored:" + id)) != null) return;
        redis.value(String.class).setnx(key("url:" + id), url);
        redis.sortedSet(String.class).zadd(key("queue"), new io.quarkus.redis.datasource.sortedset.ZAddArgs().nx(),
            url.contains("/newswire/article/") ? -3 : url.contains("/VI/") ? -2.5 : url.contains("/merchandise/") || url.contains("/products/") ? -2 : -1, id);
    }
    public List<String> due(int limit) {
        if (limit <= 0) return List.of();
        var ids = redis.sortedSet(String.class).zrange(key("queue"), 0, Math.max(99,limit - 1));
        var urls = new ArrayList<String>();
        for (String id : ids) {
            var score = redis.sortedSet(String.class).zscore(key("queue"), id);
            if (score.isPresent() && score.getAsDouble() > System.currentTimeMillis()) break;
            if (score.isPresent()) {
                String url = redis.value(String.class).get(key("url:" + id));
                if (url != null && !OfficialUrlPolicy.crawlable(url) && read("pending:" + id) == null) {
                    ignore(url, "Asset or localized site route; excluded from page monitoring");
                } else if (url != null) urls.add(url);
                if (urls.size() >= limit) break;
            }
        }
        return urls;
    }

    /** Retire polluted entries; queued replay plans must finish before their URLs are retired. */
    public int pruneInvalidPages() {
        JsonNode progress = read("queue-cleanup");
        long offset = progress == null ? 0 : progress.path("offset").asLong();
        var ids = redis.sortedSet(String.class).zrange(key("queue"), offset, offset + 99);
        if (ids.isEmpty()) {
            write("queue-cleanup", json.createObjectNode().put("offset", 0));
            return 0;
        }
        String[] keys = ids.stream().map(id -> key("url:" + id)).toArray(String[]::new);
        var urls = redis.execute("MGET", keys);
        int removed = 0;
        for (int i = 0; i < ids.size(); i++) {
            String url = urls.get(i) == null ? null : urls.get(i).toString();
            if (url == null) {
                redis.sortedSet(String.class).zrem(key("queue"), ids.get(i));
                removed++;
            } else if (!OfficialUrlPolicy.crawlable(url) && read("pending:" + ids.get(i)) == null) {
                ignore(url, "Asset or localized site route; excluded from page monitoring");
                removed++;
            }
        }
        long next = offset + ids.size() - removed;
        write("queue-cleanup", json.createObjectNode().put("offset", next >= queuedCount() ? 0 : next));
        return removed;
    }

    public void ignore(String url, String reason) {
        String id = OfficialPage.identity(url);
        recordCheck(id, "SKIPPED", reason, 86400);
        redis.sortedSet(String.class).zrem(key("queue"), id);
        // An extensionless resource can change into a real page later; discovery may reconsider it tomorrow.
        redis.value(String.class).set(key("ignored:" + id), reason, new SetArgs().ex(86400));
    }

    public void reschedule(String url, int seconds) {
        redis.sortedSet(String.class).zadd(key("queue"), System.currentTimeMillis() + seconds * 1000L, OfficialPage.identity(url));
    }
    public List<JsonNode> list(String kind, int page, int size) {
        int count = Math.clamp(size, 1, 100);
        var result = new ArrayList<JsonNode>();
        for (String id : redis.sortedSet(String.class).zrange(key("index:" + kind),
                (long)Math.max(0,page)*count, (long)Math.max(0,page)*count+count-1, new ZRangeArgs().rev())) {
            JsonNode record = read(kind + ":" + id);
            if (record != null) result.add(record);
        }
        return result;
    }
    public long count(String kind) { return redis.sortedSet(String.class).zcard(key("index:" + kind)); }
    public void upsert(String kind, ObjectNode record) {
        String id = record.path("id").asText();
        if (id.isBlank()) throw new IllegalArgumentException("Record identity required");
        long score;
        try { score = OffsetDateTime.parse(record.path("publishedAt").asText()).toInstant().toEpochMilli(); }
        catch (Exception e) { score = System.currentTimeMillis(); }
        // Keep verified fields when enrichment is incomplete; indexes and records change together.
        redis.execute("EVAL", """
            local oldraw=redis.call('GET',KEYS[1])
            local old=oldraw and cjson.decode(oldraw) or {}
            local next=cjson.decode(ARGV[1])
            for k,v in pairs(next) do
              if v ~= cjson.null and v ~= '' then old[k]=v end
            end
            redis.call('SET',KEYS[1],cjson.encode(old))
            if not redis.call('ZSCORE',KEYS[2],ARGV[3]) then redis.call('ZADD',KEYS[2],ARGV[2],ARGV[3]) end
            return 1
            """, "2", key(kind + ":" + id), key("index:" + kind), record.toString(), Long.toString(score), id);
    }
    public long nextRevision(String id) { return redis.value(Long.class).incr(key("revision:"+id)); }

    public void recordCheck(String id, String status, String reason, int retrySeconds) {
        JsonNode old = read("checks:"+id);
        int attempts = "FAILED".equals(status) ? (old == null ? 1 : old.path("attempts").asInt()+1) : 0;
        ObjectNode check=json.createObjectNode().put("id",id).put("status",status).put("reason",reason)
            .put("attempts",attempts).put("lastAttemptAt",OffsetDateTime.now().toString())
            .put("nextAttemptAt",OffsetDateTime.now().plusSeconds(retrySeconds).toString());
        upsert("checks",check);
        if (old!=null) redis.set(String.class).srem(key("state:"+old.path("status").asText()),id);
        redis.set(String.class).sadd(key("state:"+status),id);
    }
    public long stateCount(String status) { return redis.set(String.class).scard(key("state:"+status)); }
    public long queuedCount() { return redis.sortedSet(String.class).zcard(key("queue")); }

    public String acquire(String source) {
        String token = UUID.randomUUID().toString();
        var result = redis.execute("SET", key("lock:" + source), token, "NX", "EX", "900");
        return result == null ? null : token;
    }
    public void assertOwner(String source, String token) {
        if (!Objects.equals(token, redis.value(String.class).get(key("lock:" + source))))
            throw new IllegalStateException("Monitoring lease expired for " + source);
    }
    public void release(String source, String token) {
        redis.execute("EVAL", "if redis.call('GET',KEYS[1])==ARGV[1] then return redis.call('DEL',KEYS[1]) end return 0",
            "1", key("lock:" + source), token);
    }
}
