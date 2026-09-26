package com.gtavi.persistence;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.gtavi.domain.ChangeEvent;
import com.gtavi.domain.Edition;
import com.gtavi.domain.Game;
import com.gtavi.domain.NotificationDelivery;
import com.gtavi.domain.NotificationDeliveryStatus;
import com.gtavi.domain.RetailOffer;
import com.gtavi.domain.Retailer;
import com.gtavi.domain.Trailer;
import io.quarkus.redis.datasource.RedisDataSource;
import io.quarkus.redis.datasource.hash.HashCommands;
import io.quarkus.redis.datasource.set.SetCommands;
import io.quarkus.redis.datasource.sortedset.ScoreRange;
import io.quarkus.redis.datasource.sortedset.SortedSetCommands;
import io.quarkus.redis.datasource.sortedset.ZRangeArgs;
import io.quarkus.redis.datasource.value.SetArgs;
import io.quarkus.redis.datasource.value.ValueCommands;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * The application's Redis persistence boundary.
 *
 * Records are stored as ordinary JSON strings, so the implementation only
 * depends on core Redis commands supported by Upstash. Sets and sorted sets
 * replace the lookup and time-ordering behavior previously provided by Cypher.
 */
@ApplicationScoped
public class RedisPersistence {

    private static final String DEFAULT_GAME_CODE = "GTA_VI";
    private static final int ATOMIC_WRITE_MAX_RETRIES = 5;
    private static final int SNAPSHOT_CLEANUP_BATCH_SIZE = 250;
    private static final String SAVE_EVENT_AND_OUTBOX_SCRIPT = """
        local existing = redis.call('HGET', KEYS[1], ARGV[1])
        if existing then
          return {0, existing}
        end
        redis.call('HSET', KEYS[1], ARGV[1], ARGV[2])
        redis.call('SET', KEYS[2], ARGV[3])
        if ARGV[5] == '1' then
          redis.call('ZADD', KEYS[3], ARGV[4], ARGV[2])
        end
        for i = 6, #ARGV, 4 do
          redis.call('SET', ARGV[i], ARGV[i + 2])
          redis.call('SADD', KEYS[4], ARGV[i + 1])
          redis.call('SADD', KEYS[5], ARGV[i + 1])
          redis.call('ZADD', KEYS[6], ARGV[i + 3], ARGV[i + 1])
        end
        return {1, ARGV[2]}
        """;
    private static final String UPSERT_OFFER_SCRIPT = """
        local current = redis.call('GET', KEYS[1]) or ''
        if current ~= ARGV[1] then
          return 0
        end
        redis.call('SET', KEYS[1], ARGV[2])
        redis.call('SADD', KEYS[2], ARGV[3])
        redis.call('SADD', KEYS[3], ARGV[3])
        if KEYS[4] ~= KEYS[2] then redis.call('SREM', KEYS[4], ARGV[3]) end
        if KEYS[5] ~= KEYS[3] then redis.call('SREM', KEYS[5], ARGV[3]) end
        return 1
        """;
    private static final String REGISTER_DEVICE_SCRIPT = """
        local raw = redis.call('GET', KEYS[1])
        local device = raw and cjson.decode(raw) or {}
        local previousToken = device.pushToken
        local owner = redis.call('HGET', KEYS[3], ARGV[3])
        if owner and owner ~= ARGV[2] then
          local otherKey = ARGV[1] .. ':device:' .. owner
          local otherRaw = redis.call('GET', otherKey)
          if otherRaw then
            local other = cjson.decode(otherRaw)
            other.active = false
            other.notificationsEnabled = false
            other.updatedAt = ARGV[7]
            redis.call('SET', otherKey, cjson.encode(other))
          end
        end
        if previousToken and previousToken ~= ARGV[3]
            and redis.call('HGET', KEYS[3], previousToken) == ARGV[2] then
          redis.call('HDEL', KEYS[3], previousToken)
        end
        device.installationId = ARGV[2]
        device.pushToken = ARGV[3]
        device.platform = ARGV[4]
        device.locale = ARGV[5]
        device.appVersion = ARGV[6]
        device.notificationsEnabled = true
        device.active = true
        device.lastSeenAt = ARGV[7]
        if not device.createdAt then device.createdAt = ARGV[7] end
        device.updatedAt = ARGV[7]
        redis.call('SET', KEYS[1], cjson.encode(device))
        redis.call('SADD', KEYS[2], ARGV[2])
        redis.call('HSET', KEYS[3], ARGV[3], ARGV[2])
        redis.call('SETNX', KEYS[4], ARGV[8])
        return 1
        """;
    private static final String UPDATE_DEVICE_SCRIPT = """
        local raw = redis.call('GET', KEYS[1])
        if not raw then return 0 end
        local device = cjson.decode(raw)
        if ARGV[3] == '1' then
          local token = ARGV[4]
          local previousToken = device.pushToken
          local owner = redis.call('HGET', KEYS[2], token)
          if owner and owner ~= ARGV[2] then
            local otherKey = ARGV[1] .. ':device:' .. owner
            local otherRaw = redis.call('GET', otherKey)
            if otherRaw then
              local other = cjson.decode(otherRaw)
              other.active = false
              other.notificationsEnabled = false
              other.updatedAt = ARGV[11]
              redis.call('SET', otherKey, cjson.encode(other))
            end
          end
          if previousToken and previousToken ~= token
              and redis.call('HGET', KEYS[2], previousToken) == ARGV[2] then
            redis.call('HDEL', KEYS[2], previousToken)
          end
          device.pushToken = token
          redis.call('HSET', KEYS[2], token, ARGV[2])
        end
        if ARGV[5] == '1' then device.appVersion = ARGV[6] end
        if ARGV[7] == '1' then device.locale = ARGV[8] end
        if ARGV[9] == '1' then device.notificationsEnabled = ARGV[10] == '1' end
        device.lastSeenAt = ARGV[11]
        device.updatedAt = ARGV[11]
        redis.call('SET', KEYS[1], cjson.encode(device))
        return 1
        """;
    private static final String DEACTIVATE_DEVICE_SCRIPT = """
        local raw = redis.call('GET', KEYS[1])
        if not raw then return 0 end
        local device = cjson.decode(raw)
        local token = device.pushToken
        device.active = false
        device.notificationsEnabled = false
        device.updatedAt = ARGV[2]
        redis.call('SET', KEYS[1], cjson.encode(device))
        if token and redis.call('HGET', KEYS[2], token) == ARGV[1] then
          redis.call('HDEL', KEYS[2], token)
        end
        return 1
        """;
    private static final String COMPLETE_DELIVERY_SCRIPT = """
        if redis.call('GET', KEYS[1]) ~= ARGV[1] then
          return 0
        end
        redis.call('SET', KEYS[2], ARGV[2])
        if ARGV[3] == '1' then
          redis.call('ZADD', KEYS[3], ARGV[4], ARGV[5])
        else
          redis.call('ZREM', KEYS[3], ARGV[5])
        end
        redis.call('DEL', KEYS[1])
        return 1
        """;
    private static final List<String> PREFERENCE_FIELDS = List.of(
        "collectorEditionAnnouncement",
        "collectorEditionPreorder",
        "releaseDateChanges",
        "newOfficialTrailers",
        "majorRockstarNews",
        "generalNews",
        "priceChanges",
        "outOfStock",
        "backInStock"
    );

