package com.gtavi.monitoring.core;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * Facade that delegates HTML extraction to the appropriate typed LangChain4j AiService.
 * Each extractor returns a strongly-typed DTO — no manual JSON parsing needed.
 *
 * Before sending HTML to the LLM, we strip noise (scripts, styles, comments)
 * to maximise the signal that fits in the token budget.
 */
@ApplicationScoped
public class AiExtractionService {

    private static final int MAX_CHARS = 24_000;

    @Inject com.gtavi.news.NewsRepository checkpoints;
    @org.eclipse.microprofile.config.inject.ConfigProperty(name="gtavi.monitoring.ai-calls-per-source",defaultValue="4")
    int maxCalls;

    @Inject RockstarMainExtractor rockstarMain;
    @Inject RockstarEditionsExtractor rockstarEditions;
    @Inject RockstarMediaExtractor rockstarMedia;
    @Inject RetailerProductsExtractor retailerProducts;
    @Inject YoutubeRssExtractor youtubeRss;

    private final ObjectMapper mapper = new ObjectMapper();

    /**
     * Extract structured GTA VI data from HTML.
     * Strips noise (scripts, styles) before sending to the LLM for maximum signal density.
     */
    public JsonNode extractFromHtml(String html, String sourceType) {
        if (html == null || html.isBlank()) return null;
        String clean = prepareHtml(html);
        String cacheKey = "typed-extraction:" + com.gtavi.news.OfficialPage.fingerprint("typed-v2|" + sourceType + "|" + clean);
        JsonNode state = checkpoints.read(cacheKey);
        if (state != null && state.path("complete").asBoolean()) return state.path("result");
        var merged = state != null ? (com.fasterxml.jackson.databind.node.ObjectNode)state.path("result").deepCopy() : mapper.createObjectNode();
        int offset = state == null ? 0 : state.path("offset").asInt();
        int calls = 0;
        try {
            while (offset < clean.length() && calls++ < Math.clamp(maxCalls,1,20)) {
                int end = Math.min(clean.length(),offset + MAX_CHARS);
                String chunk = clean.substring(offset,end);
                JsonNode facts = switch (sourceType) {
                    case "rockstar_main" -> mapper.valueToTree(rockstarMain.extract(chunk));
                    case "rockstar_editions" -> mapper.valueToTree(rockstarEditions.extract(chunk));
                    case "rockstar_media" -> mapper.valueToTree(rockstarMedia.extract(chunk));
                    case "retailer" -> mapper.valueToTree(retailerProducts.extract(chunk));
                    case "youtube_rss" -> mapper.valueToTree(youtubeRss.extract(chunk));
                    default -> throw new IllegalArgumentException("Unknown extraction type: " + sourceType);
                };
                ExtractionMerge.into(merged,facts);
                offset = end == clean.length() ? end : end - 500;
                var progress=mapper.createObjectNode().put("offset",offset).put("complete",offset>=clean.length());
                progress.set("result",merged);
                checkpoints.cache(cacheKey,progress);
            }
            return offset>=clean.length() ? merged : null;
        } catch (ExtractionMerge.ConflictingFactsException e) {
            // Re-evaluate the whole observation: an earlier cached chunk may be the incorrect one.
            checkpoints.delete(cacheKey);
            Log.warnf("Conflicting facts for %s; restarting extraction on the next check", sourceType);
            return null;
        } catch (Exception e) {
            Log.warnf(e,"Typed extraction incomplete for %s; preserving previous app records",sourceType);
            return null;
        }
    }

    /**
     * Extract retailer products as a typed DTO (for MonitoringOrchestrator.persistOffers).
     */
    public RetailerProductsData extractRetailerProducts(String html) {
        JsonNode result=extractFromHtml(html,"retailer");
        return result==null ? null : mapper.convertValue(result,RetailerProductsData.class);
    }

    // ── HTML preparation ─────────────────────────────────────────────────

    /**
     * Strip layout noise while retaining structured evidence. Chunking resumes across runs
     * for large retailer pages;
     * no middle segment is discarded.
     */
    private String prepareHtml(String html) {
        StringBuilder prepared=new StringBuilder(stripNoise(html));
        var document=org.jsoup.Jsoup.parse(html);
        for(var script:document.select("script[type=application/ld+json]")) prepared.append("\nStructured evidence:\n").append(script.data());
        return prepared.toString();
    }

    /**
     * Remove elements the LLM doesn't need:
     * &lt;script&gt;...&lt;/script&gt;, &lt;style&gt;...&lt;/style&gt;, &lt;noscript&gt;...&lt;/noscript&gt;,
     * &lt;nav&gt;...&lt;/nav&gt;, &lt;footer&gt;...&lt;/footer&gt;, &lt;header&gt;...&lt;/header&gt;,
     * &lt;svg&gt;...&lt;/svg&gt;, HTML comments &lt;!-- ... --&gt;, and collapse whitespace.
     */
    static String stripNoise(String html) {
        // Remove whole elements (greedy across lines)
        String cleaned = html
            .replaceAll("(?s)<script[^>]*>.*?</script>", "")
            .replaceAll("(?s)<style[^>]*>.*?</style>", "")
            .replaceAll("(?s)<noscript[^>]*>.*?</noscript>", "")
            .replaceAll("(?s)<nav[^>]*>.*?</nav>", "")
            .replaceAll("(?s)<footer[^>]*>.*?</footer>", "")
            .replaceAll("(?s)<header[^>]*>.*?</header>", "")
            .replaceAll("(?s)<svg[^>]*>.*?</svg>", "")
            .replaceAll("(?s)<path[^>]*>.*?</path>", "")
            // HTML comments
            .replaceAll("<!--.*?-->", "")
            // data-* attributes (bloat)
            .replaceAll("\\sdata-[a-zA-Z0-9-]+=(?:\"[^\"]*\"|'[^']*')", "")
            // class attributes (CSS only — zero semantic value for LLM extraction)
            .replaceAll("\\sclass=(?:\"[^\"]*\"|'[^']*')", "")
            // style attributes (inline CSS — also useless for extraction)
            .replaceAll("\\sstyle=(?:\"[^\"]*\"|'[^']*')", "")
            // id attributes (DOM identity — no semantic value)
            .replaceAll("\\sid=(?:\"[^\"]*\"|'[^']*')", "")
            // Collapse whitespace (blank lines / runs of spaces)
            .replaceAll("\\n\\s*\\n", "\n")
            .replaceAll("[ \\t]{2,}", " ");

        return cleaned;
    }

}
