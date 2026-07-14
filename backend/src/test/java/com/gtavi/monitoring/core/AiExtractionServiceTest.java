package com.gtavi.monitoring.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AiExtractionServiceTest {

    @Test
    void stripNoiseRemovesScriptTags() {
        String html = "<html><head><script>var x=1;</script></head><body><p>Hello</p></body></html>";
        String cleaned = AiExtractionService.stripNoise(html);
        assertFalse(cleaned.contains("<script"), "script tag should be removed");
        assertTrue(cleaned.contains("<p>Hello</p>"), "body content should be preserved");
        assertTrue(cleaned.contains("<html>"), "outer tags preserved");
    }

    @Test
    void stripNoiseRemovesStyleTags() {
        String html = "<html><style>.foo { color: red; }</style><body>text</body></html>";
        String cleaned = AiExtractionService.stripNoise(html);
        assertFalse(cleaned.contains("<style"), "style tag should be removed");
        assertTrue(cleaned.contains("text"), "body text preserved");
    }

    @Test
    void stripNoiseRemovesNoscriptTags() {
        String html = "<html><noscript>JS disabled</noscript><body>content</body></html>";
        String cleaned = AiExtractionService.stripNoise(html);
        assertFalse(cleaned.contains("<noscript"), "noscript tag removed");
        assertTrue(cleaned.contains("content"), "content preserved");
    }

    @Test
    void stripNoiseRemovesHtmlComments() {
        String html = "<body><!-- ads --><!-- tracking --><p>real</p></body>";
        String cleaned = AiExtractionService.stripNoise(html);
        assertFalse(cleaned.contains("<!--"), "comments removed");
        assertTrue(cleaned.contains("<p>real</p>"), "real content preserved");
    }

    @Test
    void stripNoiseRemovesNavTags() {
        String html = "<html><nav><ul><li>Home</li></ul></nav><body><p>editions</p></body></html>";
        String cleaned = AiExtractionService.stripNoise(html);
        assertFalse(cleaned.contains("<nav"), "nav tag removed");
        assertTrue(cleaned.contains("editions"), "content preserved");
    }

    @Test
    void stripNoiseRemovesFooterTags() {
        String html = "<body><p>products</p><footer>Copyright 2026</footer></body>";
        String cleaned = AiExtractionService.stripNoise(html);
        assertFalse(cleaned.contains("<footer"), "footer tag removed");
        assertTrue(cleaned.contains("products"), "content preserved");
    }

    @Test
    void stripNoiseRemovesSvg() {
        String html = "<div><svg><path d='M0,0'/></svg><p>text</p></div>";
        String cleaned = AiExtractionService.stripNoise(html);
        assertFalse(cleaned.contains("<svg"), "svg removed");
        assertTrue(cleaned.contains("<p>text</p>"), "text preserved");
    }

    @Test
    void stripNoiseRemovesDataAttributes() {
        String html = "<div data-testid='123' data-qa='btn' class='foo'>click</div>";
        String cleaned = AiExtractionService.stripNoise(html);
        assertFalse(cleaned.contains("data-testid"), "data-testid removed");
        assertFalse(cleaned.contains("data-qa"), "data-qa removed");
        assertTrue(cleaned.contains("class='foo'"), "class preserved");
        assertTrue(cleaned.contains("click"), "text preserved");
    }

    @Test
    void stripNoiseCollapsesWhitespace() {
        String html = "<div>   hello    world   </div>";
        String cleaned = AiExtractionService.stripNoise(html);
        assertTrue(cleaned.contains("hello world"), "whitespace collapsed");
    }

    @Test
    void stripNoiseHandlesMultilineScript() {
        String html = """
            <html>
            <head>
            <script type="text/javascript">
              var analytics = {};
              analytics.track('pageview');
            </script>
            </head>
            <body>
            <h1>GTA VI</h1>
            <p>Pre-order now</p>
            </body>
            </html>""";
        String cleaned = AiExtractionService.stripNoise(html);
        assertFalse(cleaned.contains("analytics"), "script content removed");
        assertTrue(cleaned.contains("<h1>GTA VI</h1>"), "heading preserved");
        assertTrue(cleaned.contains("Pre-order now"), "product text preserved");
    }

    @Test
    void retailerPagePatternSurvives() {
        // Simulate a real retailer search result pattern
        String html = """
            <html><head><script src="bundle.js"></script><style>.product{}</style></head>
            <body>
            <header>Menu</header>
            <main>
              <div class="product" data-sku="123">
                <h3>Grand Theft Auto VI Standard Edition</h3>
                <span class="price">79.90 CHF</span>
                <a href="/p/123">Buy</a>
              </div>
              <div class="product" data-sku="456">
                <h3>Grand Theft Auto VI Ultimate Edition</h3>
                <span class="price">99.90 CHF</span>
                <a href="/p/456">Buy</a>
              </div>
            </main>
            <footer><!-- tracker --></footer>
            </body></html>""";
        String cleaned = AiExtractionService.stripNoise(html);
        assertTrue(cleaned.contains("Standard Edition"), "standard edition preserved");
        assertTrue(cleaned.contains("Ultimate Edition"), "ultimate edition preserved");
        assertTrue(cleaned.contains("79.90"), "price preserved");
        assertTrue(cleaned.contains("99.90"), "price preserved");
        assertFalse(cleaned.contains("<script"), "scripts stripped");
        assertFalse(cleaned.contains("<style"), "styles stripped");
        assertFalse(cleaned.contains("data-sku"), "data attrs stripped");
    }
}