    private final ObjectMapper objectMapper;
    private final RedisDataSource redis;
    private final ValueCommands<String, String> values;
    private final SetCommands<String, String> sets;
    private final SortedSetCommands<String, String> sortedSets;
    private final HashCommands<String, String, String> hashes;
    private final String prefix;

    @Inject
    public RedisPersistence(
        RedisDataSource redis,
        ObjectMapper objectMapper,
        @ConfigProperty(name = "gtavi.redis.key-prefix", defaultValue = "gtavi:v1") String prefix
    ) {
        this.objectMapper = objectMapper;
        this.redis = redis;
        this.values = redis.value(String.class);
        this.sets = redis.set(String.class);
        this.sortedSets = redis.sortedSet(String.class);
        this.hashes = redis.hash(String.class);
        this.prefix = prefix.endsWith(":") ? prefix.substring(0, prefix.length() - 1) : prefix;
    }

    // ---- Games, trailers, editions, retailers and offers ----

    public Game getGame(String code) {
        return read(entityKey("game", code), Game.class);
    }

    public void saveGame(Game game) { values.set(entityKey("game",game.getCode()),toJson(game)); }

    public void saveEdition(Edition edition) {
        values.set(entityKey("edition",edition.getId()),toJson(edition));
        sets.sadd(indexKey("editions","game",edition.getGameCode()),edition.getId());
    }

    public void saveTrailer(Trailer trailer) {
        values.set(entityKey("trailer",trailer.getId()),toJson(trailer));
        sortedSets.zadd(indexKey("trailers","game",trailer.getGameCode()),epoch(trailer.getPublicationDate()),trailer.getId());
    }

    public void saveGameIfAbsent(Game game) {
        writeIfAbsent(entityKey("game", game.getCode()), game);
    }

    public List<Trailer> getTrailers(String gameCode, String mediaType) {
        List<Trailer> trailers = readSortedEntities(
            indexKey("trailers", "game", gameCode), "trailer", Trailer.class);
        if (mediaType == null) return trailers;
        return trailers.stream()
            .filter(trailer -> mediaType.equals(trailer.getMediaType()))
            .toList();
    }

    public void saveTrailerIfAbsent(Trailer trailer) {
        String key = entityKey("trailer", trailer.getId());
        values.setnx(key, toJson(trailer));
        sortedSets.zadd(indexKey("trailers", "game", trailer.getGameCode()),
            epoch(trailer.getPublicationDate()), trailer.getId());
    }

    public List<Edition> getEditions(String gameCode) {
        List<Edition> editions = readSetEntities(
            indexKey("editions", "game", gameCode), "edition", Edition.class);
        editions.sort(Comparator.comparing(Edition::getNormalizedType,
            Comparator.nullsLast(String::compareTo)));
        return editions;
    }

    public List<String> getEditionIds() {
        return new ArrayList<>(sets.smembers(indexKey("editions", "game", DEFAULT_GAME_CODE)));
    }

    public void saveEditionIfAbsent(Edition edition) {
        String key = entityKey("edition", edition.getId());
        values.setnx(key, toJson(edition));
        sets.sadd(indexKey("editions", "game", edition.getGameCode()), edition.getId());
    }

    public List<Retailer> getRetailers() {
        List<Retailer> retailers = readSetEntities(indexKey("retailers"), "retailer", Retailer.class);
        return retailers.stream()
            .filter(Retailer::isEnabled)
            .sorted(Comparator.comparing(Retailer::getName, Comparator.nullsLast(String::compareTo)))
            .toList();
    }

