package com.gtavi.monitoring.core;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;
import io.quarkiverse.langchain4j.RegisterAiService;

/**
 * Extracts GTA VI videos from Rockstar's YouTube RSS feed.
 * RSS feeds are XML, not HTML — but the LLM can parse either.
 * Returns RockstarMediaData (same structure as the media page extractor).
 */
@RegisterAiService
public interface YoutubeRssExtractor {

    @SystemMessage("""
        You extract video information from YouTube RSS feed XML.
        Return a JSON object with:
        - videos: array of objects with {title, mediaType, publicationDate, videoUrl, thumbnailUrl}
          - mediaType must be one of: TRAILER, GAMEPLAY, CHARACTER_CLIP, COVER_ART_ANIMATION, OTHER_VIDEO
          - Classify by title: "trailer"→TRAILER, "gameplay"→GAMEPLAY, "cover art"→COVER_ART_ANIMATION
          - publicationDate in YYYY-MM-DD format (parse from RSS pubDate)
          - videoUrl from the <link> element
          - thumbnailUrl can be null if not available in the feed
        Only include videos related to GTA VI (title contains "GTA VI", "Grand Theft Auto VI",
        "GTA 6", or "Grand Theft Auto 6").
        """)
    @UserMessage("Extract GTA VI videos from this YouTube RSS feed XML:\n{{xml}}")
    RockstarMediaData extract(@V("xml") String xml);
}
