package com.gtavi.api.dto;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * System status for the game overview response.
 */
public record SystemStatusResponse(
    OffsetDateTime lastMonitoringRunAt,
    boolean monitoringHealthy
) {}