    public void saveRetailerIfAbsent(Retailer retailer) {
        String key = entityKey("retailer", retailer.getCode());
        values.setnx(key, toJson(retailer));
        sets.sadd(indexKey("retailers"), retailer.getCode());
    }

    public List<RetailOffer> getOffers(String editionId) {
        Set<String> ids = sets.smembers(indexKey("offers", "edition", editionId));
        List<RetailOffer> offers = new ArrayList<>();
        for (String id : ids) {
            JsonNode node = readNode(entityKey("offer", id));
            if (node == null || !node.path("active").asBoolean(true)) continue;
            offers.add(convert(node, RetailOffer.class));
        }
        offers = com.gtavi.monitoring.core.OfferIdentity.distinct(offers);
        offers.sort(Comparator
            .comparing(RetailOffer::getRetailerCode, Comparator.nullsLast(String::compareTo))
            .thenComparing(RetailOffer::getPlatform, Comparator.nullsLast(String::compareTo)));
        return offers;
    }

    public void upsertOffer(String id, String editionId, String retailerCode,
                            String platform, String countryCode, BigDecimal price,
                            String currency, String url, String availabilityStatus,
                            boolean preorderAvailable) {
        String key = entityKey("offer", id);
        for (int attempt = 0; attempt < ATOMIC_WRITE_MAX_RETRIES; attempt++) {
            String previousJson = values.get(key);
            JsonNode previous = parseNode(key, previousJson);
            OffsetDateTime now = OffsetDateTime.now();
            BigDecimal observedPrice = price;
            if (observedPrice == null && previous != null && sameText(previous, "currency", currency)
                    && previous.path("price").isNumber()) observedPrice = previous.path("price").decimalValue();
            boolean changed = previous == null
                || !sameText(previous, "price", observedPrice == null ? null : observedPrice.toPlainString())
                || !sameText(previous, "currency", currency)
                || !sameText(previous, "url", url)
                || !sameText(previous, "availabilityStatus", availabilityStatus);

            ObjectNode offer = previous != null && previous.isObject()
                ? ((ObjectNode) previous).deepCopy() : objectMapper.createObjectNode();
            offer.put("id", id);
            offer.put("editionId", editionId);
            offer.put("retailerCode", retailerCode);
            putNullable(offer, "platform", platform);
            putNullable(offer, "countryCode", countryCode);
            if (observedPrice != null) offer.put("price", observedPrice); else offer.putNull("price");
            putNullable(offer, "currency", currency);
            putNullable(offer, "url", url);
            putNullable(offer, "availabilityStatus", availabilityStatus);
            offer.put("preorderAvailable", preorderAvailable);
            offer.put("active", true);
            offer.put("missedChecks", 0);
            offer.put("lastSuccessfulCheckAt", now.toString());
            if (changed) offer.put("lastChangedAt", now.toString());
            if (!offer.hasNonNull("createdAt")) offer.put("createdAt", now.toString());
            offer.put("updatedAt", now.toString());

            String oldEdition = previous == null ? editionId : text(previous, "editionId");
            String oldRetailer = previous == null ? retailerCode : text(previous, "retailerCode");
            int result = evalInteger(UPSERT_OFFER_SCRIPT,
                List.of(key,
                    indexKey("offers", "edition", editionId),
                    indexKey("offers", "retailer", retailerCode),
                    indexKey("offers", "edition", oldEdition == null ? editionId : oldEdition),
                    indexKey("offers", "retailer", oldRetailer == null ? retailerCode : oldRetailer)),
                List.of(previousJson == null ? "" : previousJson, offer.toString(), id));
            if (result == 1) return;
        }
        throw new IllegalStateException("Could not atomically update offer " + id);
    }

    public void deactivateOffer(String id) {
        JsonNode current = readNode(entityKey("offer", id));
        if (current == null || !current.isObject()) return;
        ObjectNode offer = ((ObjectNode) current).deepCopy();
        offer.put("active", false);
        offer.put("updatedAt", OffsetDateTime.now().toString());
        writeNode(entityKey("offer", id), offer);
    }

    public void markMissingOffers(String retailerCode, Set<String> seenOfferIds) {
        for (String id : sets.smembers(indexKey("offers", "retailer", retailerCode))) {
            if (seenOfferIds.contains(id)) continue;
            JsonNode current = readNode(entityKey("offer", id));
            if (current == null || !current.isObject()) continue;
            ObjectNode offer = ((ObjectNode) current).deepCopy();
            if (!com.gtavi.monitoring.core.OfferIdentity.listing(retailerCode, offer.path("url").asText())) {
                deactivateOffer(id);
                continue;
            }
            int missedChecks = offer.path("missedChecks").asInt(0) + 1;
            offer.put("missedChecks", missedChecks);
            if (missedChecks >= 2) offer.put("active", false);
            offer.put("updatedAt", OffsetDateTime.now().toString());
            writeNode(entityKey("offer", id), offer);
        }
    }

    // ---- Change events ----

    public List<ChangeEvent> getEvents(String gameCode, int page, int size) {
        long start = (long) page * size;
        long stop = start + size - 1L;
        List<String> ids = sortedSets.zrange(
            indexKey("events", "game", gameCode, "visible"), start, stop,
            new ZRangeArgs().rev());
        List<ChangeEvent> events = new ArrayList<>();
        for (String id : ids) {
            ChangeEvent event = read(entityKey("event", id), ChangeEvent.class);
            if (event != null) events.add(event);
        }
        return events;
    }

