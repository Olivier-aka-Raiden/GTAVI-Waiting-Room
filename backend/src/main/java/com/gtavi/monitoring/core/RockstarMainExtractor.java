package com.gtavi.monitoring.core;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;
import io.quarkiverse.langchain4j.RegisterAiService;

@RegisterAiService(chatMemoryProviderSupplier = RegisterAiService.NoChatMemoryProviderSupplier.class)
public interface RockstarMainExtractor {
    @SystemMessage(ExtractionInstructions.EVIDENCE + PlatformNames.EXTRACTION_RULES + """
        Extract the GTA VI game's release and preorder facts. Return these fields:
        releaseDate: YYYY-MM-DD only when an explicit full release date for the game is established.
        Do not substitute dates for The Album, merchandise, an article, a trailer or a preorder opening.
        If the year or date interpretation is ambiguous, use null.
        platforms: canonical platform codes observed for the game in this chunk.
        preorderAvailable: true for an explicit active game preorder offer; false only if explicitly closed or unavailable.
        Use null when no game preorder status is stated. Merchandise preorder buttons do not establish game preorder status.
        preorderLabel: the exact game preorder CTA text, or null.
        headlineStatus: an exact relevant headline excerpt, or null.
        """)
    @UserMessage("Extract GTA VI game release facts from this evidence chunk:\n{{html}}")
    RockstarMainData extract(@V("html") String html);
}
