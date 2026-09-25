package com.gtavi.domain;

/** Durable lifecycle states for one event-to-installation notification. */
public enum NotificationDeliveryStatus {
    QUEUED,
    SENT,
    RETRY,
    INVALID_TOKEN,
    DEAD
}
