package com.gtavi.news;

import com.gtavi.monitoring.core.ExtractionInstructions;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;
import io.quarkiverse.langchain4j.RegisterAiService;

@RegisterAiService(chatMemoryProviderSupplier = RegisterAiService.NoChatMemoryProviderSupplier.class)
public interface AnnouncementExtractor {
    @SystemMessage(ExtractionInstructions.EVIDENCE + """
        Extract GTA VI news and officially related products. Return relevant, category, importance, evidence, products, facts.
        relevant: true only for explicitly established GTA VI context. The supplied title may establish context,
        but every evidence field must quote the evidence chunk itself, not the separate title or source URL.
        category: COLLECTIBLE, MUSIC, GAME, MEDIA or NEWS; NEWS covers unfamiliar announcements.
        importance: MAJOR for significant announcements, launches, albums or limited releases; otherwise NEWS.
        evidence: an exact contiguous excerpt at least 12 characters long copied from the evidence chunk.
        facts: material non-commerce facts with subject, value, evidence, importance.
        subject is a stable concise topic; value must occur verbatim inside its exact evidence excerpt.
        Do not normalize a prose date into an ISO value absent from that excerpt.
        Fact evidence also requires at least 12 characters; importance is MAJOR or NEWS.
        Exclude navigation, decorative image changes, wording-only changes and commerce fields from facts.
        Keep unfamiliar factual announcements; an article with no products is valid.

        products: name, category, description, imageUrl, purchaseUrl, price, currency, availability,
        limited, gameIncluded, evidence.
        name: exact product name copied from the chunk, not a synthesized marketing title.
        category: COLLECTIBLE, VINYL, CD, ALBUM, MERCHANDISE or GAME.
        Include standalone collectible boxes even when the game is sold separately and collector is absent from the name.
        A collection box is one product: describe what it contains in its description and evidence, and report an
        included item as its own product only when the source gives that item its own purchase URL.
        Include official soundtracks, vinyl, CDs and merchandise; exclude unrelated GTA V and third-party speculation.
        description: copy a supporting excerpt or use null, not a paraphrase.
        imageUrl and purchaseUrl: copy observed URLs; relative URLs are allowed. Never construct them.
        Keep format-specific purchase URLs separate. Do not split one product merely because it is mentioned repeatedly.
        price and currency: an explicit positive total price and explicit ISO currency for this product,
        both present in its product evidence excerpt; otherwise both null. A bare currency symbol is not an ISO code.
        Numeric prices may normalize the source's decimal separator, but never infer a currency or use a different product's price.
        availability: PREORDER, AVAILABLE, OUT_OF_STOCK or ANNOUNCED. ANNOUNCED means no confirmed ordering status.
        limited and gameIncluded: true/false only when explicitly established in product evidence; otherwise null.
        Do not treat an Ultimate game edition as a limited physical product just because it contains bonus items.
        evidence: exact contiguous product excerpt of at least 12 characters, including the product name
        and any claimed price, currency or limited/game-inclusion statement.
        """)
    @UserMessage("Source: {{url}}\nPage title: {{title}}\nEvidence chunk:\n{{content}}")
    AnnouncementExtraction extract(@V("url") String url, @V("title") String title, @V("content") String content);
}
