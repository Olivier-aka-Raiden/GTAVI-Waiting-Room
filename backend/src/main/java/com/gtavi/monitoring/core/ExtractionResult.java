package com.gtavi.monitoring.core;

import com.fasterxml.jackson.databind.JsonNode;

/** A bounded extraction can make progress without either succeeding or failing. */
public record ExtractionResult(State state, JsonNode data, String reason,
                               int processedCharacters, int totalCharacters) {
    public enum State { COMPLETE, PENDING, FAILED }

    public static ExtractionResult complete(JsonNode data, int characters) {
        return new ExtractionResult(State.COMPLETE, data, null, characters, characters);
    }

    public static ExtractionResult pending(String reason, int processed, int total) {
        return new ExtractionResult(State.PENDING, null, reason, processed, total);
    }

    public static ExtractionResult failed(String reason, int processed, int total) {
        return new ExtractionResult(State.FAILED, null, reason, processed, total);
    }

    public MonitorResult toMonitorResult(String code, String url) {
        return switch (state) {
            case COMPLETE -> MonitorResult.success(code, url, data, null);
            case PENDING -> MonitorResult.pending(code, url,
                reason + " (" + processedCharacters + "/" + totalCharacters + " characters)");
            case FAILED -> MonitorResult.failure(code, url, MonitorStatus.PARSER_FAILURE, reason);
        };
    }
}
