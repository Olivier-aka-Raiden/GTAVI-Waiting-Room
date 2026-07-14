package com.gtavi.monitoring.core;

import com.fasterxml.jackson.databind.JsonNode;
import com.gtavi.domain.ChangeEvent;
import com.gtavi.monitoring.diff.DiffEngine;
import com.gtavi.service.GameService;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import org.neo4j.driver.Driver;

import java.time.OffsetDateTime;
import java.util.*;

/**
 * Orchestrates the full monitoring pipeline:
 * 1. Select due sources
 * 2. Fetch → Extract → Normalize → Hash → Load previous → Diff → Store
 */
@ApplicationScoped
public class MonitoringOrchestrator {

    private final Driver driver;
    private final Normalizer normalizer;
    private final DiffEngine diffEngine;
    private final GameService gameService;
    private final Instance<GameSourceMonitor> allMonitors;

    public MonitoringOrchestrator(Driver driver, Normalizer normalizer,
                                   DiffEngine diffEngine, GameService gameService,
                                   Instance<GameSourceMonitor> allMonitors) {
        this.driver = driver;
        this.normalizer = normalizer;
        this.diffEngine = diffEngine;
        this.gameService = gameService;
        this.allMonitors = allMonitors;
    }

    /**
     * Run a monitoring check for all due sources.
     */
    public MonitoringRunSummary runCheck() {
        return runCheck(Set.of());
    }

    /**
     * Run a monitoring check for specific sources (or all if empty).
     */
    public MonitoringRunSummary runCheck(Set<String> sourceCodes) {
        OffsetDateTime startedAt = OffsetDateTime.now();
        int checked = 0, successful = 0, failed = 0, eventsCreated = 0;

        List<GameSourceMonitor> monitors = getDueMonitors(sourceCodes);

        for (GameSourceMonitor monitor : monitors) {
            checked++;
            try {
                MonitorResult result = monitor.fetchCurrentState();

                if (result.isSuccess() && result.normalizedData() != null) {
                    String hash = normalizer.computeHash(result.normalizedData());

                    JsonNode previous = loadPreviousSnapshot(monitor.sourceCode());

                    storeSnapshot(monitor.sourceCode(), monitor.sourceUrl(),
                        result.normalizedData(), hash, true, null);

                    List<ChangeEvent> events = diffEngine.diff(
                        monitor.sourceCode(), monitor.sourceUrl(),
                        previous, result.normalizedData());

                    for (ChangeEvent event : events) {
                        saveEvent(event);
                    }
                    eventsCreated += events.size();

                    // Persist retailer offers from product data
                    if (isRetailer(monitor.sourceCode())) {
                        persistOffers(monitor.sourceCode(), result.normalizedData());
                    }

                    successful++;
                    Log.infof("Monitor %s: SUCCESS (hash=%s, %d events)",
                        monitor.sourceCode(), hash != null ? hash.substring(0, 8) : "null",
                        events.size());
                } else {
                    storeSnapshot(monitor.sourceCode(), monitor.sourceUrl(),
                        null, null, false,
                        result.errorMessage() != null ? result.errorMessage() : result.status().name());
                    failed++;
                    Log.warnf("Monitor %s: %s — %s", monitor.sourceCode(),
                        result.status(), result.errorMessage());
                }

            } catch (Exception e) {
                storeSnapshot(monitor.sourceCode(), monitor.sourceUrl(),
                    null, null, false, e.getMessage());
                failed++;
                Log.errorf(e, "Monitor %s: unexpected error", monitor.sourceCode());
            }
        }

        OffsetDateTime finishedAt = OffsetDateTime.now();

        return new MonitoringRunSummary(startedAt, finishedAt,
            checked, successful, failed, eventsCreated);
    }

    private List<GameSourceMonitor> getDueMonitors(Set<String> sourceCodes) {
        List<GameSourceMonitor> monitors = new ArrayList<>();
        for (GameSourceMonitor m : allMonitors) {
            if (sourceCodes.isEmpty() || sourceCodes.contains(m.sourceCode())) {
                monitors.add(m);
            }
        }
        if (monitors.isEmpty()) {
            Log.warn("No GameSourceMonitor beans discovered via Instance<> injection. " +
                "Verify monitors are @ApplicationScoped and in a scanned package.");
        }
        return monitors;
    }

