package com.gtavi.monitoring.core;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;
import io.quarkiverse.langchain4j.RegisterAiService;

@RegisterAiService(chatMemoryProviderSupplier = RegisterAiService.NoChatMemoryProviderSupplier.class)
public interface RockstarMediaExtractor {
    @SystemMessage(ExtractionInstructions.EVIDENCE + ExtractionInstructions.VIDEOS + """
        Extract actual GTA VI videos from the supplied page evidence, including embedded player metadata.
        Keep relative video/thumbnail URLs exactly as observed; the backend resolves them against the source page.
        A label mentioning trailer without a corresponding video URL is not a complete video item.
        """)
    @UserMessage("Extract GTA VI videos from this evidence chunk:\n{{html}}")
    RockstarMediaData extract(@V("html") String html);
}
