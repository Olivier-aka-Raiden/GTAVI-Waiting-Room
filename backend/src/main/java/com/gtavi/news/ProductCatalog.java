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
        var album = new AlbumIdentity(observations);
        var linkedNames = new HashMap<String, Set<String>>();
        for (JsonNode item : observations) if (!item.path("purchaseUrl").asText("").isBlank())
            linkedNames.computeIfAbsent(name(item), ignored -> new LinkedHashSet<>()).add(identity(item, album.matches(item)));
        var groups = new LinkedHashMap<String, ObjectNode>();
        var choices = new HashMap<String, LinkedHashMap<String, ObjectNode>>();
        var albumDescriptions = new HashMap<String, String>();
        // Oldest first means fresh verified details win, while missing details preserve old values.
        var ordered = new ArrayList<>(observations);
        ordered.sort(Comparator.comparing(item -> item.path("updatedAt").asText("")));
        for (JsonNode item : ordered) {
            String key = identity(item, album.matches(item));
            if (!album.matches(item) && item.path("purchaseUrl").asText("").isBlank()) {
                var matches = linkedNames.get(name(item));
                if (matches != null && matches.size() == 1) key = matches.iterator().next();
            }
            ObjectNode merged = groups.computeIfAbsent(key, ignored -> ((ObjectNode)item).deepCopy());
            item.fields().forEachRemaining(entry -> {
                if (!entry.getValue().isNull() && !entry.getValue().asText("").isBlank())
                    merged.set(entry.getKey(), entry.getValue());
            });
            merged.put("id", OfficialPage.fingerprint(key));
            if (album.matches(item)) {
                merged.put("name", "Grand Theft Auto VI: The Album").put("category", "ALBUM");
                merged.remove("limited"); // Limited applies to a format, not every copy of the album.
                // The release text describes the album; a format entry must not replace it.
                if (AlbumIdentity.isAlbumTitle(item) && !item.path("description").asText("").isBlank())
                    albumDescriptions.put(key, item.path("description").asText());
            }
            var offers = choices.computeIfAbsent(key, ignored -> new LinkedHashMap<>());
            if (item.path("offers").isArray() && !item.path("offers").isEmpty())
                item.path("offers").forEach(offer -> add(offers, offer, item));
            else add(offers, item, item);
            var array = merged.putArray("offers");
            visibleOffers(offers).forEach(array::add);
        }
        albumDescriptions.forEach((key, description) -> {
            ObjectNode card = groups.get(key);
            if (card != null) card.put("description", description);
        });
        var result = new ArrayList<>(groups.values());
        result.sort(Comparator.comparing((ObjectNode item) -> item.path("updatedAt").asText("")).reversed());
        return reconcile(result);
    }

    /**
     * One card per announced item. A verified price makes an unpriced duplicate of the same
     * item redundant, and a collection box described by a purchase link owns the page's
     * descriptive entries: the contents of a box are never sold separately.
     */
    private static List<ObjectNode> reconcile(List<ObjectNode> cards) {
        var priced = new HashMap<String, ObjectNode>();
        for (ObjectNode card : cards) {
            if (!hasPrice(card)) continue;
            priced.merge(articleOf(card) + "|" + name(card), card, (current, candidate) -> current);
        }
        var collectionPages = new HashSet<String>();
        for (ObjectNode card : cards) if (isLinkedCollection(card)) collectionPages.add(articleOf(card));
        var kept = new ArrayList<ObjectNode>();
        for (ObjectNode card : cards) {
            ObjectNode verified = priced.get(articleOf(card) + "|" + name(card));
            if (verified != null && verified != card && !hasPrice(card)) {
                foldInto(verified, card);
                continue;
            }
            if (collectionPages.contains(articleOf(card)) && card.path("offers").isEmpty() && !hasPrice(card))
                continue;
            kept.add(card);
        }
        return kept;
    }

    /** Keep the richest description and a missing image when an unpriced duplicate is dropped. */
    private static void foldInto(ObjectNode target, ObjectNode dropped) {
        if (dropped.path("description").asText("").length() > target.path("description").asText("").length())
            target.put("description", dropped.path("description").asText());
        if (!target.hasNonNull("imageUrl") && dropped.hasNonNull("imageUrl"))
            target.set("imageUrl", dropped.get("imageUrl"));
    }

    private static boolean isLinkedCollection(JsonNode card) {
        if (card.path("offers").isEmpty()) return false;
        String lower = card.path("name").asText("").toLowerCase(Locale.ROOT);
        return "COLLECTIBLE".equals(card.path("category").asText())
            || lower.contains("collection") || lower.contains("collector") || lower.contains("box");
    }

    private static boolean hasPrice(JsonNode card) {
        return positive(card.path("price")) || card.path("offers").findValues("price").stream().anyMatch(ProductCatalog::positive);
    }

    private static boolean positive(JsonNode value) {
        return value.isNumber() && value.asDouble() > 0;
    }

    private static String articleOf(JsonNode card) {
        return card.path("articleId").asText("");
    }

    /** An unpriced observation of one listing never renders beside its priced offer. */
    private static List<ObjectNode> visibleOffers(Map<String, ObjectNode> offers) {
        var pricedListings = new HashSet<String>();
        offers.values().stream().filter(ProductCatalog::hasPrice).forEach(offer -> pricedListings.add(listingOf(offer)));
        return offers.values().stream()
            .filter(offer -> hasPrice(offer) || !pricedListings.contains(listingOf(offer)))
            .toList();
    }

    private static String listingOf(JsonNode offer) {
        return com.gtavi.monitoring.core.OfferIdentity.url(offer.path("purchaseUrl").asText(""))
            + "|" + offer.path("variant").asText("") + "|" + offer.path("market").asText("");
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
    private static String identity(JsonNode item, boolean album) {
        if (album) return "gta-vi:the-album";
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