    private JsonNode loadPreviousSnapshot(String sourceCode) {
        try (var session = driver.session()) {
            var result = session.run(
                "MATCH (s:SourceSnapshot {sourceCode: $code, successful: true}) " +
                "RETURN s.normalizedJson AS json ORDER BY s.checkedAt DESC LIMIT 1",
                Map.of("code", sourceCode)
            );
            if (result.hasNext()) {
                var record = result.single();
                if (!record.get("json").isNull()) {
                    String jsonStr = record.get("json").asString();
                    return new com.fasterxml.jackson.databind.ObjectMapper().readTree(jsonStr);
                }
            }
        } catch (Exception e) {
            Log.debugf("No previous snapshot for %s: %s", sourceCode, e.getMessage());
        }
        return null;
    }

    private void storeSnapshot(String sourceCode, String sourceUrl,
                                JsonNode data, String hash,
                                boolean successful, String errorMessage) {
        String json = data != null ? data.toString() : null;

        try (var session = driver.session()) {
            session.run("""
                CREATE (s:SourceSnapshot {
                    sourceCode: $sourceCode,
                    sourceUrl: $sourceUrl,
                    status: $status,
                    normalizedJson: $json,
                    normalizedHash: $hash,
                    checkedAt: datetime(),
                    successful: $successful,
                    errorMessage: $errorMessage
                })
                """, Map.ofEntries(
                    Map.entry("sourceCode", sourceCode),
                    Map.entry("sourceUrl", sourceUrl),
                    Map.entry("status", successful ? "SUCCESS" : "FAILURE"),
                    Map.entry("json", json != null ? json : ""),
                    Map.entry("hash", hash != null ? hash : ""),
                    Map.entry("successful", successful),
                    Map.entry("errorMessage", errorMessage != null ? errorMessage : "")
                ));
        }
    }

    private void saveEvent(ChangeEvent event) {
        try (var session = driver.session()) {
            session.run("""
                MERGE (ce:ChangeEvent {deduplicationKey: $key})
                ON CREATE SET
                    ce.id = $id,
                    ce.gameCode = $gameCode,
                    ce.sourceCode = $sourceCode,
                    ce.eventType = $eventType,
                    ce.priority = $priority,
                    ce.title = $title,
                    ce.description = $description,
                    ce.oldValue = $oldValue,
                    ce.newValue = $newValue,
                    ce.evidenceUrl = $evidenceUrl,
                    ce.detectedAt = datetime(),
                    ce.userVisible = $userVisible,
                    ce.notificationEligible = $notificationEligible,
                    ce.createdAt = datetime()
                """, Map.ofEntries(
                    Map.entry("key", event.getDeduplicationKey()),
                    Map.entry("id", event.getId()),
                    Map.entry("gameCode", "GTA_VI"),
                    Map.entry("sourceCode", event.getSourceCode() != null ? event.getSourceCode() : ""),
                    Map.entry("eventType", event.getEventType()),
                    Map.entry("priority", event.getPriority()),
                    Map.entry("title", event.getTitle()),
                    Map.entry("description", event.getDescription() != null ? event.getDescription() : ""),
                    Map.entry("oldValue", event.getOldValue() != null ? event.getOldValue() : ""),
                    Map.entry("newValue", event.getNewValue() != null ? event.getNewValue() : ""),
                    Map.entry("evidenceUrl", event.getEvidenceUrl() != null ? event.getEvidenceUrl() : ""),
                    Map.entry("userVisible", event.isUserVisible()),
                    Map.entry("notificationEligible", event.isNotificationEligible())
                ));
        }
    }

    private boolean isRetailer(String sourceCode) {
        return sourceCode.equals("PS_STORE") || sourceCode.equals("XBOX_STORE")
            || sourceCode.equals("ROCKSTAR_STORE") || sourceCode.equals("GALAXUS")
            || sourceCode.equals("WOG") || sourceCode.equals("AMAZON_FR");
    }

