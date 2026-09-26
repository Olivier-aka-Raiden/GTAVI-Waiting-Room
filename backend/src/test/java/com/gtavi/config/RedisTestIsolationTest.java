package com.gtavi.config;

import com.gtavi.persistence.RedisPersistence;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

@QuarkusTest
class RedisTestIsolationTest extends RedisBackedTest {

    @Inject
    RedisPersistence persistence;

    @RepeatedTest(2)
    void everyInvocationStartsWithOnlySeedSources() {
        assertEquals(9, persistence.getMonitoringHealth().monitoredSources());
        persistence.saveSourceDefinitionIfAbsent(Map.of("code", "ISOLATION_TEST", "enabled", true));
        assertEquals(10, persistence.getMonitoringHealth().monitoredSources());
    }

    @Test
    void resetRemovesTestDataButPreservesOtherNamespaces() {
        String ownKey = prefix + ":fixture-marker";
        String otherKey = "gtavi:test:" + UUID.randomUUID() + ":fixture-marker";
        var values = redis.value(String.class);
        try {
            values.set(ownKey, "owned");
            values.set(otherKey, "unrelated");
            resetRedisFixtures();
            assertFalse(redis.key().exists(ownKey));
            assertEquals("unrelated", values.get(otherKey));
            assertEquals(9, persistence.getMonitoringHealth().monitoredSources());
        } finally {
            redis.key().del(otherKey);
        }
    }
}
