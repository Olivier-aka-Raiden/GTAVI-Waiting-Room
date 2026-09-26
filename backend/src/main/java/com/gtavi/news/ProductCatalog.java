package com.gtavi.news;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.*;

/** Public product families with format/market choices; raw observations remain available for replay. */
@ApplicationScoped
public class ProductCatalog {
    @Inject NewsRepository repository;

    public Map<String, Object> page(int page, int size) {
        var observations = new ArrayList<JsonNode>();
        for (int index = 0; ; index++) {
            var batch = repository.list("products", index, 100);
            observations.addAll(batch);
            if (batch.size() < 100) break;
        }
        var products = group(observations);
        int count = Math.clamp(size, 1, 100);
        long start = (long)Math.max(0, page) * count;
        return Map.of("items", products.subList((int)Math.min(start, products.size()),
            (int)Math.min(start + count, products.size())), "total", products.size());
    }

    static List<ObjectNode> group(List<JsonNode> observations) {
        var linkedNames = new HashMap<String, Set<String>>();
        for (JsonNode item : observations) if (!item.path("purchaseUrl").asText("").isBlank())
            linkedNames.computeIfAbsent(name(item), ignored -> new LinkedHashSet<>()).add(identity(item));
        var groups = new LinkedHashMap<String, ObjectNode>();
        var choices = new HashMap<String, LinkedHashMap<String, ObjectNode>>();
        // Oldest first means fresh verified details win, while missing details preserve old values.
        var ordered = new ArrayList<>(observations);
        ordered.sort(Comparator.comparing(item -> item.path("updatedAt").asText("")));
        for (JsonNode item : ordered) {
            String key = identity(item);
            if (!album(item) && item.path("purchaseUrl").asText("").isBlank()) {
                var matches = linkedNames.get(name(item));
                if (matches != null && matches.size() == 1) key = matches.iterator().next();
            }
            ObjectNode merged = groups.computeIfAbsent(key, ignored -> ((ObjectNode)item).deepCopy());
            item.fields().forEachRemaining(entry -> {
                if (!entry.getValue().isNull() && !entry.getValue().asText("").isBlank())
                    merged.set(entry.getKey(), entry.getValue());
            });
            merged.put("id", OfficialPage.fingerprint(key));
            if (album(item)) {
                merged.put("name", "Grand Theft Auto VI: The Album").put("category", "ALBUM");
                merged.remove("limited"); // Limited applies to a format, not every copy of the album.
            }
            var offers = choices.computeIfAbsent(key, ignored -> new LinkedHashMap<>());
            if (item.path("offers").isArray() && !item.path("offers").isEmpty())
                item.path("offers").forEach(offer -> add(offers, offer, item));
            else add(offers, item, item);
            var array = merged.putArray("offers");
            offers.values().forEach(array::add);
        }
        var result = new ArrayList<>(groups.values());
        result.sort(Comparator.comparing((ObjectNode item) -> item.path("updatedAt").asText("")).reversed());
        return result;
    }
    private static void add(Map<String, ObjectNode> offers, JsonNode source, JsonNode product) {
        String url = source.path("purchaseUrl").asText(product.path("purchaseUrl").asText(""));
        if (OfficialPage.resolve(url, url) == null) return;
        String canonical = com.gtavi.monitoring.core.OfferIdentity.url(url);
        String variant = source.path("variant").asText("");
        String currency = source.path("currency").asText("");
        String market = source.path("market").asText("");
        String key = canonical + "|" + variant + "|" + currency + "|" + market;
        ObjectNode offer = offers.computeIfAbsent(key, ignored -> ((ObjectNode)product).objectNode());
        for (String field : List.of("price", "currency", "market", "availability", "verifiedAt"))
            if (source.hasNonNull(field) && !source.path(field).asText("").isBlank()) offer.set(field, source.get(field));
        offer.put("id", OfficialPage.fingerprint(key)).put("purchaseUrl", canonical);
        offer.put("variant", variant.isBlank() ? product.path("name").asText("") : variant);
        if (product.path("limited").asBoolean()) offer.put("limited", true);
    }
    private static String name(JsonNode item) {
        return item.path("name").asText("").toLowerCase(Locale.ROOT)
            .replaceAll("[^\\p{L}\\p{N}]+", " ").strip();
    }
    private static boolean album(JsonNode item) {
        String url = item.path("purchaseUrl").asText("");
        return Set.of("MUSIC", "ALBUM", "VINYL", "CD").contains(item.path("category").asText())
            && (name(item).matches("(?:grand theft auto vi|gta vi) the album(?: .*)?")
                || url.startsWith("https://gtavithealbum.lnk.to/"));
    }
    private static String identity(JsonNode item) {
        if (album(item)) return "gta-vi:the-album";
        String url = item.path("purchaseUrl").asText("");
        // Distinct structured SKUs retain their original identity even at a shared purchase URL.
        if (!url.isBlank()) {
            var variants = new TreeSet<String>();
            item.path("offers").forEach(offer -> { if (!offer.path("variant").asText("").isBlank()) variants.add(offer.path("variant").asText()); });
            return "product:" + com.gtavi.monitoring.core.OfferIdentity.url(url) + ":"
                + item.path("variant").asText(String.join("|", variants));
        }
        return "unlinked:" + name(item);
    }
}
