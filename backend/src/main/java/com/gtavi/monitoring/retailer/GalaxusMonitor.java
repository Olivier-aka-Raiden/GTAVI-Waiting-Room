package com.gtavi.monitoring.retailer;

import com.gtavi.monitoring.core.*;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class GalaxusMonitor implements GameSourceMonitor {

    private static final String CODE = "GALAXUS";
    private static final String URL = "https://www.galaxus.ch/en/search?q=grand+theft+auto+vi";

    @Inject HttpFetcher fetcher;
    @Inject AiExtractionService aiExtraction;

    @Override public String sourceCode() { return CODE; }
    @Override public String sourceUrl() { return URL; }
    @Override public String sourceName() { return "Galaxus"; }
    @Override public boolean isOfficial() { return false; }
    @Override public int checkIntervalSeconds() { return 900; }

    @Override
    public MonitorResult fetchCurrentState() {
        try {
            String html = fetcher.fetch(URL);
            var data = aiExtraction.extractFromHtml(html, "retailer");
            if (data == null) return MonitorResult.failure(CODE, URL, MonitorStatus.PARSER_FAILURE, "AI extraction null");
            return MonitorResult.success(CODE, URL, data, null);
        } catch (Exception e) {
            Log.errorf("Galaxus monitor failed: %s", e.getMessage());
            return MonitorResult.failure(CODE, URL, MonitorStatus.TEMPORARY_FAILURE, e.getMessage());
        }
    }
}
