package com.gtavi.api.dto;

import java.time.LocalDate;
import java.time.OffsetDateTime;

/**
 * Release information for the game overview response.
 */
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
