package com.gtavi.monitoring.diff;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gtavi.domain.ChangeEvent;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class DiffEngineTest {

    private final DiffEngine engine = new DiffEngine();
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void testFirstRunCreatesInitialEvent() throws Exception {
        var current = mapper.readTree("{\"releaseDate\":\"2026-11-19\"}");
        List<ChangeEvent> events = engine.diff("TEST", "http://test", null, current);
        assertEquals(1, events.size());
        assertEquals("INITIAL_SNAPSHOT", events.get(0).getEventType());
    }

    @Test
    void testIdenticalDataCreatesNoEvents() throws Exception {
        var prev = mapper.readTree("{\"releaseDate\":\"2026-11-19\"}");
        var curr = mapper.readTree("{\"releaseDate\":\"2026-11-19\"}");
        List<ChangeEvent> events = engine.diff("TEST", "http://test", prev, curr);
        assertEquals(0, events.size());
    }

    @Test
    void testReleaseDateChanged() throws Exception {
        var prev = mapper.readTree("{\"releaseDate\":\"2026-11-19\"}");
        var curr = mapper.readTree("{\"releaseDate\":\"2027-03-12\"}");
        List<ChangeEvent> events = engine.diff("TEST", "http://test", prev, curr);
        assertEquals(1, events.size());
        assertEquals("RELEASE_DATE_CHANGED", events.get(0).getEventType());
        assertEquals("CRITICAL", events.get(0).getPriority());
    }

    @Test
    void testNewEditionDetected() throws Exception {
        var prev = mapper.readTree("{\"editions\":[{\"name\":\"Standard Edition\"}]}");
        var curr = mapper.readTree("{\"editions\":[{\"name\":\"Standard Edition\"},{\"name\":\"Ultimate Edition\"}]}");
        List<ChangeEvent> events = engine.diff("TEST", "http://test", prev, curr);
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
        List<ChangeEvent> events = engine.diff("TEST", "http://test", prev, curr);
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
        List<ChangeEvent> events = engine.diff("TEST", "http://test", prev, curr);
        assertEquals(1, events.size());
        assertEquals("EDITION_REMOVED", events.get(0).getEventType());
        assertEquals("Deluxe Edition", events.get(0).getOldValue());
    }

    @Test
    void testNewTrailerDetected() throws Exception {
        var prev = mapper.readTree("{\"videos\":[" +
            "{\"title\":\"Trailer 1\",\"mediaType\":\"TRAILER\"}]}");
        var curr = mapper.readTree("{\"videos\":[" +
            "{\"title\":\"Trailer 1\",\"mediaType\":\"TRAILER\"}," +
            "{\"title\":\"Trailer 2\",\"mediaType\":\"TRAILER\"}]}");
        List<ChangeEvent> events = engine.diff("TEST", "http://test", prev, curr);
        assertEquals(1, events.size());
        assertEquals("NEW_TRAILER", events.get(0).getEventType());
        assertEquals("MAJOR", events.get(0).getPriority());
    }

    @Test
    void testPreorderOpened() throws Exception {
        var prev = mapper.readTree("{\"preorderAvailable\":false}");
        var curr = mapper.readTree("{\"preorderAvailable\":true}");
        List<ChangeEvent> events = engine.diff("TEST", "http://test", prev, curr);
        assertEquals(1, events.size());
        assertEquals("PREORDER_OPENED", events.get(0).getEventType());
    }

    @Test
    void testNullCurrentReturnsEmpty() throws Exception {
        var prev = mapper.readTree("{\"releaseDate\":\"2026-11-19\"}");
        List<ChangeEvent> events = engine.diff("TEST", "http://test", prev, null);
        assertEquals(0, events.size());
    }

    @Test
    void testMultipleChangesDetected() throws Exception {
        var prev = mapper.readTree("{\"releaseDate\":\"2026-11-19\"," +
            "\"editions\":[{\"name\":\"Standard Edition\"}]}");
        var curr = mapper.readTree("{\"releaseDate\":\"2027-03-12\"," +
            "\"editions\":[" +
            "{\"name\":\"Standard Edition\"},{\"name\":\"Collector's Edition\"}]}");
        List<ChangeEvent> events = engine.diff("TEST", "http://test", prev, curr);
        assertEquals(2, events.size());
        assertTrue(events.stream().anyMatch(e -> "RELEASE_DATE_CHANGED".equals(e.getEventType())));
        assertTrue(events.stream().anyMatch(e -> "COLLECTOR_EDITION_ANNOUNCED".equals(e.getEventType())));
    }

    @Test
    void testCollectorKeywordsMatch() throws Exception {
        var prev = mapper.readTree("{\"editions\":[]}");
        var curr = mapper.readTree("{\"editions\":[" +
            "{\"name\":\"Limited Collector's Premium Edition\"}]}");
        List<ChangeEvent> events = engine.diff("TEST", "http://test", prev, curr);
        assertEquals(1, events.size());
        assertEquals("COLLECTOR_EDITION_ANNOUNCED", events.get(0).getEventType());
    }
}
