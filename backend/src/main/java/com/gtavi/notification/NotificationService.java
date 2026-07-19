package com.gtavi.notification;

import com.gtavi.domain.ChangeEvent;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.neo4j.driver.Driver;

import java.util.*;

/**
 * Maps ChangeEvents to push notifications, respects device preferences,
 * and handles deduplication and token invalidation.
 */
@ApplicationScoped
public class NotificationService {

    @Inject
    Driver driver;

    @Inject
    FcmSender fcmSender;

    private static final Map<String, String> EVENT_TITLE_TEMPLATES = Map.of(
        "COLLECTOR_EDITION_ANNOUNCED", "\uD83D\uDEA8 GTA VI Collector's Edition announced!",
        "COLLECTOR_EDITION_PREORDER_OPENED", "\uD83D\uDED2 Collector's Edition pre-orders open!",
        "RELEASE_DATE_CHANGED", "\uD83D\uDCC5 GTA VI release date updated",
        "NEW_TRAILER", "\uD83C\uDFAC New GTA VI trailer released!",
        "NEW_OFFICIAL_EDITION", "\uD83D\uDCE2 New edition announced",
        "PREORDER_OPENED", "\uD83D\uDED2 GTA VI pre-orders are now available"
    );

    /**
     * Send notifications for a change event to all eligible devices.
     */
    public int sendNotifications(ChangeEvent event) {
        if (!event.isNotificationEligible()) {
            Log.debugf("Event %s not notification-eligible", event.getEventType());
            return 0;
        }

        String title = EVENT_TITLE_TEMPLATES.getOrDefault(
            event.getEventType(), event.getTitle());
        String body = event.getDescription() != null ? event.getDescription() : "";

        // Dedup check
        if (isAlreadyDelivered(event.getId())) {
            Log.debugf("Event %s already delivered, skipping", event.getDeduplicationKey());
            return 0;
        }

        // Find eligible devices
        List<DeviceToken> devices = getEligibleDevices(event.getEventType());

        int sent = 0;
        for (DeviceToken device : devices) {
            // Build deep link data
            Map<String, String> data = Map.of(
                "eventId", event.getId(),
                "eventType", event.getEventType(),
                "priority", event.getPriority()
            );

            String result = fcmSender.send(device.token, title, body, data);

            if ("INVALID_TOKEN".equals(result)) {
                deactivateDevice(device.installationId);
                Log.infof("Deactivated device with invalid token: %s", device.installationId);
            } else if (result != null && !"disabled".equals(result)) {
                recordDelivery(event.getId(), device.installationId, result);
                sent++;
            }
        }

        Log.infof("Sent %d notifications for event: %s", sent, event.getEventType());
        return sent;
    }

    private boolean isAlreadyDelivered(String eventId) {
        try (var session = driver.session()) {
            var result = session.run(
                "MATCH (nd:NotificationDelivery {changeEventId: $eventId}) RETURN count(nd) > 0 AS exists",
                Map.of("eventId", eventId)
            );
            return result.single().get("exists").asBoolean();
        }
    }

    private List<DeviceToken> getEligibleDevices(String eventType) {
        String prefField = eventTypeToPreferenceField(eventType);
        if (prefField == null) return List.of();

        try (var session = driver.session()) {
            var result = session.run("""
                MATCH (d:DeviceInstallation {active: true, notificationsEnabled: true})
                OPTIONAL MATCH (d)-[:HAS_PREFERENCES]->(np:NotificationPreference)
                WHERE np IS NULL OR np.`%s` = true
                RETURN d.installationId AS installationId, d.pushToken AS token
                """.formatted(prefField));

            List<DeviceToken> devices = new ArrayList<>();
            while (result.hasNext()) {
                var record = result.next();
                String token = record.get("token").asString();
                if (token != null && !token.isEmpty()) {
                    devices.add(new DeviceToken(
                        record.get("installationId").asString(), token));
                }
            }
            return devices;
        }
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

    private void deactivateDevice(String installationId) {
        try (var session = driver.session()) {
            session.run(
                "MATCH (d:DeviceInstallation {installationId: $id}) SET d.active = false",
                Map.of("id", installationId));
        }
    }

    private void recordDelivery(String eventId, String installationId, String messageId) {
        try (var session = driver.session()) {
            session.run("""
                CREATE (nd:NotificationDelivery {
                    changeEventId: $eventId,
                    deviceInstallationId: $installationId,
                    providerMessageId: $messageId,
                    status: 'SENT',
                    sentAt: datetime(),
                    createdAt: datetime()
                })
                """, Map.of("eventId", eventId, "installationId", installationId,
                    "messageId", messageId));
        }
    }

    record DeviceToken(String installationId, String token) {}
}
