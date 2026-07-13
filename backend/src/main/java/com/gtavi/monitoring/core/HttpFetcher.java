package com.gtavi.monitoring.core;

import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jsoup.Connection;
import org.jsoup.Jsoup;

import java.io.IOException;

/**
 * Fetches HTML content from URLs with retry, timeout, and browser-like headers.
 * Respects HTTP 429 (Retry-After), 5xx retry with backoff, and connection limits.
 */
@ApplicationScoped
public class HttpFetcher {

    @ConfigProperty(name = "gtavi.monitoring.user-agent",
        defaultValue = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36")
    String userAgent;

    @ConfigProperty(name = "gtavi.monitoring.default-timeout-ms", defaultValue = "15000")
    int defaultTimeoutMs;

    private static final int MAX_RETRIES = 3;
    private static final long BASE_BACKOFF_MS = 2000;
    private static final int MAX_RESPONSE_SIZE = 2 * 1024 * 1024; // 2MB

    /**
     * Fetch HTML from the given URL and return the body text.
     * Retries on transient failures with exponential backoff.
     *
     * @return the HTML body as a string
     * @throws IOException if all retries are exhausted
     */
    public String fetch(String url) throws IOException {
        return fetch(url, defaultTimeoutMs);
    }

    /**
     * Fetch with explicit timeout.
     */
    public String fetch(String url, int timeoutMs) throws IOException {
        IOException lastException = null;

        for (int attempt = 0; attempt < MAX_RETRIES; attempt++) {
            try {
                Connection.Response response = Jsoup.connect(url)
                    .userAgent(userAgent)
                    .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,*/*;q=0.8")
                    .header("Accept-Language", "en-US,en;q=0.9,fr;q=0.8")
                    .header("Accept-Encoding", "gzip, deflate, br")
                    .header("Cache-Control", "no-cache")
                    .header("DNT", "1")
                    .timeout(timeoutMs)
                    .maxBodySize(MAX_RESPONSE_SIZE)
                    .followRedirects(true)
                    .ignoreContentType(true)
                    .execute();

                int statusCode = response.statusCode();

                if (statusCode == 200) {
                    return response.body();
                }

                if (statusCode == 429) {
                    String retryAfter = response.header("Retry-After");
                    long waitMs = parseRetryAfter(retryAfter);
                    Log.debugf("HTTP 429 from %s, waiting %dms (attempt %d/%d)",
                        url, waitMs, attempt + 1, MAX_RETRIES);
                    sleep(waitMs);
                    continue;
                }

                // 503, 403, 500 — all retryable with backoff in case of temporary blocks
                if (statusCode >= 500 || statusCode == 403 || statusCode == 503 || statusCode == 502) {
                    Log.debugf("HTTP %d from %s (attempt %d/%d)",
                        statusCode, url, attempt + 1, MAX_RETRIES);
                    if (attempt < MAX_RETRIES - 1) {
                        sleep(backoff(attempt));
                        continue;
                    }
                }

                // Non-retryable status
                throw new IOException("HTTP error fetching URL. Status=" + statusCode + ", URL=[" + url + "]");

            } catch (IOException e) {
                if (isRetryable(e) && attempt < MAX_RETRIES - 1) {
                    lastException = e;
                    Log.debugf("Fetch error from %s: %s (attempt %d/%d)",
                        url, e.getMessage(), attempt + 1, MAX_RETRIES);
                    sleep(backoff(attempt));
                } else {
                    throw e;
                }
            }
        }

        throw lastException != null ? lastException
            : new IOException("Failed to fetch " + url + " after " + MAX_RETRIES + " attempts");
    }

    private long backoff(int attempt) {
        return BASE_BACKOFF_MS * (long) Math.pow(2, attempt);
    }

    private boolean isRetryable(IOException e) {
        String msg = e.getMessage().toLowerCase();
        return msg.contains("timeout") || msg.contains("connect")
            || msg.contains("reset") || msg.contains("broken pipe")
            || msg.contains("status=403") || msg.contains("status=503")
            || msg.contains("status=502");
    }

    private long parseRetryAfter(String header) {
        if (header == null || header.isEmpty()) return 5000;
        try {
            return Long.parseLong(header) * 1000;
        } catch (NumberFormatException e) {
            return 5000;
        }
    }

    private void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
