package com.gtavi.notification;

import com.gtavi.domain.ChangeEvent;
import com.gtavi.notification.fcm.FcmHttpSender;
import com.gtavi.persistence.RedisPersistence;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

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

    private static final Map<String, String> EVENT_TITLE_TEMPLATES = Map.of(
        "COLLECTOR_EDITION_ANNOUNCED", "\uD83D\uDEA8 GTA VI Collector's Edition announced!",
        "COLLECTOR_EDITION_PREORDER_OPENED", "\uD83D\uDED2 Collector's Edition pre-orders open!",
        "RELEASE_DATE_CHANGED", "\uD83D\uDCC5 GTA VI release date updated",
        "NEW_TRAILER", "\uD83C\uDFAC New GTA VI trailer released!",
        "NEW_OFFICIAL_EDITION", "\uD83D\uDCE2 New edition announced",
        "PREORDER_OPENED", "\uD83D\uDED2 GTA VI pre-orders are now available"
    );

    public int sendNotifications(ChangeEvent event) {
        if (!event.isNotificationEligible()) {
            Log.debugf("Event %s not notification-eligible", event.getEventType());
            return 0;
        }

        String title = EVENT_TITLE_TEMPLATES.getOrDefault(event.getEventType(), event.getTitle());
        String body = event.getDescription() != null ? event.getDescription() : "";

        if (persistence.isEventAlreadyDelivered(event.getId())) {
            Log.debugf("Event %s already delivered, skipping", event.getDeduplicationKey());
            return 0;
        }

        String preferenceField = eventTypeToPreferenceField(event.getEventType());
        if (preferenceField == null) return 0;
        List<RedisPersistence.DeviceTokenRecord> devices =
            persistence.getEligibleDevices(preferenceField);

        int sent = 0;
        for (var device : devices) {
            Map<String, String> data = Map.of(
                "eventId", event.getId(),
                "eventType", event.getEventType(),
                "priority", event.getPriority()
            );

            String result = fcmSender.send(device.token(), title, body, data);
            if ("INVALID_TOKEN".equals(result)) {
                persistence.deactivateDevice(device.installationId());
                Log.infof("Deactivated device with invalid token: %s", device.installationId());
            } else if (result != null && !"disabled".equals(result)) {
                persistence.recordDelivery(event.getId(), device.installationId(), result);
                sent++;
            }
        }

        Log.infof("Sent %d notifications for event: %s", sent, event.getEventType());
        return sent;
    }

    private String eventTypeToPreferenceField(String eventType) {
        return switch (eventType) {
            case "COLLECTOR_EDITION_ANNOUNCED" -> "collectorEditionAnnouncement";
            case "COLLECTOR_LISTING_DETECTED_AT_RETAILER",
                 "COLLECTOR_EDITION_PREORDER_OPENED" -> "collectorEditionPreorder";
            case "RELEASE_DATE_CHANGED" -> "releaseDateChanges";
            case "NEW_TRAILER" -> "newOfficialTrailers";
            case "NEW_OFFICIAL_EDITION", "EDITION_REMOVED",
                 "PREORDER_OPENED", "PREORDER_CLOSED" -> "majorRockstarNews";
            case "NEW_OFFICIAL_VIDEO" -> "generalNews";
            case "BACK_IN_STOCK" -> "backInStock";
            case "OUT_OF_STOCK" -> "outOfStock";
            case "PRICE_CHANGED" -> "priceChanges";
            default -> null;
        };
    }
}
