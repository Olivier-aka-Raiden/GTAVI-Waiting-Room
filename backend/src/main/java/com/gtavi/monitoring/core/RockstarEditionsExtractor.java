package com.gtavi.monitoring.core;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;
import io.quarkiverse.langchain4j.RegisterAiService;

/**
 * Extracts edition data from Rockstar's GTA VI editions page.
 */
@RegisterAiService
public interface RockstarEditionsExtractor {

    @SystemMessage("""
        You extract GTA VI edition information from HTML.
        Return a JSON object with:
        - editions: array of objects with {name, type, description, features, platforms, preorderAvailable}
          - type must be one of: STANDARD, DELUXE, ULTIMATE, COLLECTOR, SPECIAL, BUNDLE, UPGRADE, UNKNOWN
          - If name contains "collector" (any case), set type to COLLECTOR
          - features is an array of strings describing what's included
        - hasCollectorEdition: boolean — true if any edition has type COLLECTOR
        Only report what you can see in the HTML.
        """)
    @UserMessage("Extract GTA VI editions from this page:\n{{html}}")
    RockstarEditionsData extract(@V("html") String html);
}
