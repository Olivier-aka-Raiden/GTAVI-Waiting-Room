package com.gtavi.notification;

import com.gtavi.domain.ChangeEvent;
import com.gtavi.domain.NotificationDelivery;
import com.gtavi.notification.fcm.FcmHttpSender;
import com.gtavi.persistence.RedisPersistence;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Maps ChangeEvents to push notifications, respects device preferences,
 * and handles deduplication and token invalidation.
 */
@ApplicationScoped
public class NotificationService {

    @Inject
    RedisPersistence persistence;

    @Inject
    FcmHttpSender fcmSender;

    @ConfigProperty(name = "gtavi.notifications.max-attempts", defaultValue = "5")
    int maxAttempts;

    @ConfigProperty(name = "gtavi.notifications.retry-base-seconds", defaultValue = "60")
    long retryBaseSeconds;

    @ConfigProperty(name = "gtavi.notifications.retry-max-seconds", defaultValue = "3600")
    long retryMaxSeconds;

    @ConfigProperty(name = "gtavi.notifications.outbox-batch-size", defaultValue = "100")
    int outboxBatchSize;

    @ConfigProperty(name = "gtavi.notifications.delivery-lease-seconds", defaultValue = "60")
    long deliveryLeaseSeconds;

    private static final Map<String, String> EVENT_TITLE_TEMPLATES = Map.of(
        "COLLECTOR_EDITION_ANNOUNCED", "\uD83D\uDEA8 GTA VI Collector's Edition announced!",
        "COLLECTOR_EDITION_PREORDER_OPENED", "\uD83D\uDED2 Collector's Edition pre-orders open!",
        "RELEASE_DATE_CHANGED", "\uD83D\uDCC5 GTA VI release date updated",
        "NEW_TRAILER", "\uD83C\uDFAC New GTA VI trailer released!",
        "NEW_OFFICIAL_EDITION", "\uD83D\uDCE2 New edition announced",
        "PREORDER_OPENED", "\uD83D\uDED2 GTA VI pre-orders are now available"
    );

    public QueueResult saveEventAndQueue(ChangeEvent event) {
        List<NotificationDelivery> deliveries = new ArrayList<>();
        if (event.isNotificationEligible()) {
            String preferenceField = eventTypeToPreferenceField(event.getEventType());
            if (preferenceField != null) {
                OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
                String title = EVENT_TITLE_TEMPLATES.getOrDefault(
                    event.getEventType(), event.getTitle());
                if (title == null || title.isBlank()) title = "GTA VI update";
                String body = event.getDescription() == null ? "" : event.getDescription();
                Map<String, String> data = notificationData(event);
                for (var device : persistence.getEligibleDevices(preferenceField)) {
                    deliveries.add(NotificationDelivery.queued(
                        event.getId(), device.installationId(), title, body, data, now));
                }
            }
        }

        boolean created = persistence.saveEventAndOutboxIfAbsent(event, deliveries);
        return new QueueResult(created, created ? deliveries.size() : 0);
    }

    public int processPendingDeliveries() {
        if (!fcmSender.isEnabled()) return 0;
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        var claimed = persistence.claimDueDeliveries(
            now,
            Math.max(1, outboxBatchSize),
            Duration.ofSeconds(Math.max(1, deliveryLeaseSeconds)));
        int sent = 0;
        for (var item : claimed) {
            NotificationDelivery delivery = item.delivery();
            int attempts = delivery.attempts() + 1;
            try {
                var device = persistence.getActiveDeviceToken(delivery.installationId());
                if (device == null) {
                    persistence.markDeliveryDead(item, attempts,
                        "Device is inactive or notifications are disabled", now);
                    continue;
                }

                String result = fcmSender.send(device.token(), delivery.title(),
                    delivery.body(), delivery.data());
                if ("INVALID_TOKEN".equals(result)) {
                    persistence.markDeliveryInvalidToken(item, attempts, now);
                    persistence.deactivateDevice(delivery.installationId());
                } else if (result != null && !"disabled".equals(result)) {
                    if (persistence.markDeliverySent(item, attempts, result, now)) sent++;
                } else {
                    scheduleRetryOrDead(item, attempts, "Transient FCM delivery failure", now);
                }
            } catch (RuntimeException e) {
                Log.errorf(e, "Delivery %s failed unexpectedly", delivery.id());
                safelyScheduleRetryOrDead(item, attempts,
                    "Unexpected delivery failure", now);
            }
        }
        if (!claimed.isEmpty()) {
            Log.infof("Processed %d notification deliveries; %d sent", claimed.size(), sent);
        }
        return sent;
    }

    private void safelyScheduleRetryOrDead(
        RedisPersistence.ClaimedNotificationDelivery claimed,
        int attempts,
        String error,
        OffsetDateTime now
    ) {
        try {
            scheduleRetryOrDead(claimed, attempts, error, now);
        } catch (RuntimeException persistenceFailure) {
            Log.errorf(persistenceFailure,
                "Could not persist failed delivery %s; its lease will expire for retry",
                claimed.delivery().id());
        }
    }

    private void scheduleRetryOrDead(RedisPersistence.ClaimedNotificationDelivery claimed,
                                     int attempts, String error, OffsetDateTime now) {
        if (attempts >= Math.max(1, maxAttempts)) {
            persistence.markDeliveryDead(claimed, attempts, error, now);
            return;
        }
        long multiplier = 1L << Math.min(20, Math.max(0, attempts - 1));
        long delay = Math.min(Math.max(0, retryMaxSeconds),
            Math.max(0, retryBaseSeconds) * multiplier);
        persistence.markDeliveryForRetry(claimed, attempts, error,
            now.plusSeconds(delay), now);
    }

    private Map<String, String> notificationData(ChangeEvent event) {
        Map<String, String> data = new LinkedHashMap<>();
        data.put("eventId", event.getId());
        if ("ROCKSTAR_NEWS".equals(event.getSourceCode()) && event.getNewValue() != null
            && event.getNewValue().matches("[a-f0-9]{24}"))
            data.put("url", "/?news=" + event.getNewValue());
        else data.put("url", "/#section-updates");
        if (event.getEventType() != null) data.put("eventType", event.getEventType());
        if (event.getPriority() != null) data.put("priority", event.getPriority());
        return data;
    }

    private String eventTypeToPreferenceField(String eventType) {
        return switch (eventType) {
            case "COLLECTIBLE_ANNOUNCED", "COLLECTOR_EDITION_ANNOUNCED" -> "collectorEditionAnnouncement";
            case "COLLECTIBLE_PREORDER_OPENED", "COLLECTOR_LISTING_DETECTED_AT_RETAILER",
                 "COLLECTOR_EDITION_PREORDER_OPENED" -> "collectorEditionPreorder";
            case "RELEASE_DATE_CHANGED" -> "releaseDateChanges";
            case "NEW_TRAILER" -> "newOfficialTrailers";
            case "MUSIC_PREORDER_OPENED", "MUSIC_ANNOUNCED", "MAJOR_OFFICIAL_NEWS", "NEW_OFFICIAL_EDITION", "EDITION_REMOVED",
                 "PREORDER_OPENED", "PREORDER_CLOSED" -> "majorRockstarNews";
            case "OFFICIAL_NEWS", "NEW_OFFICIAL_VIDEO" -> "generalNews";
            case "BACK_IN_STOCK" -> "backInStock";
            case "OUT_OF_STOCK" -> "outOfStock";
            case "PRICE_CHANGED" -> "priceChanges";
            default -> null;
        };
    }

    public record QueueResult(boolean created, int deliveriesQueued) {}
}
