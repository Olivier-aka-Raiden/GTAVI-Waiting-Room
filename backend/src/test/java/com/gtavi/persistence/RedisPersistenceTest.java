package com.gtavi.persistence;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gtavi.domain.ChangeEvent;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
class RedisPersistenceTest {

    @Inject
    RedisPersistence persistence;

    @Inject
    ObjectMapper objectMapper;

    @Test
    void changeEventDeduplicationAndIndexingAreIdempotent() {
        String unique = UUID.randomUUID().toString();
        long before = persistence.getEventCount("GTA_VI");
        ChangeEvent event = event(unique);

        assertTrue(persistence.saveEventIfAbsent(event));
        assertFalse(persistence.saveEventIfAbsent(event(unique)));
        assertEquals(before + 1, persistence.getEventCount("GTA_VI"));
    }

    @Test
    void latestSuccessfulSnapshotRoundTripsNormalizedJson() throws Exception {
        String sourceCode = "TEST_" + UUID.randomUUID();
        var expected = objectMapper.readTree("{\"status\":\"available\"}");

        persistence.saveSnapshot(sourceCode, "https://example.test", expected,
            "hash", true, null);

        assertNotNull(persistence.getLatestSnapshotTime(sourceCode));
        assertEquals(expected, persistence.getLatestSuccessfulSnapshotData(sourceCode));
    }

    private ChangeEvent event(String unique) {
        ChangeEvent event = new ChangeEvent();
        event.setGameCode("GTA_VI");
        event.setEventType("NEW_TRAILER");
        event.setPriority("MAJOR");
        event.setTitle("Test event");
        event.setDeduplicationKey("test:" + unique);
        event.setDetectedAt(OffsetDateTime.now());
        event.setUserVisible(true);
        event.setNotificationEligible(false);
        return event;
    }
}

