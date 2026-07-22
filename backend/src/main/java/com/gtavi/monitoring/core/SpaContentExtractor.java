package com.gtavi.monitoring.core;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Extracts human-readable content from Next.js App Router SPA pages.
 *
 * Pages like rockstargames.com/VI/editions embed all content inside
 * {@code self.__next_f.push([1,"...escaped JSON..."])} calls.
 * Standard HTML noise stripping removes all &lt;script&gt; tags, leaving
 * the AI with nothing to extract from.
 *
 * This utility extracts the text content from those payloads so the
 * AI receives actual data to structure — no page-specific parsing.
 */
public final class SpaContentExtractor {

    // Matches self.__next_f.push([1,"<escaped JSON>"])
    private static final Pattern RSC_PUSH = Pattern.compile(
        "self\\.__next_f\\.push\\(\\[1,\"(.*?)\"\\]\\)",
        Pattern.DOTALL
    );

    private SpaContentExtractor() {}

    /**
     * Extract human-readable text from a Next.js RSC page.
     * Returns the original HTML if no RSC payloads are found (non-SPA page).
     */
    public static String extractContent(String html) {
        StringBuilder content = new StringBuilder();

        Matcher m = RSC_PUSH.matcher(html);
        int found = 0;
        while (m.find()) {
            found++;
            String payload = unescape(m.group(1));
            extractText(payload, content);
        }

        if (found == 0) {
            // Not a Next.js RSC page — return original HTML
            return html;
        }

        return content.toString();
    }

    /**
     * Walk the JSON tree and collect all visible text strings,
     * skipping HTML tags, JS references, and style tokens.
     */
    private static void extractText(String json, StringBuilder out) {
        // Simple approach: extract all JSON string values that look like
        // human-readable text (contain letters, spaces, and are at least
        // a few characters long). Skip URLs, CSS classes, JS module paths.
        int len = json.length();
        int i = 0;
        while (i < len) {
            // Find next quoted string
            int start = json.indexOf('"', i);
            if (start == -1) break;

            int end = start + 1;
            while (end < len) {
                char c = json.charAt(end);
                if (c == '"') break;
                if (c == '\\') end++; // skip escaped char
                end++;
            }
            if (end >= len) break;

            String value = unescape(json.substring(start + 1, end));
            i = end + 1;

            if (isMeaningfulText(value)) {
                out.append(value).append('\n');
            }
        }
    }

    /**
     * Heuristic: keep strings that look like human-readable content.
     * Filters out URLs, CSS classes, file paths, and JSON boilerplate.
     */
    private static boolean isMeaningfulText(String s) {
        if (s.length() < 3 || s.length() > 500) return false;
        // Keep store URLs — the AI needs them for product links
        if (s.startsWith("http") && (s.contains("playstation") || s.contains("xbox")
            || s.contains("microsoft") || s.contains("store"))) return true;
        if (s.startsWith("http")) return false;
        if (s.startsWith("/VI/_next/")) return false;
        if (s.startsWith("_")) return false;
        if (s.matches("[a-zA-Z0-9_-]+")) return false;
        if (s.contains("\\") && s.length() < 20) return false;
        if (s.matches(".*\\.[a-z]{2,4}$")) return false;
        return s.contains(" ") && s.matches(".*[a-zA-Z].*");
    }

    private static String unescape(String s) {
        if (s == null) return "";
        StringBuilder sb = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '\\' && i + 1 < s.length()) {
                char next = s.charAt(i + 1);
                switch (next) {
                    case '"': sb.append('"'); i++; break;
                    case '\\': sb.append('\\'); i++; break;
                    case 'n': sb.append('\n'); i++; break;
                    case 't': sb.append('\t'); i++; break;
                    case 'u':
                        if (i + 5 < s.length()) {
                            try {
                                int cp = Integer.parseInt(s.substring(i + 2, i + 6), 16);
                                sb.append((char) cp);
                                i += 5;
                            } catch (NumberFormatException e) { sb.append(c); }
                        } else { sb.append(c); }
                        break;
                    default: sb.append(c); break;
                }
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }
}
