package com.gtavi.monitoring.core;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;
import io.quarkiverse.langchain4j.RegisterAiService;

@RegisterAiService(chatMemoryProviderSupplier = RegisterAiService.NoChatMemoryProviderSupplier.class)
public interface RockstarEditionsExtractor {
    @SystemMessage(ExtractionInstructions.EVIDENCE + PlatformNames.EXTRACTION_RULES + ExtractionInstructions.EDITIONS + """
        Extract GTA VI game editions. Return editions and hasCollectorEdition.
        editions: array of name, type, description, features, platforms, preorderAvailable.
        name: exact edition name; do not turn an included bonus, location or cosmetic item into a separate edition.
        description: a supporting description excerpt or null.
        features: only explicitly included features for this edition; preserve their wording.
        platforms: only platform codes associated with this edition; do not copy another edition's platforms.
        preorderAvailable: true for an explicit active preorder offer for this edition;
        false only if preorder is explicitly closed/unavailable; otherwise null.
        hasCollectorEdition: true when a collector game edition is explicitly present;
        false only when the source explicitly states none is announced; otherwise null.
        Absence of a collector edition from this partial chunk does not mean false.
        """)
    @UserMessage("Extract GTA VI game editions from this evidence chunk:\n{{html}}")
    RockstarEditionsData extract(@V("html") String html);
}
