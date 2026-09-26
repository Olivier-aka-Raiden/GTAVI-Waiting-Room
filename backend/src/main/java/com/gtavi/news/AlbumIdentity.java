package com.gtavi.news;

import com.fasterxml.jackson.databind.JsonNode;
import java.net.URI;
import java.util.*;

/** Reconcile short format labels only when evidence connects them to the known album. */
final class AlbumIdentity {
    private final Set<String> sources = new HashSet<>();
    private final Set<String> articles = new HashSet<>();

    AlbumIdentity(List<JsonNode> observations) {
        for (JsonNode item : observations) if (explicit(item)) {
            add(sources, item.path("sourceUrl").asText(""));
            add(articles, item.path("articleId").asText(""));
        }
    }
    boolean matches(JsonNode item) {
        if (!music(item)) return false;
        if (explicit(item)) return true;
        String name = normalized(item);
        boolean format = name.matches("(?:(?:new|limited|edition|standard|deluxe|exclusive|collectors|collector|colored|coloured|black|color|colour|double|2lp|lp) )*(?:cd|vinyl|compact disc|vinyl record)(?: edition)?");
        return format && (sources.contains(item.path("sourceUrl").asText(""))
            || articles.contains(item.path("articleId").asText("")) || musicPage(item.path("sourceUrl").asText("")));
    }
    private static boolean explicit(JsonNode item) {
        if (!music(item)) return false;
        if (normalized(item).matches("(?:(?:grand theft auto vi|gta vi) )?the album(?: .*)?")) return true;
        if (albumUrl(item.path("purchaseUrl").asText(""))) return true;
        for (JsonNode offer : item.path("offers")) if (albumUrl(offer.path("purchaseUrl").asText(""))) return true;
        return false;
    }
    private static boolean music(JsonNode item) {
        return Set.of("MUSIC", "ALBUM", "VINYL", "CD").contains(item.path("category").asText());
    }
    private static String normalized(JsonNode item) {
        return item.path("name").asText("").toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}]+", " ").strip();
    }
    private static boolean albumUrl(String url) {
        try { return Set.of("gtavithealbum.lnk.to", "gtavi-thealbum.lnk.to").contains(Objects.toString(URI.create(url).getHost(), "").toLowerCase(Locale.ROOT)); }
        catch (IllegalArgumentException invalid) { return false; }
    }
    private static boolean musicPage(String url) {
        try {
            URI uri = URI.create(url);
            return Set.of("www.rockstargames.com", "rockstargames.com").contains(Objects.toString(uri.getHost(), "").toLowerCase(Locale.ROOT))
                && uri.getPath().matches("(?i)/vi/music/?");
        } catch (IllegalArgumentException invalid) { return false; }
    }
    private static void add(Set<String> values, String value) { if (!value.isBlank()) values.add(value); }
}
