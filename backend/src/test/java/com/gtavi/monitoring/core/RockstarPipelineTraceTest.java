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
    @Inject com.gtavi.news.NewsRepository processing;

    private JsonNode editions;
    private boolean fetchFails;
    private int fetchCalls;
    private int extractionCalls;
    private int discardedResults;

    @BeforeEach
    void replaceRemoteBoundaries() throws Exception {
        String html = fixture("rockstar-editions-rsc.html");
        editions = mapper.readTree(fixture("rockstar-editions-response.json"));
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
            @Override public void discardCompletedResult(String html, String sourceType) { discardedResults++; }
            @Override
            public ExtractionResult extractFromHtml(String content, String sourceType) {
                extractionCalls++;
                assertEquals("rockstar_editions", sourceType);
                assertTrue(content.contains("Ultimate Edition"));
                assertTrue(content.contains("<script"), "The extraction service must receive all original SPA evidence");
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

    @Test
    void missingPlatformDetailsKeepAGeneralRockstarLink() {
        for (JsonNode item : editions.path("editions"))
            ((com.fasterxml.jackson.databind.node.ObjectNode)item).putArray("platforms");
        assertEquals(1, orchestrator.runCheck(Set.of(SOURCE)).successfulSources());
        assertEquals(1, orchestrator.runCheck(Set.of(SOURCE)).successfulSources());
        for (String editionId : java.util.List.of("ed-standard", "ed-ultimate")) {
            var offers = persistence.getOffers(editionId);
            assertEquals(1, offers.size());
            assertEquals("UNKNOWN", offers.getFirst().getPlatform());
            assertEquals(URL, offers.getFirst().getUrl());
            assertNull(offers.getFirst().getPrice());
        }
    }

    @Test
    void fullPlatformNamesAndDuplicateAliasesRestoreRetiredOffers() {
        assertEquals(1, orchestrator.runCheck(Set.of(SOURCE)).successfulSources());
        for (String id : java.util.List.of("ed-standard", "ed-ultimate"))
            persistence.getOffers(id).forEach(offer -> persistence.deactivateOffer(offer.getId()));
        for (JsonNode item : editions.path("editions")) {
            ((com.fasterxml.jackson.databind.node.ObjectNode)item).putArray("platforms")
                .add("PlayStation 5").add("PS5").add("Xbox Series X|S").add("Xbox Series S")
                .addNull().add(" ").add("unrecognized future console");
        }
        for (int run = 0; run < 2; run++) {
            assertEquals(1, orchestrator.runCheck(Set.of(SOURCE)).successfulSources());
            assertPersistedOffers("ed-standard");
            assertPersistedOffers("ed-ultimate");
            assertEquals(4, persistence.getLatestSuccessfulSnapshotData(SOURCE).path("products").size());
        }
    }

    @Test
    void unrecognizedNonEmptyPlatformListsRetainGeneralEditionLinks() {
        for (JsonNode item : editions.path("editions"))
            ((com.fasterxml.jackson.databind.node.ObjectNode)item).putArray("platforms")
                .add("new console").addNull().add("");
        for (int run = 0; run < 2; run++) {
            assertEquals(1, orchestrator.runCheck(Set.of(SOURCE)).successfulSources());
            for (String id : java.util.List.of("ed-standard", "ed-ultimate")) {
                var offers = persistence.getOffers(id);
                assertEquals(1, offers.size());
                assertEquals("UNKNOWN", offers.getFirst().getPlatform());
                assertEquals(URL, offers.getFirst().getUrl());
            }
        }
    }

    @Test
    void emptyEditionExtractionCannotRetireExistingOffersOrAdvanceBaseline() {
        assertEquals(1, orchestrator.runCheck(Set.of(SOURCE)).successfulSources());
        JsonNode previous = persistence.getLatestSuccessfulSnapshotData(SOURCE);
        ((com.fasterxml.jackson.databind.node.ObjectNode)editions).putArray("editions");
        for (int run = 0; run < 3; run++) {
            assertEquals(1, orchestrator.runCheck(Set.of(SOURCE)).failedSources());
            assertEquals(previous, persistence.getLatestSuccessfulSnapshotData(SOURCE));
            assertPersistedOffers("ed-standard");
            assertPersistedOffers("ed-ultimate");
        }
    }

    @Test
    void rejectedStagedProductsCannotBePersistedAsAnEmptySuccessfulStore() throws Exception {
        assertEquals(1, orchestrator.runCheck(Set.of(SOURCE)).successfulSources());
        JsonNode previous = persistence.getLatestSuccessfulSnapshotData(SOURCE);
        processing.write("observation:" + SOURCE, mapper.readTree("""
            {"products":[{"name":"Ultimate Edition","platform":"unsupported platform","url":"https://www.rockstargames.com/VI/editions"}]}
            """));
        for (int run = 0; run < 3; run++) {
            assertEquals(1, orchestrator.runCheck(Set.of(SOURCE)).failedSources());
            assertEquals(previous, persistence.getLatestSuccessfulSnapshotData(SOURCE));
            assertPersistedOffers("ed-standard");
            assertPersistedOffers("ed-ultimate");
        }
    }

    @Test
    void unknownPreorderPreservesOffersButExplicitClosureIsApplied() {
        assertEquals(1, orchestrator.runCheck(Set.of(SOURCE)).successfulSources());
        for (JsonNode item : editions.path("editions"))
            ((com.fasterxml.jackson.databind.node.ObjectNode)item).putNull("preorderAvailable");
        assertEquals(1, orchestrator.runCheck(Set.of(SOURCE)).successfulSources());
        assertPersistedOffers("ed-standard");
        assertPersistedOffers("ed-ultimate");
        assertTrue(persistence.getEditions("GTA_VI").stream()
            .allMatch(edition -> "PREORDER_AVAILABLE".equals(edition.getStatus())));
        for (JsonNode item : editions.path("editions"))
            ((com.fasterxml.jackson.databind.node.ObjectNode)item).put("preorderAvailable",false);
        assertEquals(1, orchestrator.runCheck(Set.of(SOURCE)).successfulSources());
        for (String id : java.util.List.of("ed-standard","ed-ultimate"))
            assertTrue(persistence.getOffers(id).stream().allMatch(offer ->
                !offer.isPreorderAvailable() && "UNAVAILABLE".equals(offer.getAvailabilityStatus())));
    }
}
