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

    @jakarta.inject.Inject com.gtavi.news.NewsRepository processing;
    @jakarta.inject.Inject GameProjectionService projections;
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
        int checked = 0, successful = 0, failed = 0, pending = 0, eventsCreated = 0;
        drainNotificationOutbox("before monitoring");

        List<GameSourceMonitor> monitors = getDueMonitors(sourceCodes);
        for (GameSourceMonitor monitor : monitors) {
            String lockName="pipeline:"+monitor.sourceCode();
            String token=processing.acquire(lockName);
            if (token==null) continue;
            checked++;
            try {
                JsonNode staged=processing.read("observation:"+monitor.sourceCode());
                MonitorResult result = staged==null ? monitor.fetchCurrentState()
                    : MonitorResult.success(monitor.sourceCode(),monitor.sourceUrl(),staged,null);
                if (result.isSuccess() && result.normalizedData() != null) {
                    JsonNode currentData = result.normalizedData();
                    if (isRetailer(monitor.sourceCode())) {
                        currentData = retailerProductValidator.validate(
                            monitor.sourceCode(), monitor.sourceUrl(), currentData);
                    }

                    processing.assertOwner(lockName,token);
                    if (staged==null) processing.write("occurrence:"+monitor.sourceCode(),
                        com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode().put("id",java.util.UUID.randomUUID().toString()));
                    processing.write("observation:"+monitor.sourceCode(),currentData);
                    String hash = normalizer.computeHash(currentData);
                    JsonNode previous = persistence.getLatestSuccessfulSnapshotData(monitor.sourceCode());


                    List<ChangeEvent> events = diffEngine.diff(
                        monitor.sourceCode(), monitor.sourceUrl(), previous, currentData);
                    var occurrence=processing.read("occurrence:"+monitor.sourceCode());
                    String occurrenceId=occurrence==null ? hash : occurrence.path("id").asText();
                    projections.project(monitor.sourceCode(),monitor.sourceUrl(),currentData);
                    int createdForSource = 0;
                    for (ChangeEvent event : events) {
                        if (Set.of("OUT_OF_STOCK","BACK_IN_STOCK","PRICE_CHANGED","RELEASE_DATE_CHANGED",
                                "PREORDER_OPENED","PREORDER_CLOSED").contains(event.getEventType()))
                            event.setDeduplicationKey(event.getDeduplicationKey()+":"+occurrenceId);
                        var queued = notificationService.saveEventAndQueue(event);
                        if (!queued.created()) {
                            Log.debugf("Skipping duplicate event: %s", event.getDeduplicationKey());
                            continue;
                        }
                        createdForSource++;
                        if (queued.deliveriesQueued() > 0) {
                            Log.infof("Queued %d push notifications for event: %s",
                                queued.deliveriesQueued(), event.getEventType());
                        }
                    }
                    eventsCreated += createdForSource;

                    if (isRetailer(monitor.sourceCode())) {
                        persistOffers(monitor.sourceCode(), currentData);
                    }

                    processing.assertOwner(lockName,token);
                    // Advance the comparison baseline only after events and projections are durable.
                    persistence.saveSnapshot(monitor.sourceCode(), monitor.sourceUrl(),
                        currentData, hash, true, null);
                    processing.delete("observation:"+monitor.sourceCode());
                    processing.delete("extraction-pending:"+monitor.sourceCode());
                    successful++;
                    Log.infof("Monitor %s: SUCCESS (hash=%s, %d events)",
                        monitor.sourceCode(), hash != null ? hash.substring(0, 8) : "null",
                        createdForSource);
                } else if (result.status() == MonitorStatus.EXTRACTION_PENDING) {
                    var progress = com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode()
                        .put("processingStatus", "EXTRACTION_PENDING").put("degraded", true)
                        .put("reason", result.errorMessage());
                    processing.write("extraction-pending:"+monitor.sourceCode(), progress);
                    persistence.saveSnapshot(monitor.sourceCode(), monitor.sourceUrl(), progress, null, false, result.errorMessage());
                    pending++;
                    Log.infof("Monitor %s: EXTRACTION_PENDING — %s", monitor.sourceCode(), result.errorMessage());
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
            } finally {
                processing.release(lockName,token);
            }
        }

        drainNotificationOutbox("after monitoring");

        return new MonitoringRunSummary(startedAt, OffsetDateTime.now(),
            checked, successful, failed, eventsCreated, pending);
    }

    private void drainNotificationOutbox(String phase) {
        try {
            notificationService.processPendingDeliveries();
        } catch (RuntimeException e) {
            Log.errorf(e, "Could not process notification outbox %s", phase);
        }
    }

    private List<GameSourceMonitor> getDueMonitors(Set<String> sourceCodes) {
        List<GameSourceMonitor> monitors = new ArrayList<>();
        int discovered = 0;
        for (GameSourceMonitor monitor : allMonitors) {
            discovered++;
            JsonNode definition=persistence.getSourceDefinition(monitor.sourceCode());
            if (definition!=null && !definition.path("enabled").asBoolean(true)) continue;
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
            int interval = processing.read("extraction-pending:"+monitor.sourceCode()) == null
                ? monitor.checkIntervalSeconds() : Math.min(300, monitor.checkIntervalSeconds());
            return lastCheck == null
                || lastCheck.isBefore(OffsetDateTime.now().minusSeconds(interval));
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
                editionId=projections.ensureRetailEdition(productName,sourceCode);
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
                ? product.get("currency").asText() : null;
            if (currency==null) price=null;
            if (price == null && currency != null) {
                final String offerCurrency = currency;
                final String offerPlatform = platform;
                price = persistence.getOffers(editionId).stream()
                    .filter(old -> java.util.Objects.equals(offerCurrency, old.getCurrency())
                        && java.util.Objects.equals(offerPlatform, old.getPlatform())
                        && OfferIdentity.url(url).equals(OfferIdentity.url(old.getUrl())))
                    .map(com.gtavi.domain.RetailOffer::getPrice).filter(java.util.Objects::nonNull).findFirst().orElse(null);
            }
            String legacyId = sourceCode + ":" + editionId + ":" + (platform != null ? platform : "UNKNOWN");
            String offerId = legacyId + ":" + com.gtavi.news.OfficialPage.fingerprint(
                OfferIdentity.url(url)+"|"+(currency==null?"":currency)+"|"+product.path("market").asText(""));
            persistence.deactivateOffer(legacyId);
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

    private String matchEdition(String productName, List<String> editionIds) {
        String lower = productName.toLowerCase().replaceAll("[^a-z]", "");
        for (String id : editionIds) {
            String lowerId = id.toLowerCase();
            if (lowerId.contains("standard") && lower.contains("standard")) return id;
            if (lowerId.contains("ultimate") && lower.contains("ultimate")) return id;
            if (lowerId.contains("collector") && lower.contains("collector")) return id;
            if (lowerId.contains("deluxe") && lower.contains("deluxe")) return id;
        }
        return null;
    }

    public record MonitoringRunSummary(
        OffsetDateTime startedAt,
        OffsetDateTime finishedAt,
        int checkedSources,
        int successfulSources,
        int failedSources,
        int eventsCreated,
        int pendingSources
    ) {}
}
