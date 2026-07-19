package com.gtavi.monitoring.core;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;

import java.net.URI;
import java.util.Locale;
import java.util.Set;

/**
 * Applies deterministic validation after AI extraction and before snapshots,
 * diffs or offers are persisted. The LLM is a candidate extractor, not a
 * trusted product classifier.
 */
@ApplicationScoped
public class RetailerProductValidator {

    private static final Set<String> EXCLUDED_PRODUCT_TERMS = Set.of(
        "slowed", "explicit", "soundtrack", "song", "music", "album",
        "mp3", "vinyl", "ebook", "novel", "guide", "poster", "shirt",
        "t-shirt", "hoodie", "wallpaper", "skin", "sticker", "keychain"
    );
    private static final Set<String> EDITIONS = Set.of(
        "STANDARD", "ULTIMATE", "COLLECTOR", "DELUXE", "UNKNOWN"
    );
    private static final Set<String> PLATFORMS = Set.of("PS5", "XSX", "PC", "UNKNOWN");
    private static final Set<String> AVAILABILITY = Set.of(
        "IN_STOCK", "OUT_OF_STOCK", "PREORDER", "COMING_SOON", "UNAVAILABLE", "UNKNOWN"
    );
    private static final Set<String> CURRENCIES = Set.of("CHF", "EUR", "USD");

    private final ObjectMapper mapper = new ObjectMapper();

    public JsonNode validate(String sourceCode, String sourceUrl, JsonNode data) {
        if (data == null || !data.isObject()) return data;

        ObjectNode validated = ((ObjectNode) data).deepCopy();
        JsonNode products = data.get("products");
        if (products == null || !products.isArray()) return validated;

        ArrayNode accepted = mapper.createArrayNode();
        for (JsonNode candidate : products) {
            if (!candidate.isObject()) continue;

            String name = text(candidate, "name");
            if (!isGtaViGameProduct(name)) {
                Log.warnf("Rejected non-game retailer candidate from %s: %s", sourceCode, name);
                continue;
            }

            String url = normalizeUrl(sourceUrl, text(candidate, "url"));
            if (url == null) {
                Log.warnf("Rejected retailer candidate with invalid URL from %s: %s", sourceCode, name);
                continue;
            }

            ObjectNode product = ((ObjectNode) candidate).deepCopy();
            String edition = normalizeEnum(text(candidate, "edition"), EDITIONS, inferEdition(name));
            String platform = normalizeEnum(text(candidate, "platform"), PLATFORMS, "UNKNOWN");
            String availability = normalizeEnum(text(candidate, "availability"), AVAILABILITY, "UNKNOWN");
            String currency = normalizeEnum(text(candidate, "currency"), CURRENCIES, null);

            product.put("name", name.trim());
            product.put("edition", edition);
            product.put("platform", platform);
            product.put("availability", availability);
            product.put("url", url);
            product.put("canonicalKey", canonicalKey(product, url));

            if (currency != null) product.put("currency", currency);
            else product.putNull("currency");

            if (!candidate.has("price") || !candidate.get("price").isNumber()
                || candidate.get("price").asDouble() <= 0) {
                product.putNull("price");
            }

            accepted.add(product);
        }

        validated.set("products", accepted);
        return validated;
    }

    static boolean isGtaViGameProduct(String name) {
        if (name == null || name.isBlank()) return false;
        String lower = name.toLowerCase(Locale.ROOT);
        String compact = lower.replaceAll("[^a-z0-9]", "");

        boolean mentionsGame = lower.contains("grand theft auto vi")
            || lower.contains("grand theft auto 6")
            || lower.contains("gta vi")
            || lower.contains("gta 6")
            || compact.contains("gtavi")
            || compact.contains("gta6");
        if (!mentionsGame) return false;

        return EXCLUDED_PRODUCT_TERMS.stream().noneMatch(lower::contains);
    }

    static String normalizeUrl(String sourceUrl, String candidateUrl) {
        if (candidateUrl == null || candidateUrl.isBlank()) return null;
        try {
            URI base = URI.create(sourceUrl);
            URI resolved = base.resolve(candidateUrl.trim());
            String scheme = resolved.getScheme();
            if (scheme == null || resolved.getHost() == null
                || !(scheme.equalsIgnoreCase("https") || scheme.equalsIgnoreCase("http"))) {
                return null;
            }
            return resolved.toString();
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static String canonicalKey(JsonNode product, String url) {
        String edition = text(product, "edition");
        String platform = text(product, "platform");
        try {
            URI uri = URI.create(url);
            String productPath = (uri.getHost() + uri.getPath()).toLowerCase(Locale.ROOT)
                .replaceAll("/+$", "");
            return edition + "|" + platform + "|" + productPath;
        } catch (IllegalArgumentException e) {
            return edition + "|" + platform + "|" + normalizeTitle(text(product, "name"));
        }
    }

    private static String inferEdition(String name) {
        String lower = name.toLowerCase(Locale.ROOT);
        if (lower.contains("collector")) return "COLLECTOR";
        if (lower.contains("ultimate")) return "ULTIMATE";
        if (lower.contains("deluxe")) return "DELUXE";
        return "STANDARD";
    }

    private static String normalizeEnum(String value, Set<String> allowed, String fallback) {
        if (value == null) return fallback;
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        return allowed.contains(normalized) ? normalized : fallback;
    }

    private static String normalizeTitle(String value) {
        return value == null ? "unknown" : value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node != null ? node.get(field) : null;
        return value == null || value.isNull() ? null : value.asText(null);
    }
}
