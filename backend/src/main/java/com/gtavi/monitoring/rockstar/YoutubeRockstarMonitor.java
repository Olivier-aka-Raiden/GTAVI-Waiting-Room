package com.gtavi.monitoring.rockstar;

import com.gtavi.monitoring.core.*;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * Monitors the Rockstar Games YouTube channel via RSS feed.
 * Detects new GTA VI trailers, gameplay videos, and official media.
 * When a new video appears with "Trailer" or "GTA VI" in the title,
 * the DiffEngine creates a NEW_TRAILER or NEW_OFFICIAL_VIDEO event.
 */
@ApplicationScoped
public class YoutubeRockstarMonitor implements GameSourceMonitor {

    private static final String CODE = "ROCKSTAR_YOUTUBE";
    private static final String URL =
        "https://www.youtube.com/feeds/videos.xml?channel_id=UCaWd5_7JhbQBe4dknZhsHJg";

    @Inject HttpFetcher fetcher;
    @Inject AiExtractionService aiExtraction;

    @Override public String sourceCode() { return CODE; }
    @Override public String sourceUrl() { return URL; }
    @Override public String sourceName() { return "Rockstar Games YouTube"; }
    @Override public boolean isOfficial() { return true; }
    @Override public int checkIntervalSeconds() { return 900; } // every 15 min

    @Override
    public MonitorResult fetchCurrentState() {
        try {
            String xml = fetcher.fetch(URL);
            var data = aiExtraction.extractFromHtml(xml, "youtube_rss");
            if (data == null) return MonitorResult.failure(CODE, URL,
                MonitorStatus.PARSER_FAILURE, "AI extraction null");
            return MonitorResult.success(CODE, URL, data, null);
        } catch (Exception e) {
            Log.errorf("YouTube RSS monitor failed: %s", e.getMessage());
            return MonitorResult.failure(CODE, URL,
                MonitorStatus.TEMPORARY_FAILURE, e.getMessage());
        }
    }
}
