package com.gtavi.monitoring.core;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;
import io.quarkiverse.langchain4j.RegisterAiService;

/**
 * Extracts structured release-date data from Rockstar's GTA VI main page.
 * Returns a strongly-typed RockstarMainData — LangChain4j handles JSON deserialization.
 */
@RegisterAiService
public interface RockstarMainExtractor {

    @SystemMessage("""
        You extract structured GTA VI data from HTML content.
        Return a JSON object with these fields:
        - releaseDate: string in YYYY-MM-DD format, or null if not found
        - platforms: array of strings (e.g. ["PS5", "XSX"])
        - preorderAvailable: boolean
        - preorderLabel: string (the CTA text on the page)
        - headlineStatus: string (the main headline or status text)
        Only use information you can actually see in the HTML.
        """)
    @UserMessage("Extract GTA VI release info from this page:\n{{html}}")
    RockstarMainData extract(@V("html") String html);
}