    public long getEventCount(String gameCode) {
        return sortedSets.zcard(indexKey("events", "game", gameCode, "visible"));
    }

    public boolean saveEventIfAbsent(ChangeEvent event) {
        return saveEventAndOutboxIfAbsent(event, List.of());
    }

    public boolean saveEventAndOutboxIfAbsent(
        ChangeEvent event,
        List<NotificationDelivery> deliveries
    ) {
        if (event.getGameCode() == null) event.setGameCode(DEFAULT_GAME_CODE);
        if (event.getDetectedAt() == null) event.setDetectedAt(OffsetDateTime.now());
        if (event.getCreatedAt() == null) event.setCreatedAt(OffsetDateTime.now());
        String deduplicationKey = event.getDeduplicationKey() == null
            ? event.getId() : event.getDeduplicationKey();

        List<String> arguments = new ArrayList<>();
        arguments.add(deduplicationKey);
        arguments.add(event.getId());
        arguments.add(toJson(event));
        arguments.add(Long.toString(epoch(event.getDetectedAt())));
        arguments.add(event.isUserVisible() ? "1" : "0");
        for (NotificationDelivery delivery : deliveries) {
            if (!event.getId().equals(delivery.eventId())) {
                throw new IllegalArgumentException("Delivery event ID does not match event");
            }
            arguments.add(entityKey("delivery", delivery.id()));
            arguments.add(delivery.id());
            arguments.add(toJson(delivery));
            arguments.add(Long.toString(epoch(delivery.nextAttemptAt())));
        }

        var result = eval(SAVE_EVENT_AND_OUTBOX_SCRIPT,
            List.of(
                indexKey("events", "dedup"),
                entityKey("event", event.getId()),
                indexKey("events", "game", event.getGameCode(), "visible"),
                indexKey("deliveries"),
                indexKey("deliveries", "event", event.getId()),
                indexKey("deliveries", "pending")),
            arguments);
        boolean created = result.get(0).toInteger() == 1;
        if (!created) event.setId(result.get(1).toString());
        return created;
    }

    // ---- Source definitions and monitoring snapshots ----

    public void saveSourceDefinitionIfAbsent(Map<String, Object> source) {
        String code = Objects.toString(source.get("code"));
        String key = entityKey("source", code);
        values.setnx(key, toJson(source));
        sets.sadd(indexKey("sources"), code);
    }

    public JsonNode getSourceDefinition(String code) { return readNode(entityKey("source",code)); }

    public MonitoringHealthData getMonitoringHealth() {
        int monitoredSources = 0;
        int healthySources = 0;
        OffsetDateTime lastRunAt = null;
        OffsetDateTime lastSuccessfulAt = null;
        OffsetDateTime oldestRunAt = null;

        for (String sourceCode : sets.smembers(indexKey("sources"))) {
            JsonNode definition = readNode(entityKey("source", sourceCode));
            if (definition == null || !definition.path("enabled").asBoolean(true)) continue;
            monitoredSources++;

            JsonNode latest = latestSnapshot(sourceCode, false);
            if (latest == null) continue;
            OffsetDateTime checkedAt = parseDate(latest, "checkedAt");
            lastRunAt = later(lastRunAt, checkedAt);
            oldestRunAt = earlier(oldestRunAt, checkedAt);
            if (latest.path("successful").asBoolean(false) && !latest.path("normalizedJson").path("degraded").asBoolean(false)) {
                healthySources++;
                lastSuccessfulAt = later(lastSuccessfulAt, checkedAt);
            }
        }

        boolean recent = oldestRunAt != null
            && oldestRunAt.isAfter(OffsetDateTime.now().minusHours(2));
        boolean healthy = monitoredSources > 0
            && healthySources == monitoredSources && recent;
        return new MonitoringHealthData(lastRunAt, lastSuccessfulAt,
            monitoredSources, healthySources, healthy);
    }

    public OffsetDateTime getLatestSnapshotTime(String sourceCode) {
        JsonNode latest = latestSnapshot(sourceCode, false);
        return latest == null ? null : parseDate(latest, "checkedAt");
    }

    public JsonNode getLatestSuccessfulSnapshotData(String sourceCode) {
        JsonNode snapshot = latestSnapshot(sourceCode, true);
        if (snapshot == null) return null;
        JsonNode normalized = snapshot.get("normalizedJson");
        if (normalized == null || normalized.isNull()) return null;
        if (!normalized.isTextual()) return normalized;
        String json = normalized.asText();
        if (json.isBlank()) return null;
        try {
            return objectMapper.readTree(json);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Invalid normalized snapshot JSON for " + sourceCode, e);
        }
    }

    public void saveSnapshot(String sourceCode, String sourceUrl, JsonNode data,
                             String hash, boolean successful, String errorMessage) {
        saveSnapshotAt(sourceCode, sourceUrl, data, hash, successful, errorMessage,
            OffsetDateTime.now());
    }

