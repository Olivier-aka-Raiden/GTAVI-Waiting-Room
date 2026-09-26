package com.gtavi.monitoring.core;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gtavi.config.RedisBackedTest;
import com.gtavi.domain.RetailOffer;
import com.gtavi.persistence.RedisPersistence;
import io.quarkus.test.junit.QuarkusMock;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/** Runs the real monitor, conversion, validation and persistence; only remote boundaries are replaced. */
@QuarkusTest
class RockstarPipelineTraceTest extends RedisBackedTest {

    private static final String SOURCE = "ROCKSTAR_STORE";
    private static final String URL = "https://www.rockstargames.com/VI/editions";

    @Inject ObjectMapper mapper;
    @Inject MonitoringOrchestrator orchestrator;
    @Inject RedisPersistence persistence;

    private boolean fetchFails;
    private int fetchCalls;
    private int extractionCalls;

    @BeforeEach
    void replaceRemoteBoundaries() throws Exception {
        String html = fixture("rockstar-editions-rsc.html");
        JsonNode editions = mapper.readTree(fixture("rockstar-editions-response.json"));
        QuarkusMock.installMockForType(new HttpFetcher() {
            @Override
            public String fetch(String url) throws IOException {
                fetchCalls++;
                assertEquals(URL, url, "Only the explicitly requested monitor should run");
                if (fetchFails) throw new IOException("simulated remote outage");
                return html;
            }
        }, HttpFetcher.class);
        QuarkusMock.installMockForType(new AiExtractionService() {
            @Override
            public ExtractionResult extractFromHtml(String content, String sourceType) {
                extractionCalls++;
                assertEquals("rockstar_editions", sourceType);
                assertTrue(content.contains("Ultimate Edition"));
                assertFalse(content.contains("<script"), "The real monitor must extract the SPA content");
                return ExtractionResult.complete(editions.deepCopy(), content.length());
            }
        }, AiExtractionService.class);
    }

    @Test
    void traceFullPipelineAndReplayWithoutDuplicatingOffersOrEvents() {
        var first = orchestrator.runCheck(Set.of(SOURCE));
        assertEquals(1, first.checkedSources());
        assertEquals(1, first.successfulSources());
        assertEquals(0, first.failedSources());
        var snapshot = persistence.getLatestSuccessfulSnapshotData(SOURCE);
        assertNotNull(snapshot);
        assertEquals(4, snapshot.path("products").size());
        assertPersistedOffers("ed-standard");
        assertPersistedOffers("ed-ultimate");
        long eventsAfterFirstRun = persistence.getEventCount("GTA_VI");

        var replay = orchestrator.runCheck(Set.of(SOURCE));
        assertEquals(1, replay.successfulSources());
        assertEquals(0, replay.eventsCreated());
        assertEquals(eventsAfterFirstRun, persistence.getEventCount("GTA_VI"));
        assertPersistedOffers("ed-standard");
        assertPersistedOffers("ed-ultimate");
        assertEquals(2, fetchCalls);
        assertEquals(2, extractionCalls);
    }

    @Test
    void failedFetchKeepsTheLastSuccessfulSnapshotAndOffers() {
        assertEquals(1, orchestrator.runCheck(Set.of(SOURCE)).successfulSources());
        JsonNode previous = persistence.getLatestSuccessfulSnapshotData(SOURCE);
        fetchFails = true;

        var failed = orchestrator.runCheck(Set.of(SOURCE));
        assertEquals(1, failed.checkedSources());
        assertEquals(1, failed.failedSources());
        assertEquals(0, failed.successfulSources());
        assertEquals(0, failed.eventsCreated());
        assertEquals(previous, persistence.getLatestSuccessfulSnapshotData(SOURCE));
        assertPersistedOffers("ed-standard");
        assertPersistedOffers("ed-ultimate");
        assertEquals(2, fetchCalls);
        assertEquals(1, extractionCalls, "An unsuccessful fetch must not reach the AI boundary");
    }

    private void assertPersistedOffers(String editionId) {
        var offers = persistence.getOffers(editionId);
        assertEquals(2, offers.size());
        assertEquals(Set.of("PS5", "XSX"), offers.stream()
            .map(RetailOffer::getPlatform).collect(Collectors.toSet()));
        for (var offer : offers) {
            assertTrue(offer.getId().startsWith(SOURCE + ":" + editionId + ":" + offer.getPlatform() + ":"));
            assertEquals(editionId, offer.getEditionId());
            assertEquals(SOURCE, offer.getRetailerCode());
            assertEquals("PREORDER_AVAILABLE", offer.getAvailabilityStatus());
            assertTrue(offer.isPreorderAvailable());
            assertEquals(URL, offer.getUrl());
            assertEquals("US", offer.getCountryCode());
            assertNull(offer.getCurrency(), "Currency must not be invented when the page provides none");
            assertNull(offer.getPrice());
        }
    }

    @Test
    void verifyEditionTypesAreRecognized() {
        assertTrue(RetailerProductValidator.isGtaViGameProduct("Ultimate Edition"));
        assertTrue(RetailerProductValidator.isGtaViGameProduct("Standard Edition"));
        assertTrue(RetailerProductValidator.isGtaViGameProduct("Collector's Edition"));
        assertTrue(RetailerProductValidator.isGtaViGameProduct("Deluxe Edition"));
    }

    @Test
    void verifyNonGameProductsAreRejected() {
        assertFalse(RetailerProductValidator.isGtaViGameProduct("GTA V Premium Edition"));
        assertFalse(RetailerProductValidator.isGtaViGameProduct("Soundtrack"));
        assertFalse(RetailerProductValidator.isGtaViGameProduct(""));
    }

    private String fixture(String name) throws IOException {
        try (var stream = getClass().getResourceAsStream("/fixtures/" + name)) {
            assertNotNull(stream, "Missing fixture: " + name);
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
