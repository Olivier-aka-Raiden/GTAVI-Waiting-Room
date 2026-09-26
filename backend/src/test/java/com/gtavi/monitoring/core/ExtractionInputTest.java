package com.gtavi.monitoring.core;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ExtractionInputTest {
    @Test void decorativeAssetChangesDoNotResetSemanticInput() {
        String start = "<main><h1>GTA VI</h1><p>Coming November 19, 2026</p><picture><source srcset='";
        String end = "'><img alt='' src='decoration.jpg'></picture></main>";
        assertEquals(ExtractionInput.prepare(start + "old-file.webp 1x" + end, "rockstar_main"),
            ExtractionInput.prepare(start + "new-file.webp 2x" + end, "rockstar_main"));
    }

    @Test void preservesCommerceEvidenceAndNavigationPreorderButtons() {
        String html = """
            <nav><button>Pre-Order Now</button></nav><main>
            <h1>GTA VI collection</h1><img src="/box.jpg" alt="Collector box">
            <a href="/buy/box">Buy box</a><p>Price 399.99 USD</p></main>
            <script type="application/ld+json">{"@type":"Product","sku":"box-red","offers":{"price":399.99,"priceCurrency":"USD"}}</script>
            """;
        String input = ExtractionInput.prepare(html, "rockstar_main");
        for (String fact : new String[]{"Pre-Order Now", "/box.jpg", "/buy/box", "399.99", "USD", "box-red"})
            assertTrue(input.contains(fact), fact);
    }

    @Test void preservesFeedLinksDatesAndVideoIdentifiers() {
        String xml = "<feed><entry><title>GTA VI Trailer</title><yt:videoId>xyz</yt:videoId>"
            + "<published>2026-09-26</published><link href='https://youtube.com/watch?v=xyz'/></entry></feed>";
        String input = ExtractionInput.prepare(xml, "youtube_rss");
        assertTrue(input.contains("2026-09-26"));
        assertTrue(input.contains("yt:videoId"));
        assertTrue(input.contains("watch?v=xyz"));
    }

    @Test void embeddedOnlyFactsSurviveWithoutRepeatingVisibleText() throws Exception {
        var json = new com.fasterxml.jackson.databind.ObjectMapper();
        String payload = "1:" + json.writeValueAsString(java.util.Map.of(
            "children", java.util.List.of("Shared official GTA VI headline", "New physical collection available November 19"),
            "href", "/products/new-box", "analytics", java.util.Map.of("text", "Buy")));
        String html = "<main><h1>Shared official GTA VI headline</h1></main><script>self.__next_f.push([1,"
            + json.writeValueAsString(payload) + "]);</script>";
        String input = ExtractionInput.prepare(html, "rockstar_main");
        assertTrue(input.contains("New physical collection available November 19"));
        assertTrue(input.contains("/products/new-box"));
        assertFalse(input.contains("extraction.invalid"));
        assertFalse(input.contains("www.rockstargames.com"));
        assertEquals(input.indexOf("Shared official GTA VI headline"), input.lastIndexOf("Shared official GTA VI headline"));
    }
}
