package com.gtavi.monitoring.core;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.gtavi.news.NewsRepository;
import com.gtavi.news.OfficialPage;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/** Stateless AI calls with durable, bounded progress across scheduled checks. */
@ApplicationScoped
public class AiExtractionService {
    private static final int MAX_CHARS = 24_000;
    // Invalidate outputs made with conversational memory or the previous input preparation.
    private static final String VERSION = "typed-v4";

    @Inject NewsRepository checkpoints;
    @ConfigProperty(name="gtavi.monitoring.ai-calls-per-source", defaultValue="4") int maxCalls;
    @Inject RockstarMainExtractor rockstarMain;
    @Inject RockstarEditionsExtractor rockstarEditions;
    @Inject RockstarMediaExtractor rockstarMedia;
    @Inject RetailerProductsExtractor retailerProducts;
    @Inject YoutubeRssExtractor youtubeRss;

    private final ObjectMapper mapper = new ObjectMapper();

    public ExtractionResult extractFromHtml(String html, String sourceType) {
        if (html == null || html.isBlank()) return ExtractionResult.failed("Empty source response", 0, 0);
        String cacheKey = null;
        int offset = 0;
        int total = 0;
        int calls = 0;
        try {
            String clean = ExtractionInput.prepare(html, sourceType);
            total = clean.length();
            if (clean.isBlank()) return ExtractionResult.failed("No readable or structured source evidence", 0, 0);
            cacheKey = "typed-extraction:" + OfficialPage.fingerprint(VERSION + "|" + sourceType + "|" + clean);
            JsonNode state = checkpoints.read(cacheKey);
            if (state != null && state.path("complete").asBoolean()) {
                return ExtractionResult.complete(state.path("result").deepCopy(), total);
            }
            ObjectNode merged = state == null ? mapper.createObjectNode() : (ObjectNode) state.path("result").deepCopy();
            offset = state == null ? 0 : state.path("offset").asInt();
            while (offset < total && calls < Math.clamp(maxCalls, 1, 20)) {
                int end = Math.min(total, offset + MAX_CHARS);
                calls++;
                ExtractionMerge.into(merged, extractChunk(clean.substring(offset, end), sourceType));
                offset = end == total ? end : end - 500;
                ObjectNode progress = mapper.createObjectNode().put("offset", offset).put("complete", offset >= total);
                progress.set("result", merged);
                checkpoints.cache(cacheKey, progress);
            }
            Log.infof("Extraction %s: %s, %d calls, %d/%d prepared characters",
                sourceType, offset >= total ? "COMPLETE" : "PENDING", calls, offset, total);
            return offset >= total ? ExtractionResult.complete(merged, total)
                : ExtractionResult.pending("Call budget reached; saved progress will resume automatically", offset, total);
        } catch (ExtractionMerge.ConflictingFactsException e) {
            if (cacheKey != null) checkpoints.delete(cacheKey);
            Log.warnf("Conflicting facts for %s; restarting extraction on the next check", sourceType);
            return ExtractionResult.failed("Conflicting source facts; extraction will restart", offset, total);
        } catch (Exception e) {
            Log.warnf(e, "Extraction failed for %s after %d calls at %d/%d characters; retaining prior data",
                sourceType, calls, offset, total);
            return ExtractionResult.failed("Extraction or checkpoint failed: " + e.getClass().getSimpleName(), offset, total);
        }
    }

    /** A structurally valid AI response can still contain no usable source facts. */
    public void discardCompletedResult(String html, String sourceType) {
        String clean = ExtractionInput.prepare(html, sourceType);
        String key = "typed-extraction:" + OfficialPage.fingerprint(VERSION + "|" + sourceType + "|" + clean);
        JsonNode state = checkpoints.read(key);
        if (state != null && state.path("complete").asBoolean()) checkpoints.delete(key);
    }

    private JsonNode extractChunk(String chunk, String sourceType) {
        return switch (sourceType) {
            case "rockstar_main" -> mapper.valueToTree(rockstarMain.extract(chunk));
            case "rockstar_editions" -> mapper.valueToTree(rockstarEditions.extract(chunk));
            case "rockstar_media" -> mapper.valueToTree(rockstarMedia.extract(chunk));
            case "retailer" -> mapper.valueToTree(retailerProducts.extract(chunk));
            case "youtube_rss" -> mapper.valueToTree(youtubeRss.extract(chunk));
            default -> throw new IllegalArgumentException("Unknown extraction type: " + sourceType);
        };
    }

    static String stripNoise(String html) {
        return ExtractionInput.cleanedHtml(html);
    }
}
