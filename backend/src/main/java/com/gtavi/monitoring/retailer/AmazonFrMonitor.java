package com.gtavi.monitoring.retailer;

import com.gtavi.monitoring.core.*;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * Monitors Amazon France for GTA VI listings and pre-orders.
 * Adaptive — as soon as Amazon lists the Collector's Edition
 * (or any new edition), the AI extraction picks it up and the
 * diff engine creates the appropriate event.
 */
@ApplicationScoped
public class AmazonFrMonitor implements GameSourceMonitor {

    private static final String CODE = "AMAZON_FR";
    private static final String URL = "https://www.amazon.fr/s?k=grand+theft+auto+vi";

    @Inject HttpFetcher fetcher;
    @Inject AiExtractionService aiExtraction;

    @Override public String sourceCode() { return CODE; }
    @Override public String sourceUrl() { return URL; }
    @Override public String sourceName() { return "Amazon.fr"; }
    @Override public boolean isOfficial() { return false; }
    @Override public int checkIntervalSeconds() { return 1800; }

    @Override
    public MonitorResult fetchCurrentState() {
        try {
            String html = fetcher.fetch(URL);
            var data = aiExtraction.extractFromHtml(html, "retailer");
            if (data == null) return MonitorResult.failure(CODE, URL, MonitorStatus.PARSER_FAILURE, "AI extraction null");
            return MonitorResult.success(CODE, URL, data, null);
        } catch (Exception e) {
            Log.errorf("Amazon FR monitor failed: %s", e.getMessage());
            return MonitorResult.failure(CODE, URL, MonitorStatus.TEMPORARY_FAILURE, e.getMessage());
        }
    }
}
