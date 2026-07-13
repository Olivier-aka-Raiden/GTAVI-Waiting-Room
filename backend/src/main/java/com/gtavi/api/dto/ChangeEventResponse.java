package com.gtavi.api.dto;

import java.time.OffsetDateTime;

/**
 * Change event information for the API response.
 */
public record ChangeEventResponse(
    String id,
    String eventType,
    String priority,
    String title,
    String description,
    String oldValue,
    String newValue,
    String evidenceUrl,
    OffsetDateTime detectedAt
) {}
