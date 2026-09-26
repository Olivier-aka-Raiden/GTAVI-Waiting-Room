package com.gtavi.monitoring.core;

import com.gtavi.domain.RetailOffer;
import java.net.URI;
import java.util.*;
import java.util.regex.Pattern;

/** Stable commerce URLs: decode only URI-unreserved escapes, never reserved separators. */
public final class OfferIdentity {
    private OfferIdentity() {}
    public static String url(String value) {
        if (value == null || value.isBlank()) return "";
        try {
            URI uri = URI.create(value);
            if (uri.getHost() == null || uri.getUserInfo() != null || !("https".equalsIgnoreCase(uri.getScheme()) || "http".equalsIgnoreCase(uri.getScheme()))) return "";
            String path = unescape(uri.getRawPath()).replaceAll("/+$", "");
            String query = uri.getRawQuery() == null ? "" : Arrays.stream(uri.getRawQuery().split("&"))
                .filter(p -> !p.toLowerCase(Locale.ROOT).matches("(utm_[^=]*|fbclid|gclid)=.*"))
                .map(OfferIdentity::unescape).sorted().collect(java.util.stream.Collectors.joining("&"));
            return uri.getScheme().toLowerCase(Locale.ROOT) + "://" + uri.getHost().toLowerCase(Locale.ROOT)
                + (uri.getPort() < 0 ? "" : ":" + uri.getPort()) + path + (query.isEmpty() ? "" : "?" + query);
        } catch (IllegalArgumentException e) { return ""; }
    }
    private static String unescape(String value) {
        return Pattern.compile("%([0-9a-fA-F]{2})").matcher(value).replaceAll(match -> {
            char c = (char) Integer.parseInt(match.group(1), 16);
            return Character.toString(c).matches("[a-zA-Z0-9._~-]") ? Character.toString(c) : match.group().toUpperCase(Locale.ROOT);
        });
    }
    public static boolean listing(String source, String value) {
        String normalized = url(value);
        if (normalized.isBlank()) return false;
        if ("WOG".equals(source)) return normalized.matches("https?://(?:www\\.)?wog\\.ch/.*?/details/product/[0-9]+(?:[-/?].*)?");
        return true;
    }
    public static String key(RetailOffer offer) {
        return offer.getRetailerCode() + "|" + offer.getPlatform() + "|" + url(offer.getUrl())
            + "|" + Objects.toString(offer.getCurrency(), "") + "|" + Objects.toString(offer.getCountryCode(), "");
    }
    /** Compatibility view also repairs pre-fix records without erasing source evidence. */
    public static List<RetailOffer> distinct(List<RetailOffer> records) {
        var result = new LinkedHashMap<String, RetailOffer>();
        records.stream().sorted(Comparator.comparing(RetailOffer::getLastSuccessfulCheckAt,
            Comparator.nullsFirst(Comparator.naturalOrder()))).forEach(offer -> {
                if (!listing(offer.getRetailerCode(), offer.getUrl())) return;
                offer.setUrl(url(offer.getUrl()));
                String key = key(offer);
                RetailOffer old = result.get(key);
                if (old != null && offer.getPrice() == null) offer.setPrice(old.getPrice());
                result.put(key, offer);
            });
        return new ArrayList<>(result.values());
    }
}
