package com.gtavi.monitoring.core;

import java.util.Locale;

/** Title-based fallback for official Rockstar videos whose labels omit "trailer". */
public final class RockstarVideoType {
    private RockstarVideoType() {}

    public static String normalize(String title, String extractedType) {
        String normalizedTitle = title == null ? "" : title.toLowerCase(Locale.ROOT);
        if (normalizedTitle.contains("trailer") || normalizedTitle.contains("extended look")) {
            return "TRAILER";
        }
        return extractedType == null || extractedType.isBlank() ? "OTHER_VIDEO" : extractedType;
    }
}
