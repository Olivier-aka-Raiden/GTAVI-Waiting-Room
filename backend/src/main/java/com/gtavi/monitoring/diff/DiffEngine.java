package com.gtavi.monitoring.diff;

import com.fasterxml.jackson.databind.JsonNode;
import com.gtavi.domain.ChangeEvent;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;

import java.time.OffsetDateTime;
import java.util.*;

/**
 * Compares two normalized JSON states and produces semantic ChangeEvents.
 * Detects changes in: release date, editions, trailers, availability.
 */
@ApplicationScoped
public class DiffEngine {

    /**
     * Compute the diff between previous and current normalized state for a given source.
     *
     * @param sourceCode which source this diff is for
     * @param sourceUrl  the source URL for evidence
     * @param previous   the last successful normalized state (may be null on first run)
     * @param current    the current normalized state
     * @return list of ChangeEvents, empty if no meaningful changes
     */
    public List<ChangeEvent> diff(String sourceCode, String sourceUrl,
                                   JsonNode previous, JsonNode current) {
        if (current == null) return List.of();

        List<ChangeEvent> events = new ArrayList<>();

        if (previous == null) {
            // First run — no diff to compute. Just note what we found.
            return List.of(createEvent(sourceCode, sourceUrl,
                "INITIAL_SNAPSHOT", "NEWS",
                "Initial data captured from " + sourceCode,
                "First successful monitoring check for this source.",
                null, null, "INITIAL:" + sourceCode + ":" + System.currentTimeMillis()));
        }

        // Detect edition changes
        events.addAll(diffEditions(sourceCode, sourceUrl, previous, current));

        // Detect release date changes
        events.addAll(diffReleaseDate(sourceCode, sourceUrl, previous, current));

        // Detect trailer changes
        events.addAll(diffTrailers(sourceCode, sourceUrl, previous, current));

        // Detect pre-order changes
        events.addAll(diffPreorder(sourceCode, sourceUrl, previous, current));

        return events;
    }

    private List<ChangeEvent> diffEditions(String sourceCode, String sourceUrl,
                                            JsonNode prev, JsonNode curr) {
        List<ChangeEvent> events = new ArrayList<>();

        JsonNode prevEditions = prev.get("editions");
        JsonNode currEditions = curr.get("editions");
        if (prevEditions == null || currEditions == null) return events;

        Set<String> prevNames = editionNames(prevEditions);
        Set<String> currNames = editionNames(currEditions);

        // New editions
        Set<String> added = new HashSet<>(currNames);
        added.removeAll(prevNames);

        for (String name : added) {
            boolean isCollector = isCollectorEdition(name, currEditions);
            if (isCollector) {
                events.add(createEvent(sourceCode, sourceUrl,
                    "COLLECTOR_EDITION_ANNOUNCED", "CRITICAL",
                    "Collector's Edition announced!",
                    "Rockstar has officially announced a Collector's Edition: " + name,
                    null, name,
                    "COLLECTOR_ANNOUNCED:" + sanitizeKey(name)));
            } else {
                events.add(createEvent(sourceCode, sourceUrl,
                    "NEW_OFFICIAL_EDITION", "MAJOR",
                    "New edition announced: " + name,
                    "A new edition has been added to the official lineup.",
                    null, name,
                    "NEW_EDITION:" + sanitizeKey(name)));
            }
        }

        // Removed editions
        Set<String> removed = new HashSet<>(prevNames);
        removed.removeAll(currNames);
        for (String name : removed) {
            events.add(createEvent(sourceCode, sourceUrl,
                "EDITION_REMOVED", "NEWS",
                "Edition removed: " + name,
                "An edition has been removed from the official listing.",
                name, null,
                "REMOVED_EDITION:" + sanitizeKey(name)));
        }

        return events;
    }

    private List<ChangeEvent> diffReleaseDate(String sourceCode, String sourceUrl,
                                               JsonNode prev, JsonNode curr) {
        String prevDate = prev.has("releaseDate") ? prev.get("releaseDate").asText(null) : null;
        String currDate = curr.has("releaseDate") ? curr.get("releaseDate").asText(null) : null;

        if (currDate == null || currDate.equals(prevDate)) return List.of();

        return List.of(createEvent(sourceCode, sourceUrl,
            "RELEASE_DATE_CHANGED", "CRITICAL",
            "Release date changed to " + currDate,
            "The official release date has been updated.",
            prevDate, currDate,
            "DATE:" + prevDate + ":" + currDate));
    }

