package com.gtavi.monitoring.core;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.Instant;

/**
 * Result of a single source monitoring check.
 * Contains the normalized data, hash, status, and any error.
 */
public record MonitorResult(
    String sourceCode,
    String sourceUrl,
    Instant fetchedAt,
    MonitorStatus status,
    JsonNode normalizedData,
    String normalizedHash,
    String errorMessage
) {
    public boolean isSuccess() {
        return status == MonitorStatus.SUCCESS;
    }

    public static MonitorResult success(String sourceCode, String sourceUrl,
                                         JsonNode data, String hash) {
        return new MonitorResult(sourceCode, sourceUrl, Instant.now(),
            MonitorStatus.SUCCESS, data, hash, null);
    }

    public static MonitorResult failure(String sourceCode, String sourceUrl,
                                         MonitorStatus status, String error) {
        return new MonitorResult(sourceCode, sourceUrl, Instant.now(),
            status, null, null, error);
    }
}
