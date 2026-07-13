package com.gtavi.monitoring.core;

/**
 * Status of a source monitoring check.
 */
public enum MonitorStatus {
    SUCCESS,
    NO_RELEVANT_DATA,
    TEMPORARY_FAILURE,
    PARSER_FAILURE,
    BLOCKED,
    RATE_LIMITED,
    UNKNOWN_FAILURE
}
