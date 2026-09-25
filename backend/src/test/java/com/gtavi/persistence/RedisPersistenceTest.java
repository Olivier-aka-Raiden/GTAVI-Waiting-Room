package com.gtavi.persistence;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gtavi.domain.ChangeEvent;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
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
    void deviceTokenOwnershipMovesAsOneAtomicUpdate() {
        String unique = UUID.randomUUID().toString();
        String firstInstallation = "token-owner-first-" + unique;
        String secondInstallation = "token-owner-second-" + unique;
        String sharedToken = "shared-token-" + unique;
        String secondToken = "second-token-" + unique;
        try {
            persistence.registerDevice(firstInstallation, sharedToken, "WEB", "en", "1");
            persistence.registerDevice(secondInstallation, secondToken, "WEB", "en", "1");

            assertTrue(persistence.updateDevice(secondInstallation,
                Map.of("pushToken", sharedToken)));
            assertFalse(persistence.isDeviceNotificationsEnabled(firstInstallation));
            assertEquals(sharedToken,
                persistence.getActiveDeviceToken(secondInstallation).token());

            var eligible = persistence.getEligibleDevices("newOfficialTrailers").stream()
                .filter(device -> device.installationId().equals(firstInstallation)
                    || device.installationId().equals(secondInstallation))
                .toList();
            assertEquals(1, eligible.size());
            assertEquals(secondInstallation, eligible.getFirst().installationId());

            persistence.registerDevice(firstInstallation, sharedToken, "WEB", "en", "2");
            assertFalse(persistence.isDeviceNotificationsEnabled(secondInstallation));
            assertEquals(sharedToken,
                persistence.getActiveDeviceToken(firstInstallation).token());
        } finally {
            persistence.deactivateDevice(firstInstallation);
            persistence.deactivateDevice(secondInstallation);
        }
    }

    @Test
    void offerEntityAndIndexesMoveTogether() {
        String unique = UUID.randomUUID().toString();
        String offerId = "atomic-offer-" + unique;
        String firstEdition = "edition-first-" + unique;
        String secondEdition = "edition-second-" + unique;
        String firstRetailer = "retailer-first-" + unique;
        String secondRetailer = "retailer-second-" + unique;

        persistence.upsertOffer(offerId, firstEdition, firstRetailer, "PS5", "CH",
            new BigDecimal("79.90"), "CHF", "https://example.test/first",
            "AVAILABLE", true);
        persistence.upsertOffer(offerId, secondEdition, secondRetailer, "PS5", "CH",
            new BigDecimal("69.90"), "CHF", "https://example.test/second",
            "AVAILABLE", true);

        assertTrue(persistence.getOffers(firstEdition).stream()
            .noneMatch(offer -> offerId.equals(offer.getId())));
        assertTrue(persistence.getOffers(secondEdition).stream()
            .anyMatch(offer -> offerId.equals(offer.getId())));

        persistence.markMissingOffers(firstRetailer, Set.of());
        persistence.markMissingOffers(firstRetailer, Set.of());
        assertTrue(persistence.getOffers(secondEdition).stream()
            .anyMatch(offer -> offerId.equals(offer.getId())));
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

    @Test
    void cleanupCompactsExpiredSnapshotsAndKeepsRecentReferenceSnapshots() throws Exception {
        String sourceCode = "CLEANUP_" + UUID.randomUUID();
        persistence.saveSourceDefinitionIfAbsent(Map.of("code", sourceCode, "enabled", true));
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        var oldData = objectMapper.readTree("{\"version\":\"old\"}");
        var currentData = objectMapper.readTree("{\"version\":\"current\"}");

        String oldSuccess = persistence.saveSnapshotAt(sourceCode, "https://example.test",
            oldData, "old-hash", true, null, now.minusDays(45));
        String oldFailure = persistence.saveSnapshotAt(sourceCode, "https://example.test",
            null, null, false, "old failure", now.minusDays(44));
        String currentSuccess = persistence.saveSnapshotAt(sourceCode, "https://example.test",
            currentData, "current-hash", true, null, now.minusDays(5));
        String currentFailure = persistence.saveSnapshotAt(sourceCode, "https://example.test",
            null, null, false, "current failure", now.minusDays(3));

        var result = persistence.cleanupSnapshots(now.minusDays(30));

        assertEquals(2, result.snapshotsDeleted());
        assertEquals(2, result.dailyHashesUpdated());
        assertNull(persistence.getSnapshot(oldSuccess));
        assertNull(persistence.getSnapshot(oldFailure));
        assertNotNull(persistence.getSnapshot(currentSuccess));
        assertNotNull(persistence.getSnapshot(currentFailure));

        var daily = persistence.getDailySnapshotHash(sourceCode,
            now.minusDays(45).toLocalDate());
        assertNotNull(daily);
        assertEquals("old-hash", daily.path("latestSuccessfulHash").asText());
        assertEquals("SUCCESS", daily.path("latestStatus").asText());

        var retryResult = persistence.cleanupSnapshots(now.minusDays(30));
        assertEquals(0, retryResult.snapshotsDeleted());
        assertEquals("old-hash", persistence.getDailySnapshotHash(sourceCode,
            now.minusDays(45).toLocalDate()).path("latestSuccessfulHash").asText());
    }

    @Test
    void cleanupAlwaysRetainsLatestSuccessfulAndFailedSnapshots() {
        String sourceCode = "CLEANUP_REFERENCES_" + UUID.randomUUID();
        persistence.saveSourceDefinitionIfAbsent(Map.of("code", sourceCode, "enabled", true));
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);

        String latestSuccess = persistence.saveSnapshotAt(sourceCode, "https://example.test",
            objectMapper.createObjectNode(), "latest-hash", true, null, now.minusDays(60));
        String latestFailure = persistence.saveSnapshotAt(sourceCode, "https://example.test",
            null, null, false, "latest failure", now.minusDays(59));

        var result = persistence.cleanupSnapshots(now.minusDays(30));

        assertEquals(0, result.snapshotsDeleted());
        assertNotNull(persistence.getSnapshot(latestSuccess));
        assertNotNull(persistence.getSnapshot(latestFailure));
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

