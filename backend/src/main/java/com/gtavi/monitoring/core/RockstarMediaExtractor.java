package com.gtavi.monitoring.core;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;
import io.quarkiverse.langchain4j.RegisterAiService;

/**
 * Extracts video/trailer data from Rockstar's GTA VI media page.
 */
@RegisterAiService
public interface RockstarMediaExtractor {

    @SystemMessage("""
        You extract GTA VI video information from HTML.
        Return a JSON object with:
        - videos: array of objects with {title, mediaType, publicationDate, videoUrl, thumbnailUrl}
          - mediaType must be one of: TRAILER, GAMEPLAY, CHARACTER_CLIP, COVER_ART_ANIMATION, OTHER_VIDEO
          - Classify by title: "trailer"→TRAILER, "gameplay"→GAMEPLAY, "cover art"→COVER_ART_ANIMATION
          - publicationDate in YYYY-MM-DD format
        Only report videos you can actually see in the HTML.
        """)
    @UserMessage("Extract GTA VI videos from this page:\n{{html}}")
    RockstarMediaData extract(@V("html") String html);
}
