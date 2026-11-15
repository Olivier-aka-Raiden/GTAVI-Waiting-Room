package com.gtavi.monitoring.rockstar;

import com.fasterxml.jackson.databind.JsonNode;
import com.gtavi.config.RedisBackedTest;
import com.gtavi.domain.ChangeEvent;
import com.gtavi.domain.Trailer;
import com.gtavi.monitoring.core.HttpFetcher;
import com.gtavi.monitoring.core.MonitoringOrchestrator;
import com.gtavi.persistence.RedisPersistence;
import io.quarkus.test.junit.QuarkusMock;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.Set;
import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;

/** Real feed metadata, with only the remote HTTP boundary replaced. */
@QuarkusTest
class RockstarYoutubePipelineTest extends RedisBackedTest {
    @Inject MonitoringOrchestrator orchestrator;
    @Inject RedisPersistence persistence;

    private String fixture() throws Exception {
        try (var input = getClass().getResourceAsStream("/fixtures/rockstar-youtube-2026-11-15.xml")) {
            assertNotNull(input);
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    @Test void sourceThroughRedisAndHomeApiIncludesOfficialMainVideos() throws Exception {
        String feed = fixture();
        QuarkusMock.installMockForType(new HttpFetcher() {
            @Override public String fetch(String url) {
                assertEquals(RockstarYoutubeFeed.URL, url);
                return feed;
            }
        }, HttpFetcher.class);
        var first = orchestrator.runCheck(Set.of(RockstarYoutubeFeed.SOURCE));
        assertEquals(1, first.successfulSources());
        var stored = persistence.getTrailers("GTA_VI", null);
        assertTrue(stored.stream().anyMatch(t -> t.getVideoUrl().endsWith("_yl5ELcmugI")));
        assertTrue(stored.stream().anyMatch(t -> t.getVideoUrl().endsWith("rz1B4B2yBZY")));
        assertTrue(stored.stream().anyMatch(t -> t.getVideoUrl().endsWith("tJbzMqJGH4k")));
        assertTrue(stored.stream().anyMatch(t -> t.getTitle().equals("A Night in Vice City")),
            "The official description explicitly connects this upload to Grand Theft Auto VI");
        assertEquals(2, stored.stream().filter(t -> t.getTitle().equals("Grand Theft Auto VI: Pre-Load Now")).count(),
            "The normal upload and the Short are distinct official videos");
        assertEquals(6, stored.stream().filter(t -> t.getTitle().equals("Grand Theft Auto VI: An Extended Look — Now Playing")).count(),
            "Different Shorts sharing a title are distinct uploads");
        assertTrue(stored.stream().filter(t -> t.getTitle().equals("Grand Theft Auto VI: Pre-Load Now"))
            .allMatch(t -> "OTHER_VIDEO".equals(t.getMediaType())));
        given().get("/api/v1/games/gta-vi").then().statusCode(200)
            .body("trailers.title", hasItems("Grand Theft Auto VI: Pre-Load Now", "Grand Theft Auto VI Launch Trailer",
                "Grand Theft Auto VI: An Extended Look", "Grand Theft Auto VI Trailer 2", "Grand Theft Auto VI Trailer 1"));
        int count = stored.size();
        assertEquals(1, orchestrator.runCheck(Set.of(RockstarYoutubeFeed.SOURCE)).successfulSources());
        assertEquals(count, persistence.getTrailers("GTA_VI", null).size());
    }

    @Test void rejectsOtherChannelsAndUnrelatedGameTitles() throws Exception {
        String feed = fixture();
        assertThrows(IllegalArgumentException.class, () -> RockstarYoutubeFeed.extract(feed
            .replace(RockstarYoutubeFeed.CHANNEL, "UCaWd5_7JhbQBe4dknZhsHJg")
            .replace(RockstarYoutubeFeed.CHANNEL.substring(2), "aWd5_7JhbQBe4dknZhsHJg")));
        assertThrows(IllegalArgumentException.class, () -> RockstarYoutubeFeed.extract(feed
            .replaceFirst("<yt:channelId>" + RockstarYoutubeFeed.CHANNEL + "</yt:channelId>",
                "<yt:channelId>UCaWd5_7JhbQBe4dknZhsHJg</yt:channelId>")));
        JsonNode result = RockstarYoutubeFeed.extract(feed.replace("Grand Theft Auto VI Launch Trailer", "Grand Theft Auto V Launch Trailer"));
        for (JsonNode video : result.path("videos"))
            assertNotEquals("Grand Theft Auto V Launch Trailer", video.path("title").asText());
        JsonNode unrelated = RockstarYoutubeFeed.extract(feed.replace("A Night in Vice City", "Grand Theft Auto V Online"));
        for (JsonNode video : unrelated.path("videos"))
            assertNotEquals("Grand Theft Auto V Online", video.path("title").asText());
    }

    @Test void oldWatchMojoRecordsAndEventsAreQuarantinedWithoutDeletingEvidence() {
        var video = new Trailer();
        video.setId("legacy-watchmojo"); video.setGameCode("GTA_VI"); video.setOfficial(true);
        video.setTitle("5 Features You ONLY Get In The Ultimate Edition of GTA 6");
        video.setVideoUrl("https://www.youtube.com/watch?v=gabghuI30io");
        video.setMediaType("OTHER_VIDEO"); video.setPublicationDate(OffsetDateTime.now());
        video.setSourceUrl("https://www.youtube.com/feeds/videos.xml?channel_id=UCaWd5_7JhbQBe4dknZhsHJg");
        persistence.saveTrailer(video);
        var event = new ChangeEvent();
        event.setSourceCode("ROCKSTAR_YOUTUBE"); event.setUserVisible(true);
        event.setTitle(video.getTitle()); event.setDeduplicationKey("old-watchmojo");
        persistence.saveEventIfAbsent(event);
        assertTrue(persistence.getEvents("GTA_VI", 0, 100).stream().anyMatch(e -> e.getId().equals(event.getId())));
        persistence.quarantineLegacyYoutube();
        assertFalse(persistence.getEvents("GTA_VI", 0, 100).stream().anyMatch(e -> e.getId().equals(event.getId())));
        assertTrue(persistence.isLegacyYoutubeEvent(event.getId()));
        assertFalse(persistence.getTrailers("GTA_VI", null).stream().anyMatch(t -> t.getId().equals(video.getId())));
        given().get("/api/v1/games/gta-vi/trailers").then().statusCode(200)
            .body("title", not(hasItem(video.getTitle())));
    }
}
