package com.gtavi.news;

import java.net.URI;
import java.util.Locale;

/** Page discovery is narrower than the set of URLs retained as source/image evidence. */
final class OfficialUrlPolicy {
    private OfficialUrlPolicy() {}

    static boolean crawlable(String url) {
        if (!OfficialPage.allowed(url)) return false;
        String path = URI.create(url).getPath().toLowerCase(Locale.ROOT);
        String directory = path + "/";
        if (directory.contains("/_next/") || directory.contains("/_nuxt/")
                || directory.contains("/static/") || directory.contains("/assets/")) return false;
        if (path.matches(".*\\.(?:jpe?g|png|gif|webp|avif|svg|ico|bmp|tiff?|woff2?|ttf|otf|eot|"
                + "css|[cm]?js|map|wasm|mp4|webm|mov|mp3|wav|ogg|flac|pdf|zip|gz|br|json|bin)/?$")) return false;
        // Rockstar exposes translations of the same VI site under these locale routes.
        return !path.matches("/vi/[a-z]{2}[-_](?:[a-z]{4}[-_])?(?:[a-z]{2}|[0-9]{3})(?:/.*)?");
    }

    static boolean discoverable(String url) {
        if (!crawlable(url)) return false;
        String path = URI.create(url).getPath().toLowerCase(Locale.ROOT);
        return path.startsWith("/newswire/article/") || path.startsWith("/vi/")
            || path.startsWith("/merchandise/") || path.startsWith("/products/");
    }

    static boolean publishable(String url) {
        if (!crawlable(url)) return false;
        String path = URI.create(url).getPath().toLowerCase(Locale.ROOT).replaceAll("/+$", "");
        return path.startsWith("/newswire/article/") || path.startsWith("/merchandise/")
            || path.startsWith("/products/") || path.startsWith("/vi/")
            && !path.contains("/characters") && !path.contains("/locations");
    }
}
