package com.gtavi.monitoring.retailer;

import com.gtavi.monitoring.core.*;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class XboxStoreMonitor implements GameSourceMonitor {

    private static final String CODE = "XBOX_STORE";
    private static final String URL = "https://www.xbox.com/en-ch/games/store/search?query=grand+theft+auto+vi";

    @Inject HttpFetcher fetcher;
    @Inject AiExtractionService aiExtraction;

    @Override public String sourceCode() { return CODE; }
    @Override public String sourceUrl() { return URL; }
    @Override public String sourceName() { return "Xbox Store"; }
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
            Log.errorf("Xbox Store monitor failed: %s", e.getMessage());
            return MonitorResult.failure(CODE, URL, MonitorStatus.TEMPORARY_FAILURE, e.getMessage());
        }
    }
}
