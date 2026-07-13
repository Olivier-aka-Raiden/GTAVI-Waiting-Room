package com.gtavi.monitoring.rockstar;

import com.gtavi.monitoring.core.*;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * Monitors the Rockstar GTA VI media page for new trailers and videos.
 */
@ApplicationScoped
public class RockstarMediaMonitor implements GameSourceMonitor {

    private static final String CODE = "ROCKSTAR_MEDIA";
    private static final String URL = "https://www.rockstargames.com/VI/media/videos";

    @Inject
    HttpFetcher fetcher;

    @Inject
    AiExtractionService aiExtraction;

    @Override
    public String sourceCode() { return CODE; }

    @Override
    public String sourceUrl() { return URL; }

    @Override
    public String sourceName() { return "Rockstar GTA VI Media"; }

    @Override
    public boolean isOfficial() { return true; }

    @Override
    public int checkIntervalSeconds() { return 900; }

    @Override
    public MonitorResult fetchCurrentState() {
        try {
            String html = fetcher.fetch(URL);
            var data = aiExtraction.extractFromHtml(html, "rockstar_media");
            if (data == null) {
                return MonitorResult.failure(CODE, URL, MonitorStatus.PARSER_FAILURE,
                    "AI extraction returned null");
            }
            return MonitorResult.success(CODE, URL, data, null);
        } catch (Exception e) {
            Log.errorf("RockstarMediaMonitor failed: %s", e.getMessage());
            return MonitorResult.failure(CODE, URL, MonitorStatus.TEMPORARY_FAILURE,
                e.getMessage());
        }
    }
}
