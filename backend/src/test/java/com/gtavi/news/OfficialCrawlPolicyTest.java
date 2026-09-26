package com.gtavi.news;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class OfficialCrawlPolicyTest {
    private static final String ROOT = "https://www.rockstargames.com";
    @Test
    void assetsAndTranslationsAreNotDiscoveredOrPublished() {
        for (String path : new String[]{"/VI/_next/static/media/edition.jpg",
                "/VI/image.JPG?width=100", "/VI/assets/cover", "/VI/ja-JP",
                "/VI/zh-Hans-CN/music", "/VI/en-US/", "/VI/music.mp3"}) {
            assertFalse(OfficialUrlPolicy.crawlable(ROOT + path), path);
            assertFalse(OfficialNewsMonitor.discoverable(ROOT + path), path);
            assertFalse(OfficialNewsMonitor.publishable(ROOT + path), path);
        }
        assertTrue(OfficialNewsMonitor.publishable(ROOT + "/VI/vice-city-collection"));
        assertTrue(OfficialNewsMonitor.publishable(ROOT + "/VI/music"));
        assertTrue(OfficialUrlPolicy.crawlable("https://store.rockstargames.com/en/products/the-album"));
    }

    @Test
    void unexpectedBinaryResponsesAreSkippedEvenWithoutAnExtension() {
        for (String type : new String[]{"image/jpeg", "application/octet-stream", "text/css", "application/json"})
            assertThrows(OfficialPageFetcher.NonPageResourceException.class,
                () -> OfficialPageFetcher.requirePageContentType(type));
        assertDoesNotThrow(() -> OfficialPageFetcher.requirePageContentType("text/html; charset=UTF-8"));
        assertDoesNotThrow(() -> OfficialPageFetcher.requirePageContentType("application/xhtml+xml"));
    }

    @Test
    void knownAssetsAreRejectedBeforeMakingANetworkRequest() {
        assertThrows(OfficialPageFetcher.NonPageResourceException.class,
            () -> new OfficialPageFetcher().fetch(ROOT + "/VI/_next/static/media/edition.jpg"));
    }
}