    private List<ChangeEvent> diffTrailers(String sourceCode, String sourceUrl,
                                            JsonNode prev, JsonNode curr) {
        List<ChangeEvent> events = new ArrayList<>();

        JsonNode prevVideos = prev.get("videos");
        JsonNode currVideos = curr.get("videos");
        if (prevVideos == null || currVideos == null) return events;

        Set<String> prevTitles = videoTitles(prevVideos);
        Set<String> currTitles = videoTitles(currVideos);

        Set<String> added = new HashSet<>(currTitles);
        added.removeAll(prevTitles);

        for (String title : added) {
            String mediaType = getMediaType(title, currVideos);
            String eventType = "TRAILER".equals(mediaType) ? "NEW_TRAILER" : "NEW_OFFICIAL_VIDEO";
            String priority = "TRAILER".equals(mediaType) ? "MAJOR" : "NEWS";

            events.add(createEvent(sourceCode, sourceUrl,
                eventType, priority,
                "New " + mediaType.toLowerCase() + ": " + title,
                "A new official GTA VI " + mediaType.toLowerCase() + " has been published.",
                null, title,
                "VIDEO:" + sanitizeKey(title)));
        }

        return events;
    }

    private List<ChangeEvent> diffPreorder(String sourceCode, String sourceUrl,
                                            JsonNode prev, JsonNode curr) {
        Boolean prevPreorder = prev.has("preorderAvailable") ? prev.get("preorderAvailable").asBoolean() : null;
        Boolean currPreorder = curr.has("preorderAvailable") ? curr.get("preorderAvailable").asBoolean() : null;

        if (currPreorder == null || currPreorder.equals(prevPreorder)) return List.of();

        if (Boolean.TRUE.equals(currPreorder)) {
            return List.of(createEvent(sourceCode, sourceUrl,
                "PREORDER_OPENED", "MAJOR",
                "Pre-orders are now available",
                "Pre-orders for GTA VI have opened.",
                "closed", "open",
                "PREORDER:opened:" + sourceCode));
        } else {
            return List.of(createEvent(sourceCode, sourceUrl,
                "PREORDER_CLOSED", "NEWS",
                "Pre-orders have closed",
                "Pre-orders for GTA VI are no longer available.",
                "open", "closed",
                "PREORDER:closed:" + sourceCode));
        }
    }

    // ---- Helpers ----

    private Set<String> editionNames(JsonNode editions) {
        Set<String> names = new LinkedHashSet<>();
        for (JsonNode e : editions) {
            if (e.has("name")) names.add(e.get("name").asText());
        }
        return names;
    }

    private boolean isCollectorEdition(String name, JsonNode editions) {
        String lower = name.toLowerCase().replaceAll("[^a-z]", "");
        return lower.contains("collector") || lower.contains("collectors");
    }

    private Set<String> videoTitles(JsonNode videos) {
        Set<String> titles = new LinkedHashSet<>();
        for (JsonNode v : videos) {
            if (v.has("title")) titles.add(v.get("title").asText());
        }
        return titles;
    }

    private String getMediaType(String title, JsonNode videos) {
        for (JsonNode v : videos) {
            if (v.has("title") && v.get("title").asText().equals(title)
                && v.has("mediaType")) {
                return v.get("mediaType").asText();
            }
        }
        return "OTHER_VIDEO";
    }

    private ChangeEvent createEvent(String sourceCode, String sourceUrl,
                                     String eventType, String priority,
                                     String title, String description,
                                     String oldValue, String newValue,
                                     String deduplicationKey) {
        ChangeEvent event = new ChangeEvent();
        event.setGameCode("GTA_VI");
        event.setSourceCode(sourceCode);
        event.setEventType(eventType);
        event.setPriority(priority);
        event.setTitle(title);
        event.setDescription(description);
        event.setOldValue(oldValue);
        event.setNewValue(newValue);
        event.setEvidenceUrl(sourceUrl);
        event.setDeduplicationKey(deduplicationKey);
        event.setDetectedAt(OffsetDateTime.now());
        event.setUserVisible(true);
        event.setNotificationEligible("CRITICAL".equals(priority) || "MAJOR".equals(priority));
        event.setCreatedAt(OffsetDateTime.now());
        return event;
    }

    private String sanitizeKey(String input) {
        return input.toLowerCase().replaceAll("[^a-z0-9]", "-").replaceAll("-+", "-");
    }
}
