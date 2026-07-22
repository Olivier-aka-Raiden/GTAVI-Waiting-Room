package com.gtavi.api.dto;

import com.fasterxml.jackson.annotation.JsonAutoDetect;

import java.time.OffsetDateTime;

import static com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility.ANY;

/**
 * Change event information for the API response.
 */
@JsonAutoDetect(fieldVisibility = ANY)
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
