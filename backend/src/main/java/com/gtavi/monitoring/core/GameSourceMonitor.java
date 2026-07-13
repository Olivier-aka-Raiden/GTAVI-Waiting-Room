package com.gtavi.monitoring.core;

/**
 * A source monitor that fetches and extracts structured data from an external source.
 * Each implementation corresponds to one monitored website (Rockstar main page, editions, etc.).
 */
public interface GameSourceMonitor {

    /** Unique code for this source (e.g., "ROCKSTAR_MAIN"). */
    String sourceCode();

    /** The URL this monitor fetches. */
    String sourceUrl();

    /** Human-readable name. */
    String sourceName();

    /** Whether this is an official Rockstar source. */
    boolean isOfficial();

    /** Recommended check interval in seconds. */
    int checkIntervalSeconds();

    /** Fetch and parse the current state from the source. */
    MonitorResult fetchCurrentState();
}
