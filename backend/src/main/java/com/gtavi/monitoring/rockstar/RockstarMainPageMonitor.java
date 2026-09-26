package com.gtavi.monitoring.rockstar;

import com.gtavi.monitoring.core.*;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * Monitors the Rockstar GTA VI main page for release date, platforms, and pre-order status.
 */
@ApplicationScoped
public class RockstarMainPageMonitor implements GameSourceMonitor {

    private static final String CODE = "ROCKSTAR_MAIN";
    private static final String URL = "https://www.rockstargames.com/VI/";

    @Inject
    HttpFetcher fetcher;

    @Inject
    AiExtractionService aiExtraction;

    @Override
    public String sourceCode() { return CODE; }

    @Override
    public String sourceUrl() { return URL; }

    @Override
    public String sourceName() { return "Rockstar GTA VI Main Page"; }

    @Override
    public boolean isOfficial() { return true; }

    @Override
    public int checkIntervalSeconds() { return 600; }

    @Override
    public MonitorResult fetchCurrentState() {
        try {
            String html = fetcher.fetch(URL);
            return aiExtraction.extractFromHtml(html, "rockstar_main").toMonitorResult(CODE, URL);
        } catch (Exception e) {
            Log.errorf("RockstarMainPageMonitor failed: %s", e.getMessage());
            return MonitorResult.failure(CODE, URL, MonitorStatus.TEMPORARY_FAILURE,
                e.getMessage());
        }
    }
}
