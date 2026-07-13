package com.gtavi.monitoring.core;

import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jsoup.Connection;
import org.jsoup.Jsoup;

import java.io.IOException;

/**
 * Fetches HTML content from URLs with retry, timeout, and User-Agent support.
 * Respects HTTP 429 (Retry-After), 5xx retry with backoff, and connection limits.
 */
@ApplicationScoped
public class HttpFetcher {

    @ConfigProperty(name = "gtavi.monitoring.user-agent", defaultValue = "GtaVIWaitingRoom/1.0")
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

                if (statusCode >= 500 && statusCode < 600) {
                    Log.debugf("HTTP %d from %s (attempt %d/%d)",
                        statusCode, url, attempt + 1, MAX_RETRIES);
                    sleep(backoff(attempt));
                    continue;
                }

                // Non-retryable status
                throw new IOException("HTTP " + statusCode + " from " + url);

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
            || msg.contains("reset") || msg.contains("broken pipe");
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
