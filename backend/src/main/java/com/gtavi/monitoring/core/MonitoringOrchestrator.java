package com.gtavi.monitoring.core;

import com.fasterxml.jackson.databind.JsonNode;
import com.gtavi.domain.ChangeEvent;
import com.gtavi.monitoring.diff.DiffEngine;
import com.gtavi.notification.NotificationService;
import com.gtavi.persistence.RedisPersistence;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Orchestrates the full monitoring pipeline:
 * fetch, extract, normalize, diff, persist in Redis, and notify.
 */
@ApplicationScoped
public class MonitoringOrchestrator {

    private final RedisPersistence persistence;
    private final Normalizer normalizer;
    private final DiffEngine diffEngine;
    private final NotificationService notificationService;
    private final RetailerProductValidator retailerProductValidator;
    private final Instance<GameSourceMonitor> allMonitors;

    public MonitoringOrchestrator(RedisPersistence persistence, Normalizer normalizer,
                                  DiffEngine diffEngine,
                                  NotificationService notificationService,
                                  RetailerProductValidator retailerProductValidator,
                                  Instance<GameSourceMonitor> allMonitors) {
        this.persistence = persistence;
        this.normalizer = normalizer;
        this.diffEngine = diffEngine;
        this.notificationService = notificationService;
        this.retailerProductValidator = retailerProductValidator;
        this.allMonitors = allMonitors;
    }

    public MonitoringRunSummary runCheck() {
        return runCheck(Set.of());
    }

    public MonitoringRunSummary runCheck(Set<String> sourceCodes) {
        OffsetDateTime startedAt = OffsetDateTime.now();
        int checked = 0, successful = 0, failed = 0, eventsCreated = 0;

        List<GameSourceMonitor> monitors = getDueMonitors(sourceCodes);
        for (GameSourceMonitor monitor : monitors) {
            checked++;
            try {
                MonitorResult result = monitor.fetchCurrentState();
                if (result.isSuccess() && result.normalizedData() != null) {
                    JsonNode currentData = result.normalizedData();
                    if (isRetailer(monitor.sourceCode())) {
                        currentData = retailerProductValidator.validate(
                            monitor.sourceCode(), monitor.sourceUrl(), currentData);
                    }

                    String hash = normalizer.computeHash(currentData);
                    JsonNode previous = persistence.getLatestSuccessfulSnapshotData(monitor.sourceCode());
                    persistence.saveSnapshot(monitor.sourceCode(), monitor.sourceUrl(),
                        currentData, hash, true, null);

                    List<ChangeEvent> events = diffEngine.diff(
                        monitor.sourceCode(), monitor.sourceUrl(), previous, currentData);
                    int createdForSource = 0;
                    for (ChangeEvent event : events) {
                        if (!persistence.saveEventIfAbsent(event)) {
                            Log.debugf("Skipping duplicate event: %s", event.getDeduplicationKey());
                            continue;
                        }
                        createdForSource++;
                        int notified = notificationService.sendNotifications(event);
                        if (notified > 0) {
                            Log.infof("Sent %d push notifications for event: %s",
                                notified, event.getEventType());
                        }
                    }
                    eventsCreated += createdForSource;

                    if (isRetailer(monitor.sourceCode())) {
                        persistOffers(monitor.sourceCode(), currentData);
                    }

                    successful++;
                    Log.infof("Monitor %s: SUCCESS (hash=%s, %d events)",
                        monitor.sourceCode(), hash != null ? hash.substring(0, 8) : "null",
                        createdForSource);
                } else {
                    persistence.saveSnapshot(monitor.sourceCode(), monitor.sourceUrl(),
                        null, null, false,
                        result.errorMessage() != null ? result.errorMessage() : result.status().name());
                    failed++;
                    Log.warnf("Monitor %s: %s — %s", monitor.sourceCode(),
                        result.status(), result.errorMessage());
                }
            } catch (Exception e) {
                persistence.saveSnapshot(monitor.sourceCode(), monitor.sourceUrl(),
                    null, null, false, e.getMessage());
                failed++;
                Log.errorf(e, "Monitor %s: unexpected error", monitor.sourceCode());
            }
        }

        return new MonitoringRunSummary(startedAt, OffsetDateTime.now(),
            checked, successful, failed, eventsCreated);
    }

    private List<GameSourceMonitor> getDueMonitors(Set<String> sourceCodes) {
        List<GameSourceMonitor> monitors = new ArrayList<>();
        int discovered = 0;
        for (GameSourceMonitor monitor : allMonitors) {
            discovered++;
            boolean explicitlyRequested = !sourceCodes.isEmpty()
                && sourceCodes.contains(monitor.sourceCode());
            if (explicitlyRequested || (sourceCodes.isEmpty() && isDue(monitor))) {
                monitors.add(monitor);
            }
        }
        if (discovered == 0) {
            Log.warn("No GameSourceMonitor beans discovered via Instance<> injection. "
                + "Verify monitors are @ApplicationScoped and in a scanned package.");
        }
        return monitors;
    }

