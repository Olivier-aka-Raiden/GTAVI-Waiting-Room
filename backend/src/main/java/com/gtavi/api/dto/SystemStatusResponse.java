package com.gtavi.api.dto;

import com.fasterxml.jackson.annotation.JsonAutoDetect;

import java.time.OffsetDateTime;

import static com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility.ANY;

/**
 * System status for the game overview response.
 */
@JsonAutoDetect(fieldVisibility = ANY)
public record SystemStatusResponse(
    OffsetDateTime lastMonitoringRunAt,
    boolean monitoringHealthy,
    int monitoredSources,
    int healthySources
) {}
