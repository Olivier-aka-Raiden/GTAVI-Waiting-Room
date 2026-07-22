package com.gtavi.api.dto;

import com.fasterxml.jackson.annotation.JsonAutoDetect;

import java.time.LocalDate;
import java.time.OffsetDateTime;

import static com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility.ANY;

/**
 * Release information for the game overview response.
 */
@JsonAutoDetect(fieldVisibility = ANY)
public record ReleaseInfoResponse(
    LocalDate date,
    boolean exactTimeKnown,
    OffsetDateTime releaseTimestamp,
    String countdownPolicy,
    boolean official,
    String sourceUrl,
    OffsetDateTime lastSuccessfulCheckAt,
    OffsetDateTime lastChangedAt
) {}
