package com.gtavi.monitoring.retailer;

import com.gtavi.monitoring.core.*;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * Monitors the Rockstar Games official store for GTA VI pre-orders.
 * As of July 2026, Rockstar already offers pre-orders for Standard
 * and Ultimate editions directly on their site. The AI extraction
 * detects edition names, prices, platforms, and purchase URLs.
 */
@ApplicationScoped
public class RockstarStoreMonitor implements GameSourceMonitor {

    private static final String CODE = "ROCKSTAR_STORE";
    private static final String URL = "https://www.rockstargames.com/VI/editions";

    @Inject HttpFetcher fetcher;
    @Inject AiExtractionService aiExtraction;

    @Override public String sourceCode() { return CODE; }
    @Override public String sourceUrl() { return URL; }
    @Override public String sourceName() { return "Rockstar Games Store"; }
    @Override public boolean isOfficial() { return true; }
    @Override public int checkIntervalSeconds() { return 1800; }

    @Override
    public MonitorResult fetchCurrentState() {
        try {
            String html = fetcher.fetch(URL);
            var data = aiExtraction.extractFromHtml(html, "retailer");
            if (data == null) return MonitorResult.failure(CODE, URL, MonitorStatus.PARSER_FAILURE, "AI extraction null");
            return MonitorResult.success(CODE, URL, data, null);
        } catch (Exception e) {
            Log.errorf("Rockstar Store monitor failed: %s", e.getMessage());
            return MonitorResult.failure(CODE, URL, MonitorStatus.TEMPORARY_FAILURE, e.getMessage());
        }
    }
}
