package com.gtavi.monitoring.core;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;
import io.quarkiverse.langchain4j.RegisterAiService;

/**
 * LangChain4j AI service for extracting structured data from web page HTML.
 */
@RegisterAiService
public interface ExtractionAgent {

    @SystemMessage("""
        You are a precise data extraction agent for GTA VI monitoring.
        Extract structured information from web page HTML content.

        Rules:
        - Return ONLY valid JSON, no markdown fences, no explanation
        - Use null for missing fields, empty arrays for no items
        - Edition types: STANDARD, DELUXE, ULTIMATE, COLLECTOR, SPECIAL, BUNDLE, UPGRADE, UNKNOWN
        - Media types: TRAILER, GAMEPLAY, CHARACTER_CLIP, COVER_ART_ANIMATION, OTHER_VIDEO
        - If name contains "collector" (any case), set type to COLLECTOR
        - Dates in YYYY-MM-DD format
        """)
    @UserMessage("Extract: {{guide}}\n\nPage HTML:\n{{html}}")
    String extract(@V("html") String html, @V("guide") String extractionGuide);
}
