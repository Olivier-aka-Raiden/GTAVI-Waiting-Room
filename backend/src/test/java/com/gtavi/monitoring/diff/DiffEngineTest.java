package com.gtavi.monitoring.diff;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gtavi.domain.ChangeEvent;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class DiffEngineTest {

    private final DiffEngine engine = new DiffEngine();
    private final ObjectMapper mapper = new ObjectMapper();

    // ── First run ───────────────────────────────────────────────────────

    @Test
    void testFirstRunCreatesInitialEvent() throws Exception {
        var current = mapper.readTree("{\"releaseDate\":\"2026-11-19\"}");
        List<ChangeEvent> events = engine.diff("TEST", "http://test", null, current);
        assertEquals(1, events.size());
        assertEquals("INITIAL_SNAPSHOT", events.get(0).getEventType());
    }

    @Test
    void testInitialSnapshotNotUserVisible() throws Exception {
        var current = mapper.readTree("{\"releaseDate\":\"2026-11-19\"}");
        List<ChangeEvent> events = engine.diff("ROCKSTAR_MAIN", "http://test", null, current);
        assertEquals(1, events.size());
        assertEquals("INITIAL_SNAPSHOT", events.get(0).getEventType());
        assertFalse(events.get(0).isUserVisible(),
            "INITIAL_SNAPSHOT should not appear in user-facing timeline");
        assertFalse(events.get(0).isNotificationEligible(),
            "INITIAL_SNAPSHOT should not trigger push notifications");
    }

    // ── Identity ────────────────────────────────────────────────────────

    @Test
    void testIdenticalDataCreatesNoEvents() throws Exception {
        var prev = mapper.readTree("{\"releaseDate\":\"2026-11-19\"}");
        var curr = mapper.readTree("{\"releaseDate\":\"2026-11-19\"}");
        List<ChangeEvent> events = engine.diff("TEST", "http://test", prev, curr);
        assertEquals(0, events.size());
    }

    @Test
    void testNullCurrentReturnsEmpty() throws Exception {
        var prev = mapper.readTree("{\"releaseDate\":\"2026-11-19\"}");
        List<ChangeEvent> events = engine.diff("TEST", "http://test", prev, null);
        assertEquals(0, events.size());
    }

    // ── Release date ────────────────────────────────────────────────────

    @Test
    void testReleaseDateChanged() throws Exception {
        var prev = mapper.readTree("{\"releaseDate\":\"2026-11-19\"}");
        var curr = mapper.readTree("{\"releaseDate\":\"2027-03-12\"}");
        List<ChangeEvent> events = engine.diff("ROCKSTAR_MAIN", "http://test", prev, curr);
        assertEquals(1, events.size());
        assertEquals("RELEASE_DATE_CHANGED", events.get(0).getEventType());
        assertEquals("CRITICAL", events.get(0).getPriority());
    }

    // ── Editions ────────────────────────────────────────────────────────

    @Test
    void testNewEditionDetected() throws Exception {
        var prev = mapper.readTree("{\"editions\":[{\"name\":\"Standard Edition\"}]}");
        var curr = mapper.readTree("{\"editions\":[{\"name\":\"Standard Edition\"},{\"name\":\"Ultimate Edition\"}]}");
        List<ChangeEvent> events = engine.diff("ROCKSTAR_EDITIONS", "http://test", prev, curr);
        assertEquals(1, events.size());
        assertEquals("NEW_OFFICIAL_EDITION", events.get(0).getEventType());
        assertEquals("Ultimate Edition", events.get(0).getNewValue());
    }

    @Test
    void testCollectorEditionCreatesCriticalEvent() throws Exception {
        var prev = mapper.readTree("{\"editions\":[" +
            "{\"name\":\"Standard Edition\"},{\"name\":\"Ultimate Edition\"}]}");
        var curr = mapper.readTree("{\"editions\":[" +
            "{\"name\":\"Standard Edition\"}," +
            "{\"name\":\"Ultimate Edition\"}," +
            "{\"name\":\"Collector's Edition\"}]}");
        List<ChangeEvent> events = engine.diff("ROCKSTAR_EDITIONS", "http://test", prev, curr);
        assertEquals(1, events.size());
        assertEquals("COLLECTOR_EDITION_ANNOUNCED", events.get(0).getEventType());
        assertEquals("CRITICAL", events.get(0).getPriority());
        assertTrue(events.get(0).isNotificationEligible());
    }

    @Test
    void testEditionRemoved() throws Exception {
        var prev = mapper.readTree("{\"editions\":[" +
            "{\"name\":\"Standard Edition\"},{\"name\":\"Deluxe Edition\"}]}");
        var curr = mapper.readTree("{\"editions\":[{\"name\":\"Standard Edition\"}]}");
        List<ChangeEvent> events = engine.diff("ROCKSTAR_EDITIONS", "http://test", prev, curr);
        assertEquals(1, events.size());
        assertEquals("EDITION_REMOVED", events.get(0).getEventType());
        assertEquals("Deluxe Edition", events.get(0).getOldValue());
    }

    @Test
    void testCollectorKeywordsMatch() throws Exception {
        var prev = mapper.readTree("{\"editions\":[]}");
        var curr = mapper.readTree("{\"editions\":[" +
            "{\"name\":\"Limited Collector's Premium Edition\"}]}");
        List<ChangeEvent> events = engine.diff("ROCKSTAR_EDITIONS", "http://test", prev, curr);
        assertEquals(1, events.size());
        assertEquals("COLLECTOR_EDITION_ANNOUNCED", events.get(0).getEventType());
    }

    // ── Trailers / videos ───────────────────────────────────────────────

    @Test
    void testNewTrailerDetected() throws Exception {
        var prev = mapper.readTree("{\"videos\":[" +
            "{\"title\":\"Trailer 1\",\"mediaType\":\"TRAILER\"}]}");
        var curr = mapper.readTree("{\"videos\":[" +
            "{\"title\":\"Trailer 1\",\"mediaType\":\"TRAILER\"}," +
            "{\"title\":\"Trailer 2\",\"mediaType\":\"TRAILER\"}]}");
        List<ChangeEvent> events = engine.diff("ROCKSTAR_MEDIA", "http://test", prev, curr);
        assertEquals(1, events.size());
        assertEquals("NEW_TRAILER", events.get(0).getEventType());
        assertEquals("MAJOR", events.get(0).getPriority());
    }

    @Test
    void testYouTubeNewGtaViVideoDetected() throws Exception {
        var prev = mapper.readTree("{\"videos\":[]}");
        var curr = mapper.readTree("{\"videos\":[" +
            "{\"title\":\"Grand Theft Auto VI Trailer 3\",\"mediaType\":\"TRAILER\"," +
            "\"videoUrl\":\"https://youtube.com/watch?v=abc123\"," +
            "\"publicationDate\":\"2026-07-01\"}]}");
        List<ChangeEvent> events = engine.diff("ROCKSTAR_YOUTUBE", "http://test", prev, curr);
        assertEquals(1, events.size());
        assertEquals("NEW_TRAILER", events.get(0).getEventType());
        assertEquals("MAJOR", events.get(0).getPriority());
    }

    // ── Pre-order ───────────────────────────────────────────────────────

    @Test
    void testPreorderOpened() throws Exception {
        var prev = mapper.readTree("{\"preorderAvailable\":false}");
        var curr = mapper.readTree("{\"preorderAvailable\":true}");
        List<ChangeEvent> events = engine.diff("ROCKSTAR_MAIN", "http://test", prev, curr);
        assertEquals(1, events.size());
        assertEquals("PREORDER_OPENED", events.get(0).getEventType());
    }

    // ── Multiple changes ────────────────────────────────────────────────

    @Test
    void testMultipleChangesDetected() throws Exception {
        var prev = mapper.readTree("{\"releaseDate\":\"2026-11-19\"," +
            "\"editions\":[{\"name\":\"Standard Edition\"}]}");
        var curr = mapper.readTree("{\"releaseDate\":\"2027-03-12\"," +
            "\"editions\":[" +
            "{\"name\":\"Standard Edition\"},{\"name\":\"Collector's Edition\"}]}");
        List<ChangeEvent> events = engine.diff("ROCKSTAR_MAIN", "http://test", prev, curr);
        assertEquals(2, events.size());
        assertTrue(events.stream().anyMatch(e -> "RELEASE_DATE_CHANGED".equals(e.getEventType())));
        assertTrue(events.stream().anyMatch(e -> "COLLECTOR_EDITION_ANNOUNCED".equals(e.getEventType())));
    }

    // ── Retailer product diff ───────────────────────────────────────────

    @Test
    void testRetailerNewProductListing() throws Exception {
        var prev = mapper.readTree("{\"products\":[]}");
        var curr = mapper.readTree(
            "{\"products\":[" +
            "{\"name\":\"Grand Theft Auto VI Standard Edition\",\"edition\":\"STANDARD\"," +
            "\"price\":79.90,\"currency\":\"CHF\",\"availability\":\"PREORDER\"," +
            "\"url\":\"https://galaxus.ch/p/123\"}]}");
        List<ChangeEvent> events = engine.diff("GALAXUS", "http://test", prev, curr);
        assertEquals(1, events.size());
        assertEquals("RETAILER_LISTING_CREATED", events.get(0).getEventType());
        assertEquals("MAJOR", events.get(0).getPriority());
        assertTrue(events.get(0).getTitle().contains("GALAXUS"));
    }

    @Test
    void testCollectorListingAtRetailerCreatesCriticalEvent() throws Exception {
        var prev = mapper.readTree("{\"products\":[]}");
        var curr = mapper.readTree(
            "{\"products\":[" +
            "{\"name\":\"GTA VI Collector's Edition\",\"edition\":\"COLLECTOR\"," +
            "\"price\":199.90,\"currency\":\"CHF\",\"availability\":\"PREORDER\"," +
            "\"url\":\"https://galaxus.ch/p/456\"}]}");
        List<ChangeEvent> events = engine.diff("GALAXUS", "http://test", prev, curr);
        assertEquals(1, events.size());
        assertEquals("COLLECTOR_LISTING_DETECTED_AT_RETAILER", events.get(0).getEventType());
        assertEquals("CRITICAL", events.get(0).getPriority());
        assertTrue(events.get(0).isNotificationEligible());
    }

    @Test
    void testRetailerIdenticalProductsReturnsEmpty() throws Exception {
        var prev = mapper.readTree(
            "{\"products\":[{\"name\":\"GTA VI Standard\",\"edition\":\"STANDARD\"}]}");
        var curr = mapper.readTree(
            "{\"products\":[{\"name\":\"GTA VI Standard\",\"edition\":\"STANDARD\"}]}");
        List<ChangeEvent> events = engine.diff("GALAXUS", "http://test", prev, curr);
        assertEquals(0, events.size());
    }

    @Test
    void testMultipleRetailerProductsDetected() throws Exception {
        var prev = mapper.readTree(
            "{\"products\":[{\"name\":\"GTA VI Standard Edition\",\"edition\":\"STANDARD\"}]}");
        var curr = mapper.readTree(
            "{\"products\":[" +
            "{\"name\":\"GTA VI Standard Edition\",\"edition\":\"STANDARD\"}," +
            "{\"name\":\"GTA VI Ultimate Edition\",\"edition\":\"ULTIMATE\"}," +
            "{\"name\":\"GTA VI Collector's Edition\",\"edition\":\"COLLECTOR\"}]}");
        List<ChangeEvent> events = engine.diff("GALAXUS", "http://test", prev, curr);
        assertEquals(2, events.size());
        assertTrue(events.stream().anyMatch(e -> "RETAILER_LISTING_CREATED".equals(e.getEventType())));
        assertTrue(events.stream().anyMatch(e -> "COLLECTOR_LISTING_DETECTED_AT_RETAILER".equals(e.getEventType())));
    }
}