    /**
     * Persist extracted product data as RetailOffer nodes in Neo4j.
     * Matches product names to known editions by name similarity.
     */
    private void persistOffers(String sourceCode, JsonNode data) {
        JsonNode products = data.get("products");
        if (products == null || !products.isArray()) return;

        List<String> knownEditionIds = getEditionIds();

        try (var session = driver.session()) {
            for (JsonNode p : products) {
                String productName = p.has("name") ? p.get("name").asText() : null;
                if (productName == null) continue;

                // Use AI-extracted edition type as hint, fall back to name matching
                String aiEdition = p.has("edition") ? p.get("edition").asText().toLowerCase() : null;
                String editionId = aiEdition != null ? matchEdition(aiEdition, knownEditionIds) : null;
                if (editionId == null) {
                    editionId = matchEdition(productName, knownEditionIds);
                }
                if (editionId == null) {
                    Log.debugf("Could not match product '%s' to any known edition", productName);
                    continue;
                }

                String url = p.has("url") ? p.get("url").asText() : null;
                String platform = p.has("platform") ? p.get("platform").asText() : null;
                String availability = p.has("availability") ? p.get("availability").asText() : "UNKNOWN";
                // Normalize AI availability values to our internal states
                availability = switch (availability.toUpperCase()) {
                    case "PREORDER" -> "PREORDER_AVAILABLE";
                    case "IN_STOCK" -> "AVAILABLE";
                    default -> availability.toUpperCase();
                };
                String priceText = p.has("price") ? p.get("price").asText(null) : null;
                String currency = p.has("currency") ? p.get("currency").asText("CHF") : "CHF";

                String offerId = sourceCode + ":" + editionId;

                session.run("""
                    MERGE (o:RetailOffer {id: $id})
                    SET o.editionId = $editionId,
                        o.retailerCode = $retailerCode,
                        o.platform = $platform,
                        o.countryCode = $country,
                        o.price = $price,
                        o.currency = $currency,
                        o.url = $url,
                        o.availabilityStatus = $availability,
                        o.preorderAvailable = $preorder,
                        o.lastSuccessfulCheckAt = datetime(),
                        o.lastChangedAt = coalesce(o.lastChangedAt, datetime()),
                        o.createdAt = coalesce(o.createdAt, datetime()),
                        o.updatedAt = datetime()
                    """, Map.ofEntries(
                        Map.entry("id", offerId),
                        Map.entry("editionId", editionId),
                        Map.entry("retailerCode", sourceCode),
                        Map.entry("platform", platform != null ? platform : ""),
                        Map.entry("country", "CH"),
                        Map.entry("price", priceText != null ? Double.parseDouble(priceText) : null),
                        Map.entry("currency", currency),
                        Map.entry("url", url != null ? url : ""),
                        Map.entry("availability", availability),
                        Map.entry("preorder", "PREORDER_AVAILABLE".equals(availability)
                            || "IN_STOCK".equals(availability))
                    ));
            }
        }
        Log.debugf("Persisted %d offers for retailer %s",
            products.size(), sourceCode);
    }

    /** Get all edition IDs from Neo4j. */
    private List<String> getEditionIds() {
        try (var session = driver.session()) {
            return session.run("MATCH (e:Edition) RETURN e.id AS id")
                .list(r -> r.get("id").asString());
        }
    }

    /** Match a product name to the closest edition ID by keyword. */
    private String matchEdition(String productName, List<String> editionIds) {
        String lower = productName.toLowerCase().replaceAll("[^a-z]", "");
        for (String id : editionIds) {
            if (id.contains("standard") && lower.contains("standard")) return id;
            if (id.contains("ultimate") && lower.contains("ultimate")) return id;
            if (id.contains("collector") && lower.contains("collector")) return id;
            if (id.contains("deluxe") && lower.contains("deluxe")) return id;
        }
        // Fallback: if product name contains no edition keyword, assume Standard
        if (!lower.contains("ultimate") && !lower.contains("collector")
            && !lower.contains("deluxe")) {
            return editionIds.stream().filter(id -> id.contains("standard")).findFirst().orElse(null);
        }
        return null;
    }

    public record MonitoringRunSummary(
        OffsetDateTime startedAt,
        OffsetDateTime finishedAt,
        int checkedSources,
        int successfulSources,
        int failedSources,
        int eventsCreated
    ) {}
}
