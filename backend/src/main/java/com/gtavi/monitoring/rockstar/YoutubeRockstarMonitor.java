package com.gtavi.monitoring.rockstar;

import com.gtavi.monitoring.core.*;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/** Monitors only the verified Rockstar Games YouTube Atom feed. */
@ApplicationScoped
public class YoutubeRockstarMonitor implements GameSourceMonitor {
    @Inject HttpFetcher fetcher;
    @Override public String sourceCode() { return RockstarYoutubeFeed.SOURCE; }
    @Override public String sourceUrl() { return RockstarYoutubeFeed.URL; }
    @Override public String sourceName() { return "Rockstar Games YouTube"; }
    @Override public boolean isOfficial() { return true; }
    @Override public int checkIntervalSeconds() { return 900; }

    @Override public MonitorResult fetchCurrentState() {
        try {
            return MonitorResult.success(sourceCode(), sourceUrl(),
                RockstarYoutubeFeed.extract(fetcher.fetch(sourceUrl())), null);
        } catch (Exception e) {
            Log.errorf("Rockstar YouTube monitor failed: %s", e.getMessage());
            return MonitorResult.failure(sourceCode(), sourceUrl(), MonitorStatus.TEMPORARY_FAILURE, e.getMessage());
        }
    }
}