    String saveSnapshotAt(String sourceCode, String sourceUrl, JsonNode data,
                          String hash, boolean successful, String errorMessage,
                          OffsetDateTime checkedAt) {
        Objects.requireNonNull(checkedAt, "checkedAt");
        String id = checkedAt.toInstant().toEpochMilli() + "-" + UUID.randomUUID();
        ObjectNode snapshot = objectMapper.createObjectNode();
        snapshot.put("id", id);
        snapshot.put("sourceCode", sourceCode);
        putNullable(snapshot, "sourceUrl", sourceUrl);
        snapshot.put("status", successful ? "SUCCESS" : "FAILURE");
        if (data == null) snapshot.putNull("normalizedJson");
        else snapshot.set("normalizedJson", data);
        putNullable(snapshot, "normalizedHash", hash);
        snapshot.put("checkedAt", checkedAt.toString());
        snapshot.put("successful", successful);
        putNullable(snapshot, "errorMessage", errorMessage);

        writeNode(entityKey("snapshot", id), snapshot);
        sortedSets.zadd(indexKey("snapshots", "source", sourceCode), epoch(checkedAt), id);
        if (successful) {
            sortedSets.zadd(indexKey("snapshots", "successful", sourceCode), epoch(checkedAt), id);
        }
        return id;
    }

    /**
     * Replaces full snapshots older than {@code cutoff} with compact daily audit
     * records. The newest successful and newest failed snapshot for every source
     * are always retained so monitoring and diagnosis keep a full reference point.
     */
    public SnapshotCleanupResult cleanupSnapshots(OffsetDateTime cutoff) {
        Objects.requireNonNull(cutoff, "cutoff");

        int sourcesProcessed = 0;
        int snapshotsDeleted = 0;
        int staleIndexEntriesDeleted = 0;
        int dailyHashesUpdated = 0;

        for (String sourceCode : sets.smembers(indexKey("sources"))) {
            String sourceIndex = indexKey("snapshots", "source", sourceCode);
            String successfulIndex = indexKey("snapshots", "successful", sourceCode);
            if (sortedSets.zcard(sourceIndex) == 0) continue;
            sourcesProcessed++;

            Set<String> retainedIds = new HashSet<>();
            String latestSuccessfulId = latestSnapshotId(successfulIndex, true);
            String latestFailedId = latestSnapshotId(sourceIndex, false);
            if (latestSuccessfulId != null) retainedIds.add(latestSuccessfulId);
            if (latestFailedId != null) retainedIds.add(latestFailedId);

            ScoreRange<Double> expiredRange = new ScoreRange<>(
                null, true, (double) epoch(cutoff), false);

            while (true) {
                List<String> expiredIds = sortedSets.zrangebyscore(
                    sourceIndex, expiredRange,
                    new ZRangeArgs().limit(0, SNAPSHOT_CLEANUP_BATCH_SIZE));
                if (expiredIds.isEmpty()) break;

                List<String> deletableIds = expiredIds.stream()
                    .filter(id -> !retainedIds.contains(id))
                    .toList();
                if (deletableIds.isEmpty()) break;

                Map<LocalDate, ObjectNode> dailyHashes = new LinkedHashMap<>();
                List<String> snapshotKeys = new ArrayList<>();
                int existingSnapshots = 0;
                for (String id : deletableIds) {
                    String snapshotKey = entityKey("snapshot", id);
                    JsonNode snapshot = readNode(snapshotKey);
                    snapshotKeys.add(snapshotKey);
                    if (snapshot == null) continue;
                    existingSnapshots++;
                    mergeDailyHash(sourceCode, snapshot, dailyHashes);
                }

                redis.withTransaction(transaction -> {
                    var transactionValues = transaction.value(String.class);
                    var transactionSortedSets = transaction.sortedSet(String.class);
                    for (Map.Entry<LocalDate, ObjectNode> entry : dailyHashes.entrySet()) {
                        LocalDate day = entry.getKey();
                        transactionValues.set(
                            entityKey("snapshot-daily", sourceCode + ":" + day),
                            entry.getValue().toString());
                        transactionSortedSets.zadd(
                            indexKey("snapshots", "daily", sourceCode),
                            day.atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli(),
                            day.toString());
                    }
                    String[] ids = deletableIds.toArray(String[]::new);
                    transactionSortedSets.zrem(sourceIndex, ids);
                    transactionSortedSets.zrem(successfulIndex, ids);
                    transaction.key().del(snapshotKeys.toArray(String[]::new));
                });

                snapshotsDeleted += existingSnapshots;
                staleIndexEntriesDeleted += deletableIds.size() - existingSnapshots;
                dailyHashesUpdated += dailyHashes.size();
            }
        }

        return new SnapshotCleanupResult(cutoff, sourcesProcessed, snapshotsDeleted,
            staleIndexEntriesDeleted, dailyHashesUpdated);
    }

    private JsonNode latestSnapshot(String sourceCode, boolean successfulOnly) {
        String key = successfulOnly
            ? indexKey("snapshots", "successful", sourceCode)
            : indexKey("snapshots", "source", sourceCode);
        List<String> ids = sortedSets.zrange(key, 0, 0, new ZRangeArgs().rev());
        return ids.isEmpty() ? null : readNode(entityKey("snapshot", ids.getFirst()));
    }

    private String latestSnapshotId(String index, boolean successful) {
        long offset = 0;
        while (true) {
            List<String> ids = sortedSets.zrange(index, offset,
                offset + SNAPSHOT_CLEANUP_BATCH_SIZE - 1L, new ZRangeArgs().rev());
            if (ids.isEmpty()) return null;
            for (String id : ids) {
                JsonNode snapshot = readNode(entityKey("snapshot", id));
                if (snapshot != null
                    && snapshot.path("successful").asBoolean(false) == successful) {
                    return id;
                }
            }
            offset += ids.size();
        }
    }