    private boolean isDue(GameSourceMonitor monitor) {
        try {
            OffsetDateTime lastCheck = persistence.getLatestSnapshotTime(monitor.sourceCode());
            return lastCheck == null
                || lastCheck.isBefore(OffsetDateTime.now().minusSeconds(monitor.checkIntervalSeconds()));
        } catch (Exception e) {
            Log.warnf("Could not evaluate schedule for %s; running it: %s",
                monitor.sourceCode(), e.getMessage());
            return true;
        }
    }

    private boolean isRetailer(String sourceCode) {
        return sourceCode.equals("PS_STORE") || sourceCode.equals("XBOX_STORE")
            || sourceCode.equals("ROCKSTAR_STORE") || sourceCode.equals("GALAXUS")
            || sourceCode.equals("WOG") || sourceCode.equals("AMAZON_FR");
    }

    private void persistOffers(String sourceCode, JsonNode data) {
        JsonNode products = data.get("products");
        if (products == null || !products.isArray()) return;

        List<String> knownEditionIds = persistence.getEditionIds();
        Set<String> seenOfferIds = new HashSet<>();

        for (JsonNode product : products) {
            String productName = product.has("name") ? product.get("name").asText() : null;
            if (productName == null) continue;

            String aiEdition = product.has("edition")
                ? product.get("edition").asText().toLowerCase() : null;
            String editionId = aiEdition != null ? matchEdition(aiEdition, knownEditionIds) : null;
            if (editionId == null) editionId = matchEdition(productName, knownEditionIds);
            if (editionId == null) {
                Log.debugf("Could not match product '%s' to any known edition", productName);
                continue;
            }

            String url = product.has("url") ? product.get("url").asText() : null;
            String platform = product.has("platform") ? product.get("platform").asText() : null;
            String availability = product.has("availability")
                ? product.get("availability").asText() : "UNKNOWN";
            availability = switch (availability.toUpperCase()) {
                case "PREORDER" -> "PREORDER_AVAILABLE";
                case "IN_STOCK" -> "AVAILABLE";
                default -> availability.toUpperCase();
            };
            String priceText = product.has("price") ? product.get("price").asText(null) : null;
            BigDecimal price = priceText == null || priceText.isBlank()
                ? null : new BigDecimal(priceText);
            String currency = product.hasNonNull("currency")
                ? product.get("currency").asText() : currencyFor(sourceCode);
            String offerId = sourceCode + ":" + editionId + ":"
                + (platform != null ? platform : "UNKNOWN");
            seenOfferIds.add(offerId);

            persistence.upsertOffer(offerId, editionId, sourceCode,
                platform != null ? platform : "", countryFor(sourceCode), price,
                currency, url != null ? url : "", availability,
                "PREORDER_AVAILABLE".equals(availability)
                    || "IN_STOCK".equals(availability)
                    || "AVAILABLE".equals(availability));

            persistence.deactivateOffer(sourceCode + ":" + editionId);
        }

        persistence.markMissingOffers(sourceCode, seenOfferIds);
        Log.debugf("Persisted %d offers for retailer %s", seenOfferIds.size(), sourceCode);
    }

    private String countryFor(String sourceCode) {
        return switch (sourceCode) {
            case "AMAZON_FR" -> "FR";
            case "ROCKSTAR_STORE" -> "US";
            default -> "CH";
        };
    }

    private String currencyFor(String sourceCode) {
        return switch (sourceCode) {
            case "AMAZON_FR" -> "EUR";
            case "ROCKSTAR_STORE" -> "USD";
            default -> "CHF";
        };
    }

    private String matchEdition(String productName, List<String> editionIds) {
        String lower = productName.toLowerCase().replaceAll("[^a-z]", "");
        for (String id : editionIds) {
            String lowerId = id.toLowerCase();
            if (lowerId.contains("standard") && lower.contains("standard")) return id;
            if (lowerId.contains("ultimate") && lower.contains("ultimate")) return id;
            if (lowerId.contains("collector") && lower.contains("collector")) return id;
            if (lowerId.contains("deluxe") && lower.contains("deluxe")) return id;
        }
        if (!lower.contains("ultimate") && !lower.contains("collector")
            && !lower.contains("deluxe")) {
            return editionIds.stream()
                .filter(id -> id.toLowerCase().contains("standard"))
                .findFirst().orElse(null);
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
