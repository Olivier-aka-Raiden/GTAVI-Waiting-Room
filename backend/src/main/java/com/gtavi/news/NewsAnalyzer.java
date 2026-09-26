package com.gtavi.news;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.*;

/** AI proposes facts; provenance validation and deterministic metadata keep official news discoverable. */
@ApplicationScoped
public class NewsAnalyzer {
    public static final String VERSION = "news-v3";
    @Inject AnnouncementExtractor ai;
    @Inject ObjectMapper json;
    @Inject StructuredProducts structured;
    public ObjectNode analyze(OfficialPage page) {
        return analyze(page, new ExtractionBudget(Integer.MAX_VALUE, Integer.MAX_VALUE), null);
    }
    public ObjectNode analyze(OfficialPage page, ExtractionBudget budget, ObjectNode prior) {
        String context = page.title() + " " + page.description();
        boolean relevant = related(context) || (prior != null && prior.path("relevant").asBoolean());
        String category = category(context);
        ObjectNode result = prior == null ? json.createObjectNode() : prior.deepCopy();
        result.put("id", OfficialPage.identity(page.url()));
        result.put("sourceUrl", OfficialPage.canonical(page.url()));
        result.put("title", page.title().replaceFirst(" - Rockstar Games$", ""));
        result.put("description", page.description());
        result.put("imageUrl", page.imageUrl());
        result.put("publishedAt", page.publishedAt());
        if (prior == null) result.put("category", category);
        if (prior == null) result.put("importance", Set.of("COLLECTIBLE","MUSIC").contains(category) ? "MAJOR" : "NEWS");
        result.put("complete", page.complete());
        result.put("extractorVersion", VERSION);
        var products = result.withArray("products");
        var links = result.putArray("links");
        page.links().forEach(links::add);
        var relationships = result.putArray("relationshipLinks");
        if (page.url().contains("/newswire/article/") && page.complete()) page.links().forEach(relationships::add);
        result.put("preorderAnnounced",page.content().toLowerCase(Locale.ROOT).matches("(?s).*(pre-order|preorder|pre-ordering).*"));
        boolean retry = !page.complete();
        int chunks = prior == null ? 0 : prior.path("nextChunk").asInt();
        var pageChunks = page.chunks(12000);
        // Metadata-only pages can still be published; fail open for news, never for invented commerce fields.
        for (int index = chunks; index < pageChunks.size(); index++) {
            String chunk = pageChunks.get(index);
            if (!budget.reserve(chunk.length() + page.url().length() + page.title().length())) { retry = true; break; }
            try {
                var extraction = ai.extract(page.url(), page.title(), chunk);
                if (extraction == null) { retry = true; break; }
                chunks = index + 1;
                if (Boolean.TRUE.equals(extraction.relevant()) && supported(chunk, extraction.evidence())) {
                    relevant = true;
                    if (Set.of("COLLECTIBLE","MUSIC","GAME","MEDIA","NEWS").contains(
                            Objects.toString(extraction.category(),""))
                            && (!"NEWS".equals(extraction.category()) || "NEWS".equals(result.path("category").asText())))
                        result.put("category", extraction.category());
                    if ("MAJOR".equals(extraction.importance())) result.put("importance","MAJOR");
                }
                if (relevant && extraction.facts()!=null) {
                    var facts=result.withArray("facts");
                    for(var fact:extraction.facts()) {
                        if(fact.subject()==null || fact.value()==null || !supported(chunk,fact.evidence())
                                || !fact.evidence().contains(fact.value())) continue;
                        var item=json.createObjectNode().put("subject",fact.subject()).put("value",fact.value())
                            .put("evidence",fact.evidence()).put("importance",fact.importance());
                        item.put("id",OfficialPage.fingerprint((fact.subject()+"|"+fact.value()).toLowerCase(Locale.ROOT).replaceAll("\\s+"," ").strip()));
                        facts.add(item);
                    }
                }
                if (extraction.products() == null || !relevant) continue;
                for (var p : extraction.products()) {
                    if (p.name() == null || !chunk.contains(p.name()) || !supported(chunk,p.evidence())) continue;
                    if (p.name().matches("(?is).*(?:GTA|Grand Theft Auto)\\s*V\\b.*")) continue;
                    var product = json.valueToTree(p);
                    var item = (ObjectNode)product;
                    String buy = verifiedUrl(page, chunk, p.purchaseUrl());
                    item.put("purchaseUrl", buy);
                    String image = verifiedUrl(page, chunk, p.imageUrl());
                    item.put("imageUrl", image == null ? page.imageUrl() : image);
                    if (p.price() == null || p.price() <= 0 || p.currency() == null
                        || !p.currency().matches("[A-Z]{3}") || !chunk.contains(p.currency())
                        || !chunk.contains(java.math.BigDecimal.valueOf(p.price()).stripTrailingZeros().toPlainString())) {
                        item.putNull("price"); item.putNull("currency");
                    }
                    if (!Set.of("COLLECTIBLE","VINYL","CD","ALBUM","MERCHANDISE","GAME").contains(
                            Objects.toString(p.category(),""))) item.put("category","MERCHANDISE");
                    if (!Set.of("PREORDER","AVAILABLE","OUT_OF_STOCK","ANNOUNCED").contains(
                            Objects.toString(p.availability(),""))) item.put("availability","ANNOUNCED");
                    if (p.description()==null || !chunk.contains(p.description())) item.put("description",page.description());
                    if (p.limited()!=null && !p.evidence().toLowerCase(Locale.ROOT).contains("limited")) item.putNull("limited");
                    if (p.gameIncluded()!=null && !p.evidence().toLowerCase(Locale.ROOT).matches("(?s).*(game included|game sold separately|includes the game).*")) item.putNull("gameIncluded");
                    item.put("sourceUrl", page.url());
                    item.put("id", buy == null ? OfficialPage.fingerprint(OfficialPage.canonical(page.url()) + "|" + slug(p.name())) : OfficialPage.identity(buy));
                    products.add(item);
                }
            } catch (Exception e) { retry = true; break; }
        }
        // Deterministic facts remain available even when the run exhausts its AI budget.
        var verifiedProducts = json.createArrayNode();
        for (String line : page.structured().split("\n")) {
            if (line.isBlank()) continue;
            try {
                var data = json.readTree(line);
                structured.extract(data, page, verifiedProducts);
                for (var link : data.path("purchaseLinks")) relationships.add(link.path("url").asText());
            }
            catch (Exception e) { retry = true; }
        }
        var unique = new LinkedHashMap<String,JsonNode>();
        var verifiedUrls = new HashSet<String>();
        verifiedProducts.forEach(p -> verifiedUrls.add(p.path("purchaseUrl").asText()));
        products.forEach(p -> {
            if (!verifiedUrls.contains(p.path("purchaseUrl").asText())) unique.put(p.path("id").asText(), p);
        });
        verifiedProducts.forEach(p -> unique.put(p.path("id").asText(), p));
        products.removeAll();
        unique.values().forEach(products::add);
        result.put("relevant", relevant || !products.isEmpty());
        result.put("enrichmentPending", retry);
        result.put("chunksProcessed", chunks);
        result.put("nextChunk", chunks);
        result.put("totalChunks", pageChunks.size());
        result.put("evidence", page.description());
        return result;
    }
    private static String verifiedUrl(OfficialPage page, String chunk, String value) {
        if (value == null || value.isBlank() || !(chunk.contains(value) || page.links().contains(value))) return null;
        return OfficialPage.resolve(page.url(),value);
    }
    private static boolean supported(String chunk, String evidence) {
        return evidence != null && evidence.length() >= 12 && chunk.contains(evidence);
    }
    public static boolean related(String text) {
        return text != null && text.toLowerCase(Locale.ROOT).matches("(?s).*(grand theft auto (vi|6)\\b|gta\\s*(vi|6)\\b).*");
    }
    public static String category(String text) {
        String s=text.toLowerCase(Locale.ROOT);
        if (s.contains("collector") || s.contains("collectible")) return "COLLECTIBLE";
        if (s.contains("album") || s.contains("soundtrack") || s.contains("vinyl")) return "MUSIC";
        return "NEWS";
    }
    private static String slug(String name) { return name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+","-"); }
}