    private void mergeDailyHash(String sourceCode, JsonNode snapshot,
                                Map<LocalDate, ObjectNode> dailyHashes) {
        OffsetDateTime checkedAt = parseDate(snapshot, "checkedAt");
        if (checkedAt == null) return;
        LocalDate day = checkedAt.withOffsetSameInstant(ZoneOffset.UTC).toLocalDate();
        ObjectNode daily = dailyHashes.computeIfAbsent(day, ignored -> {
            JsonNode existing = readNode(entityKey("snapshot-daily", sourceCode + ":" + day));
            if (existing != null && existing.isObject()) {
                return ((ObjectNode) existing).deepCopy();
            }
            ObjectNode created = objectMapper.createObjectNode();
            created.put("sourceCode", sourceCode);
            created.put("date", day.toString());
            return created;
        });

        OffsetDateTime previousLatest = parseDate(daily, "latestCheckedAt");
        if (previousLatest == null || checkedAt.isAfter(previousLatest)) {
            daily.put("latestCheckedAt", checkedAt.toString());
            daily.put("latestStatus", snapshot.path("successful").asBoolean(false)
                ? "SUCCESS" : "FAILURE");
        }

        if (snapshot.path("successful").asBoolean(false)) {
            OffsetDateTime previousSuccessful = parseDate(daily, "latestSuccessfulAt");
            if (previousSuccessful == null || checkedAt.isAfter(previousSuccessful)) {
                daily.put("latestSuccessfulAt", checkedAt.toString());
                putNullable(daily, "latestSuccessfulHash", text(snapshot, "normalizedHash"));
            }
        }
        daily.put("updatedAt", OffsetDateTime.now(ZoneOffset.UTC).toString());
    }

    JsonNode getSnapshot(String id) {
        return readNode(entityKey("snapshot", id));
    }

    JsonNode getDailySnapshotHash(String sourceCode, LocalDate day) {
        return readNode(entityKey("snapshot-daily", sourceCode + ":" + day));
    }

    // ---- Devices, preferences and notification deliveries ----

    public void registerDevice(String installationId, String pushToken,
                               String platform, String locale, String appVersion) {
        OffsetDateTime now = OffsetDateTime.now();
        evalInteger(REGISTER_DEVICE_SCRIPT,
            List.of(
                entityKey("device", installationId),
                indexKey("devices"),
                indexKey("device-tokens"),
                entityKey("preferences", installationId)),
            List.of(prefix, installationId, pushToken, platform, locale, appVersion,
                now.toString(), newPreferences(now).toString()));
    }

    public boolean updateDevice(String installationId, Map<String, Object> changes) {
        String token = changes.get("pushToken") instanceof String value && !value.isBlank()
            ? value : "";
        String appVersion = changes.get("appVersion") instanceof String value ? value : "";
        String locale = changes.get("locale") instanceof String value ? value : "";
        Boolean notificationsEnabled = changes.get("notificationsEnabled") instanceof Boolean value
            ? value : null;
        int result = evalInteger(UPDATE_DEVICE_SCRIPT,
            List.of(entityKey("device", installationId), indexKey("device-tokens")),
            List.of(
                prefix,
                installationId,
                token.isEmpty() ? "0" : "1",
                token,
                appVersion.isEmpty() ? "0" : "1",
                appVersion,
                locale.isEmpty() ? "0" : "1",
                locale,
                notificationsEnabled == null ? "0" : "1",
                Boolean.TRUE.equals(notificationsEnabled) ? "1" : "0",
                OffsetDateTime.now().toString()));
        return result == 1;
    }

    public Map<String, Boolean> getOrCreatePreferences(String installationId) {
        ensureDeviceStub(installationId);
        ObjectNode preferences = ensurePreferences(installationId);
        return preferenceMap(preferences);
    }

    public void updatePreferences(String installationId, Map<String, Object> changes) {
        ensureDeviceStub(installationId);
        ObjectNode preferences = ensurePreferences(installationId);
        for (String field : PREFERENCE_FIELDS) {
            Object value = changes.get(field);
            if (value instanceof Boolean enabled) preferences.put(field, enabled);
        }
        preferences.put("updatedAt", OffsetDateTime.now().toString());
        writeNode(entityKey("preferences", installationId), preferences);
    }

    public List<DeviceTokenRecord> getEligibleDevices(String preferenceField) {
        List<DeviceTokenRecord> result = new ArrayList<>();
        for (String installationId : sets.smembers(indexKey("devices"))) {
            JsonNode device = readNode(entityKey("device", installationId));
            if (device == null
                || !device.path("active").asBoolean(true)
                || !device.path("notificationsEnabled").asBoolean(false)) continue;
            String token = text(device, "pushToken");
            if (token == null || token.isBlank()) continue;
            JsonNode preferences = readNode(entityKey("preferences", installationId));
            if (preferences == null || preferences.path(preferenceField).asBoolean(defaultPreference(preferenceField))) {
                result.add(new DeviceTokenRecord(installationId, token));
            }
        }
        return result;
    }

    public boolean isDeviceNotificationsEnabled(String installationId) {
        JsonNode device = readNode(entityKey("device", installationId));
        return device != null && device.path("notificationsEnabled").asBoolean(false);
    }

    public void deactivateDevice(String installationId) {
        evalInteger(DEACTIVATE_DEVICE_SCRIPT,
            List.of(entityKey("device", installationId), indexKey("device-tokens")),
            List.of(installationId, OffsetDateTime.now().toString()));
    }

