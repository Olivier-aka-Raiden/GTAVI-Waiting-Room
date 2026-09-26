package com.gtavi.monitoring.core;

import java.text.Normalizer;
import java.util.Locale;

/** Translate observed platform labels to app codes; unknown hardware is never guessed. */
public final class PlatformNames {
    public static final String EXTRACTION_RULES = """
        Platform normalization is a required output contract:
        - PlayStation 5, PlayStation 5 Pro, PS5, and their trademark/punctuation variants -> PS5.
        - Xbox Series X, Xbox Series S, Xbox Series X|S, and XSX -> XSX.
        - PC or Windows PC -> PC, only when explicitly stated in the source.
        Normalize observed labels; do not infer support from prior knowledge or another product.
        Return canonical codes instead of display names and remove duplicate codes.
        Keep the product or edition even if its platform is missing or unrecognized.
        For a scalar platform use UNKNOWN; for a platforms array use an empty array when no supported code is established.
        Never substitute a known console for different or future hardware.
        """;

    private PlatformNames() {}

    public static String normalize(String label) {
        if (label == null || label.isBlank()) return null;
        String key = Normalizer.normalize(label.replace("™", "").replace("®", ""), Normalizer.Form.NFKC).toLowerCase(Locale.ROOT)
            .replace("™", "").replace("®", "").replaceAll("[^a-z0-9]", "");
        return switch (key) {
            case "ps5", "playstation5", "sonyplaystation5", "playstation5ps5", "ps5playstation5",
                 "ps5pro", "playstation5pro", "sonyps5" -> "PS5";
            case "xsx", "xss", "xboxsx", "xboxseriesx", "xboxseriess", "xboxseriesxs",
                 "xboxseriesxseriess", "xboxseriesxboxseriess", "xboxseriesxxboxseriess",
                 "xboxseriesxands", "xboxseriesxandseriess",
                 "xboxseriesxandxboxseriess", "microsoftxboxseriesx", "microsoftxboxseriesxs" -> "XSX";
            case "pc", "windows", "windowspc", "pcwindows" -> "PC";
            case "unknown" -> "UNKNOWN";
            default -> null;
        };
    }
}
