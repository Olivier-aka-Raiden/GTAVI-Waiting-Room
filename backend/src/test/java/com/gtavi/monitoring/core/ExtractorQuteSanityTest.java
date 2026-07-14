package com.gtavi.monitoring.core;

import io.quarkus.qute.TemplateException;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.fail;

/**
 * Validates that @SystemMessage annotations on all extractors don't contain
 * unescaped Qute template expressions.
 * <p>
 * Qute processes @SystemMessage strings as templates. Unescaped curly braces
 * like {field1, field2} are interpreted as expressions and fail at runtime.
 * This test catches that by actually invoking each extractor — the template
 * is rendered before the LLM call, so a TemplateException means the bug is back.
 */
@QuarkusTest
class ExtractorQuteSanityTest {

    @Inject RockstarMainExtractor rockstarMain;
    @Inject RockstarEditionsExtractor rockstarEditions;
    @Inject RockstarMediaExtractor rockstarMedia;
    @Inject YoutubeRssExtractor youtubeRss;
    @Inject RetailerProductsExtractor retailerProducts;

    @Test
    void rockstarMainSystemMessageRendersWithoutQuteError() {
        assertNoQuteError(() -> rockstarMain.extract("<html></html>"));
    }

    @Test
    void rockstarEditionsSystemMessageRendersWithoutQuteError() {
        assertNoQuteError(() -> rockstarEditions.extract("<html></html>"));
    }

    @Test
    void rockstarMediaSystemMessageRendersWithoutQuteError() {
        assertNoQuteError(() -> rockstarMedia.extract("<html></html>"));
    }

    @Test
    void youtubeRssSystemMessageRendersWithoutQuteError() {
        assertNoQuteError(() -> youtubeRss.extract("<rss></rss>"));
    }

    @Test
    void retailerProductsSystemMessageRendersWithoutQuteError() {
        assertNoQuteError(() -> retailerProducts.extract("<html></html>"));
    }

    /**
     * Invoke the extractor. If Qute finds an unescaped template expression,
     * it throws TemplateException BEFORE the LLM call — we catch that and fail.
     * Any other exception (LLM backend not configured in test) is fine.
     */
    private void assertNoQuteError(Runnable call) {
        try {
            call.run();
        } catch (Exception e) {
            if (isQuteError(e)) {
                fail("Qute TemplateException in @SystemMessage — unescaped { braces? " + e.getMessage());
            }
            // Expected: LLM backend not available in tests → OK
        }
    }

    private boolean isQuteError(Throwable t) {
        while (t != null) {
            if (t instanceof TemplateException) return true;
            t = t.getCause();
        }
        return false;
    }
}
