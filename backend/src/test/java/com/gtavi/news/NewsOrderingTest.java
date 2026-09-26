package com.gtavi.news;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gtavi.config.RedisBackedTest;
import io.quarkus.redis.datasource.RedisDataSource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
class NewsOrderingTest extends RedisBackedTest {
    @Inject NewsRepository repository;
    @Inject RedisDataSource redis;
    @Inject ObjectMapper json;
    @ConfigProperty(name="gtavi.redis.key-prefix") String prefix;

    @Test void legacyScoresAreReconciledBeforePagination() {
        repository.upsert("articles", json.createObjectNode().put("id", "new").put("publishedAt", "2026-09-26T12:00:00+02:00"));
        repository.upsert("articles", json.createObjectNode().put("id", "old").put("publishedAt", "2026-09-24"));
        // Simulate old discovery-based scores in the opposite order.
        redis.execute("ZADD", prefix + ":news:index:articles", "1", "new", "9999999999999", "old");
        assertEquals("new", repository.list("articles", 0, 1).getFirst().path("id").asText());
        assertEquals("old", repository.list("articles", 1, 1).getFirst().path("id").asText());
    }
    @Test void undatedRefreshKeepsOriginalPositionAndLaterPublicationCorrectsIndex() {
        var article = json.createObjectNode().put("id", "undated");
        repository.upsert("articles", article);
        redis.execute("ZADD", prefix + ":news:index:articles", "100", "undated");
        repository.upsert("articles", article.put("updatedAt", "2026-09-26T12:00:00Z"));
        assertEquals(100d, redis.sortedSet(String.class).zscore(prefix + ":news:index:articles", "undated").orElseThrow());
        repository.upsert("articles", article.put("publishedAt", "2026-09-25"));
        assertEquals(java.time.Instant.parse("2026-09-25T00:00:00Z").toEpochMilli(),
            redis.sortedSet(String.class).zscore(prefix + ":news:index:articles", "undated").orElseThrow());
    }
}
