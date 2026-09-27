package com.gtavi.news;

import com.fasterxml.jackson.databind.JsonNode;
import java.net.URI;
import java.util.*;

/** Reconcile short format labels only when evidence connects them to the known album. */
final class AlbumIdentity {
    /** Formats are recognized by their own words: "CD jewel case" or "liquid-filled vinyl" included. */
    private static final Set<String> FORMAT_WORDS = Set.of(
        "cd", "vinyl", "record", "lp", "2lp", "compact", "disc", "jewel", "case", "cassette");
    private static final Set<String> FORMAT_QUALIFIERS = Set.of(
        "new", "limited", "edition", "standard", "deluxe", "exclusive", "collectors", "collector",
        "colored", "coloured", "color", "colour", "black", "double", "liquid", "filled", "splatter",
        "splattered", "special", "gatefold", "the", "a", "of", "with", "and");
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
        return formatLabel(normalized(item)) && (sources.contains(item.path("sourceUrl").asText(""))
            || articles.contains(item.path("articleId").asText("")) || musicPage(item.path("sourceUrl").asText("")));
    }
    /** The release record itself, as opposed to one of its format entries. */
    static boolean isAlbumTitle(JsonNode item) {
        return music(item) && normalized(item).matches("(?:grand theft auto vi|gta vi)? ?the album");
    }
    private static boolean formatLabel(String name) {
        if (name.isBlank()) return false;
        boolean formatWord = false;
        for (String word : name.split(" ")) {
            if (FORMAT_WORDS.contains(word)) {
                formatWord = true;
                continue;
            }
            if (!FORMAT_QUALIFIERS.contains(word)) return false;
        }
        return formatWord;
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
        try {
            String host = Objects.toString(URI.create(url).getHost(), "").toLowerCase(Locale.ROOT);
            return Set.of("gtavithealbum.lnk.to", "gtavi-thealbum.lnk.to").contains(host)
                || host.equals("gtavi-thealbum.com") || host.endsWith(".gtavi-thealbum.com");
        } catch (IllegalArgumentException invalid) { return false; }
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