    public DeviceTokenRecord getActiveDeviceToken(String installationId) {
        JsonNode device = readNode(entityKey("device", installationId));
        if (device == null
            || !device.path("active").asBoolean(true)
            || !device.path("notificationsEnabled").asBoolean(false)) return null;
        String token = text(device, "pushToken");
        return token == null || token.isBlank()
            ? null : new DeviceTokenRecord(installationId, token);
    }

    public List<ClaimedNotificationDelivery> claimDueDeliveries(
        OffsetDateTime now,
        int limit,
        Duration leaseDuration
    ) {
        if (limit < 1) return List.of();
        ScoreRange<Double> dueRange = new ScoreRange<>(
            null, true, (double) epoch(now), true);
        List<String> ids = sortedSets.zrangebyscore(
            indexKey("deliveries", "pending"), dueRange,
            new ZRangeArgs().limit(0, limit));
        List<ClaimedNotificationDelivery> claimed = new ArrayList<>();
        for (String id : ids) {
            NotificationDelivery delivery = read(
                entityKey("delivery", id), NotificationDelivery.class);
            if (delivery == null || (delivery.status() != NotificationDeliveryStatus.QUEUED
                && delivery.status() != NotificationDeliveryStatus.RETRY)) {
                sortedSets.zrem(indexKey("deliveries", "pending"), id);
                continue;
            }
            String claimId = UUID.randomUUID().toString();
            boolean acquired = values.setAndChanged(
                entityKey("delivery-claim", id), claimId,
                new SetArgs().nx().ex(leaseDuration));
            if (acquired) claimed.add(new ClaimedNotificationDelivery(delivery, claimId));
        }
        return claimed;
    }

    public NotificationDelivery getNotificationDelivery(
        String eventId,
        String installationId
    ) {
        return read(entityKey("delivery", eventId + ":" + installationId),
            NotificationDelivery.class);
    }

    public boolean markDeliverySent(ClaimedNotificationDelivery claimed,
                                    int attempts, String providerMessageId,
                                    OffsetDateTime now) {
        return transitionDelivery(claimed, NotificationDeliveryStatus.SENT,
            attempts, null, providerMessageId, now, null);
    }

    public boolean markDeliveryForRetry(ClaimedNotificationDelivery claimed,
                                        int attempts, String error,
                                        OffsetDateTime nextAttemptAt,
                                        OffsetDateTime now) {
        return transitionDelivery(claimed, NotificationDeliveryStatus.RETRY,
            attempts, error, null, now, nextAttemptAt);
    }

    public boolean markDeliveryInvalidToken(ClaimedNotificationDelivery claimed,
                                            int attempts, OffsetDateTime now) {
        return transitionDelivery(claimed, NotificationDeliveryStatus.INVALID_TOKEN,
            attempts, "FCM rejected the registration token", null, now, null);
    }

    public boolean markDeliveryDead(ClaimedNotificationDelivery claimed,
                                    int attempts, String error,
                                    OffsetDateTime now) {
        return transitionDelivery(claimed, NotificationDeliveryStatus.DEAD,
            attempts, error, null, now, null);
    }

    private boolean transitionDelivery(
        ClaimedNotificationDelivery claimed,
        NotificationDeliveryStatus status,
        int attempts,
        String error,
        String providerMessageId,
        OffsetDateTime now,
        OffsetDateTime nextAttemptAt
    ) {
        NotificationDelivery current = claimed.delivery();
        NotificationDelivery updated = new NotificationDelivery(
            current.id(),
            current.eventId(),
            current.installationId(),
            current.title(),
            current.body(),
            current.data(),
            status,
            attempts,
            nextAttemptAt,
            providerMessageId,
            error,
            status == NotificationDeliveryStatus.SENT ? now : null,
            current.createdAt(),
            now);
        boolean retry = status == NotificationDeliveryStatus.RETRY;
        int result = evalInteger(COMPLETE_DELIVERY_SCRIPT,
            List.of(
                entityKey("delivery-claim", current.id()),
                entityKey("delivery", current.id()),
                indexKey("deliveries", "pending")),
            List.of(
                claimed.claimId(),
                toJson(updated),
                retry ? "1" : "0",
                retry ? Long.toString(epoch(nextAttemptAt)) : "0",
                current.id()));
        return result == 1;
    }

    // ---- Internal helpers ----

    private void ensureDeviceStub(String installationId) {
        String key = entityKey("device", installationId);
        JsonNode current = readNode(key);
        OffsetDateTime now = OffsetDateTime.now();
        if (current == null) {
            ObjectNode device = objectMapper.createObjectNode();
            device.put("installationId", installationId);
            device.put("platform", "UNKNOWN");
            device.put("notificationsEnabled", false);
            device.put("active", true);
            device.put("lastSeenAt", now.toString());
            device.put("createdAt", now.toString());
            device.put("updatedAt", now.toString());
            writeNode(key, device);
            sets.sadd(indexKey("devices"), installationId);
        } else if (current.isObject()) {
            ObjectNode device = ((ObjectNode) current).deepCopy();
            device.put("lastSeenAt", now.toString());
            device.put("updatedAt", now.toString());
            writeNode(key, device);
        }
    }

    private ObjectNode ensurePreferences(String installationId) {
        String key = entityKey("preferences", installationId);
        JsonNode current = readNode(key);
        if (current != null && current.isObject()) return ((ObjectNode) current).deepCopy();
        ObjectNode preferences = newPreferences(OffsetDateTime.now());
        writeNode(key, preferences);
        return preferences;
    }

