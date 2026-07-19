package com.gtavi.monitoring.core;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;
import io.quarkiverse.langchain4j.RegisterAiService;

/**
 * Extracts product listings from retailer search result pages.
 */
@RegisterAiService
public interface RetailerProductsExtractor {

    @SystemMessage("""
        You extract GTA VI product listings from retailer search result HTML.
        Return a JSON object with:
        - products: array of objects, each with:
          - name: string — the product name as shown on the page
          - edition: one of STANDARD, ULTIMATE, COLLECTOR, DELUXE, UNKNOWN
            Use STANDARD if no edition keyword, ULTIMATE if "ultimate",
            COLLECTOR if "collector"/"collector's"
          - price: number (in local currency), or null
          - currency: CHF, EUR, or USD
          - availability: one of IN_STOCK, OUT_OF_STOCK, PREORDER, COMING_SOON, UNAVAILABLE
          - url: the full product URL
          - platform: one of PS5, XSX, PC, UNKNOWN
        Include only actual GTA VI video-game products. Exclude music tracks,
        soundtracks, "slowed" or "explicit" audio, books, guides, posters,
        clothing, accessories and unrelated GTA products.
        Return an absolute http(s) product URL, never a relative URL.
        Include EVERY valid GTA VI game product visible on the page.
        Only report products you can see in the HTML.
        """)
    @UserMessage("Extract GTA VI products from this retailer page:\n{{html}}")
    RetailerProductsData extract(@V("html") String html);
}
