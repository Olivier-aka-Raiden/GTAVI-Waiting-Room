package com.gtavi.monitoring.core;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;
import io.quarkiverse.langchain4j.RegisterAiService;

@RegisterAiService(chatMemoryProviderSupplier = RegisterAiService.NoChatMemoryProviderSupplier.class)
public interface YoutubeRssExtractor {
    @SystemMessage(ExtractionInstructions.EVIDENCE + ExtractionInstructions.VIDEOS + """
        Extract videos from YouTube Atom/RSS XML. Read each entry/item independently.
        For Atom entries, videoUrl comes from link rel=alternate href, not link text or the feed's self/channel link.
        publicationDate comes from the entry's published element, not updated.
        thumbnailUrl comes from media:thumbnail url when present.
        For an RSS item, use its link text and pubDate; convert an explicit RFC-822 date to ISO-8601.
        Do not mix one entry's title with another entry's link or timestamp.
        Include only titles explicitly identifying GTA VI, Grand Theft Auto VI, GTA 6 or Grand Theft Auto 6.
        The Rockstar channel name alone does not make a video GTA VI-related.
        """)
    @UserMessage("Extract GTA VI videos from this YouTube feed evidence:\n{{xml}}")
    RockstarMediaData extract(@V("xml") String xml);
}