    private ObjectNode newPreferences(OffsetDateTime now) {
        ObjectNode preferences = objectMapper.createObjectNode();
        for (String field : PREFERENCE_FIELDS) {
            preferences.put(field, defaultPreference(field));
        }
        preferences.put("updatedAt", now.toString());
        return preferences;
    }

    private static boolean defaultPreference(String field) {
        return !Set.of("generalNews", "priceChanges", "outOfStock").contains(field);
    }

    private static Map<String, Boolean> preferenceMap(JsonNode preferences) {
        Map<String, Boolean> result = new LinkedHashMap<>();
        for (String field : PREFERENCE_FIELDS) {
            result.put(field, preferences.path(field).asBoolean(defaultPreference(field)));
        }
        return result;
    }

    private <T> List<T> readSortedEntities(String index, String entityType, Class<T> type) {
        List<T> result = new ArrayList<>();
        for (String id : sortedSets.zrange(index, 0, -1, new ZRangeArgs().rev())) {
            T value = read(entityKey(entityType, id), type);
            if (value != null) result.add(value);
        }
        return result;
    }

    private <T> List<T> readSetEntities(String index, String entityType, Class<T> type) {
        List<T> result = new ArrayList<>();
        for (String id : sets.smembers(index)) {
            T value = read(entityKey(entityType, id), type);
            if (value != null) result.add(value);
        }
        return result;
    }

    private void writeIfAbsent(String key, Object value) {
        values.setnx(key, toJson(value));
    }

    private void write(String key, Object value) {
        values.set(key, toJson(value));
    }

    private void writeNode(String key, JsonNode value) {
        values.set(key, value.toString());
    }

    private <T> T read(String key, Class<T> type) {
        String json = values.get(key);
        if (json == null) return null;
        try {
            return objectMapper.readerFor(type)
                .withoutFeatures(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .readValue(json);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Invalid Redis JSON at key " + key, e);
        }
    }

    private JsonNode readNode(String key) {
        String json = values.get(key);
        return parseNode(key, json);
    }

    private JsonNode parseNode(String key, String json) {
        if (json == null) return null;
        try {
            return objectMapper.readTree(json);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Invalid Redis JSON at key " + key, e);
        }
    }

    private io.vertx.mutiny.redis.client.Response eval(
        String script,
        List<String> redisKeys,
        List<String> arguments
    ) {
        List<String> command = new ArrayList<>(2 + redisKeys.size() + arguments.size());
        command.add(script);
        command.add(Integer.toString(redisKeys.size()));
        command.addAll(redisKeys);
        command.addAll(arguments);
        return redis.execute("EVAL", command.toArray(String[]::new));
    }

    private int evalInteger(String script, List<String> redisKeys,
                            List<String> arguments) {
        return eval(script, redisKeys, arguments).toInteger();
    }

    private <T> T convert(JsonNode node, Class<T> type) {
        try {
            return objectMapper.readerFor(type)
                .withoutFeatures(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .readValue(node);
        } catch (IOException e) {
            throw new IllegalStateException("Could not deserialize Redis record as " + type.getSimpleName(), e);
        }
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Could not serialize Redis record of type "
                + (value == null ? "null" : value.getClass().getName()), e);
        }
    }

    private String entityKey(String type, String id) {
        return prefix + ":" + type + ":" + id;
    }

    private String indexKey(String... parts) {
        return prefix + ":" + String.join(":", parts);
    }

    private static long epoch(OffsetDateTime value) {
        return value == null ? 0 : value.toInstant().toEpochMilli();
    }

    private static OffsetDateTime parseDate(JsonNode node, String field) {
        String value = text(node, field);
        if (value == null || value.isBlank()) return null;
        try {
            return OffsetDateTime.parse(value);
        } catch (DateTimeParseException e) {
            throw new IllegalStateException("Invalid date in Redis field " + field + ": " + value, e);
        }
    }

    private static OffsetDateTime later(OffsetDateTime left, OffsetDateTime right) {
        if (left == null) return right;
        if (right == null) return left;
        return left.isAfter(right) ? left : right;
    }

    private static OffsetDateTime earlier(OffsetDateTime left, OffsetDateTime right) {
        if (left == null) return right;
        if (right == null) return left;
        return left.isBefore(right) ? left : right;
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? null : value.asText();
    }

    private static boolean sameText(JsonNode node, String field, String expected) {
        String actual = text(node, field);
        if ("price".equals(field) && actual != null && expected != null) {
            try {
                return new BigDecimal(actual).compareTo(new BigDecimal(expected)) == 0;
            } catch (NumberFormatException ignored) {
                // Fall through to normal string comparison.
            }
        }
        return Objects.equals(actual, expected);
    }

    private static void putNullable(ObjectNode node, String field, String value) {
        if (value == null) node.putNull(field); else node.put(field, value);
    }

    public record MonitoringHealthData(
        OffsetDateTime lastRunAt,
        OffsetDateTime lastSuccessfulAt,
        int monitoredSources,
        int healthySources,
        boolean healthy
    ) {}

    public record SnapshotCleanupResult(
        OffsetDateTime cutoff,
        int sourcesProcessed,
        int snapshotsDeleted,
        int staleIndexEntriesDeleted,
        int dailyHashesUpdated
    ) {}

    public record ClaimedNotificationDelivery(
        NotificationDelivery delivery,
        String claimId
    ) {}

    public record DeviceTokenRecord(String installationId, String token) {}
}

