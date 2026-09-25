package com.gtavi.domain;

import java.time.OffsetDateTime;
import java.util.Map;

/**
 * One durable notification attempt stream, uniquely identified by
 * {@code (eventId, installationId)}.
 */
public record NotificationDelivery(
    String id,
    String eventId,
    String installationId,
    String title,
    String body,
    Map<String, String> data,
    NotificationDeliveryStatus status,
    int attempts,
    OffsetDateTime nextAttemptAt,
    String providerMessageId,
    String lastError,
    OffsetDateTime sentAt,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt
) {
    public static NotificationDelivery queued(
        String eventId,
        String installationId,
        String title,
        String body,
        Map<String, String> data,
        OffsetDateTime now
    ) {
        return new NotificationDelivery(
            eventId + ":" + installationId,
            eventId,
            installationId,
            title,
            body,
            Map.copyOf(data),
            NotificationDeliveryStatus.QUEUED,
            0,
            now,
            null,
            null,
            null,
            now,
            now
        );
    }
}
