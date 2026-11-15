package com.gtavi.news;

import com.gtavi.config.RedisBackedTest;
import io.quarkus.redis.datasource.RedisDataSource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.junit.jupiter.api.Test;
import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;

/** Reproduces a Redis response larger than the client's 65,535-element limit. */
@QuarkusTest
class NewsLargeIndexTest extends RedisBackedTest {
    @Inject RedisDataSource redis;
    @Inject NewsRepository repository;
    @ConfigProperty(name="gtavi.redis.key-prefix") String prefix;

    @Test void largeArticleIsReadableEvenWhenUtf8CharacterCrossesChunkBoundary() {
        String lead = "{\"id\":\"large\",\"description\":\"";
        int padding = 32768 - lead.getBytes(java.nio.charset.StandardCharsets.UTF_8).length - 1;
        String description = "x".repeat(padding) + "🎬" + "y".repeat(50000);
        String raw = lead + description + "\",\"publishedAt\":\"2026-11-15\"}";
        assertTrue(raw.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 65535);
        redis.value(String.class).set(prefix + ":news:articles:large", raw);
        redis.sortedSet(String.class).zadd(prefix + ":news:index:articles", 1, "large");

        assertEquals(description, repository.read("articles:large").path("description").asText());
        given().get("/api/v1/games/gta-vi/news?page=0&size=1").then().statusCode(200)
            .body("total", equalTo(1)).body("items[0].description", equalTo(description));
    }

    @Test void largeIndexKeepsOrderingDeduplicationAndTotal() {
        String index = prefix + ":news:index:articles";
        String articlePrefix = prefix + ":news:articles:";
        String seed = """
            for i=tonumber(ARGV[1]),tonumber(ARGV[2]) do
                local id=string.format('entry:%06d',i)
                redis.call('ZADD',KEYS[1],i,id)
                redis.call('SET',ARGV[3]..id,'{"id":"'..id..'"}')
            end
            return 1
            """;
        final int count = 78422;
        for (int start = 0; start < count; start += 500) {
            redis.execute("EVAL", seed, "1", index, Integer.toString(start),
                Integer.toString(Math.min(start + 499, count - 1)), articlePrefix);
        }
        // Old scores are deliberately incorrect: the verified dates must still determine order.
        redis.value(String.class).set(articlePrefix + "entry:000001", """
            {"id":"entry:000001","publishedAt":"2026-11-01","category":"MUSIC",
             "relationshipLinks":["https://www.rockstargames.com/VI/music"]}
            """);
        redis.value(String.class).set(articlePrefix + "entry:000002", """
            {"id":"entry:000002","publishedAt":"2025-05-01","category":"NEWS"}
            """);
        redis.value(String.class).set(articlePrefix + "entry:000003", """
            {"id":"entry:000003","category":"MUSIC","sourceUrl":"https://www.rockstargames.com/VI/music"}
            """);
        // Orphaned IDs must neither inflate total nor trigger a failure.
        redis.sortedSet(String.class).zadd(index, -1, "orphan");

        given().get("/api/v1/games/gta-vi/news?page=0&size=3").then().statusCode(200)
            .body("total", equalTo(count - 1))
            .body("items.id", contains("entry:000001", "entry:000002", "entry:078421"));
        assertEquals(count + 1L, repository.count("articles"), "Read must not delete indexed records");
        assertNotNull(repository.read("articles:entry:000003"), "Deduplicated raw records must remain available");
    }
}
