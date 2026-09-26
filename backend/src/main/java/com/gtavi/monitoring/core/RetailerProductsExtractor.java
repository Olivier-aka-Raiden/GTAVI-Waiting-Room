package com.gtavi.monitoring.core;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;
import io.quarkiverse.langchain4j.RegisterAiService;

@RegisterAiService(chatMemoryProviderSupplier = RegisterAiService.NoChatMemoryProviderSupplier.class)
public interface RetailerProductsExtractor {
    @SystemMessage(ExtractionInstructions.EVIDENCE + PlatformNames.EXTRACTION_RULES + ExtractionInstructions.EDITIONS + """
        Extract actual GTA VI video-game listings. Return products, an array of:
        name: the exact product name; edition: the edition type; platform: its canonical platform code.
        url: copy the actual product-detail or purchase URL present in this chunk.
        Relative URLs are valid; the backend resolves them against the source URL.
        Never invent a hostname, URL, query parameter or path. Do not use a search/results page as a product URL.
        price: positive numeric total price for this exact listing, without currency symbols.
        Never substitute a discount amount, deposit, installment, price range minimum or another product's price.
        currency: CHF, EUR or USD only when unambiguously stated for this price; otherwise null.
        If either price or its supported currency is unknown, return both as null. A dollar sign alone is ambiguous.
        availability: IN_STOCK, OUT_OF_STOCK, PREORDER, COMING_SOON, UNAVAILABLE, or UNKNOWN.
        Active pre-order -> PREORDER; in stock -> IN_STOCK; explicitly sold out -> OUT_OF_STOCK;
        announced without ordering -> COMING_SOON; explicitly unavailable -> UNAVAILABLE.
        Missing stock text, a fetch error, or missing price -> UNKNOWN, not UNAVAILABLE.
        Keep a confirmed listing when price or platform details are unknown.
        Include every observed GTA VI game listing, retaining distinct product URLs and platforms.
        Exclude GTA V, unrelated games, soundtrack/music products, merchandise and standalone boxes without the game.
        """)
    @UserMessage("Extract GTA VI game listings from this evidence chunk:\n{{html}}")
    RetailerProductsData extract(@V("html") String html);
}
