package com.gtavi.notification.fcm;

import io.quarkus.logging.Log;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.Optional;

/**
 * FCM push notification sender — pure JDK HttpClient + java.security crypto.
 * No Firebase SDK, no gRPC, no protobuf, no reflection config needed.
 * Works in both JVM and native GraalVM builds.
 *
 * Replaces the Firebase Admin SDK approach which fails in native mode
 * due to Message.Builder JSON serialization issues.
 *
 * Delete this package and restore FcmSender when a native-compatible
 * Firebase SDK solution becomes available.
 */
@ApplicationScoped
public class FcmHttpSender {

    @ConfigProperty(name = "gtavi.fcm.enabled", defaultValue = "false")
    boolean enabled;

    @ConfigProperty(name = "gtavi.fcm.service-account-json")
    Optional<String> serviceAccountJson;

    private final HttpClient httpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10))
        .build();

    private String projectId;
    private String clientEmail;
    private PrivateKey privateKey;
    private boolean initialized = false;

    // Cached OAuth2 token
    private String cachedAccessToken;
    private Instant tokenExpiry = Instant.MIN;

    @PostConstruct
    void init() {
        if (!enabled || serviceAccountJson.isEmpty()) {
            Log.info("FCM (HTTP) disabled — push notifications will not be sent");
            return;
        }

        try {
            String json = serviceAccountJson.get();
            if (!json.trim().startsWith("{")) {
                json = new String(Base64.getDecoder().decode(json), StandardCharsets.UTF_8);
            }

            projectId = extractJsonField(json, "project_id");
            clientEmail = extractJsonField(json, "client_email");
            String privateKeyPem = extractJsonField(json, "private_key");

            if (projectId == null || clientEmail == null || privateKeyPem == null) {
                Log.error("FCM service account JSON missing required fields (project_id, client_email, private_key)");
                return;
            }

            privateKey = loadPrivateKey(privateKeyPem);
            initialized = true;
            Log.infof("FCM (HTTP) initialized for project: %s", projectId);

        } catch (Exception e) {
            Log.error("Failed to initialize FCM HTTP sender", e);
        }
    }

    public String send(String token, String title, String body, Map<String, String> data) {
        if (!enabled || !initialized) {
            Log.debugf("FCM (HTTP) disabled — skipping: %s", title);
            return "disabled";
        }

        try {
            String accessToken = getAccessToken();
            String payload = buildPayload(token, title, body, data);
            String url = "https://fcm.googleapis.com/v1/projects/" + projectId + "/messages:send";

            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Authorization", "Bearer " + accessToken)
                .header("Content-Type", "application/json; charset=UTF-8")
                .timeout(Duration.ofSeconds(15))
                .POST(HttpRequest.BodyPublishers.ofString(payload))
                .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                String name = extractJsonField(response.body(), "name");
                Log.debugf("FCM sent: %s → %s...", title, token.substring(0, Math.min(8, token.length())));
                return name;
            }

            Log.errorf("FCM HTTP %d: %s", response.statusCode(), response.body());

            if (response.statusCode() == 400 || response.statusCode() == 404) {
                String respBody = response.body();
                if (respBody.contains("UNREGISTERED") ||
                    respBody.contains("registration-token-not-registered") ||
                    respBody.contains("SenderId mismatch") ||
                    respBody.contains("Requested entity was not found")) {
                    return "INVALID_TOKEN";
                }
            }
            return null;

        } catch (Exception e) {
            Log.errorf("FCM send failed: %s", e.getMessage());
            return null;
        }
    }

    public boolean isEnabled() {
        return enabled && initialized;
    }

    // ── Payload builder ──

    private String buildPayload(String token, String title, String body, Map<String, String> data) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\"message\":{");
        sb.append("\"token\":\"").append(escapeJson(token)).append("\",");
        sb.append("\"notification\":{");
        sb.append("\"title\":\"").append(escapeJson(title)).append("\",");
        sb.append("\"body\":\"").append(escapeJson(body)).append("\"}");
        if (data != null && !data.isEmpty()) {
            sb.append(",\"data\":{");
            boolean first = true;
            for (var entry : data.entrySet()) {
                if (!first) sb.append(",");
                sb.append("\"").append(escapeJson(entry.getKey()))
                  .append("\":\"").append(escapeJson(entry.getValue())).append("\"");
                first = false;
            }
            sb.append("}");
        }
        sb.append(",\"android\":{\"priority\":\"high\"}");
        sb.append("}}");
        return sb.toString();
    }

    // ── OAuth2 via service account JWT ──

    private synchronized String getAccessToken() throws Exception {
        if (cachedAccessToken != null && Instant.now().isBefore(tokenExpiry.minusSeconds(60))) {
            return cachedAccessToken;
        }

        String jwt = createJwt();
        String body = "grant_type=" +
            "urn%3Aietf%3Aparams%3Aoauth%3Agrant-type%3Ajwt-bearer" +
            "&assertion=" + jwt;

        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create("https://oauth2.googleapis.com/token"))
            .header("Content-Type", "application/x-www-form-urlencoded")
            .timeout(Duration.ofSeconds(10))
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            throw new RuntimeException("OAuth2 token request failed: HTTP " +
                response.statusCode() + " — " + response.body());
        }

        cachedAccessToken = extractJsonField(response.body(), "access_token");
        String expiresIn = extractJsonField(response.body(), "expires_in");
        tokenExpiry = Instant.now().plusSeconds(Long.parseLong(expiresIn));
        return cachedAccessToken;
    }

    private String createJwt() throws Exception {
        long now = Instant.now().getEpochSecond();
        long exp = now + 3600;

        String header = base64url("{\"alg\":\"RS256\",\"typ\":\"JWT\"}");
        String claims = base64url(
            "{\"iss\":\"" + clientEmail + "\"," +
            "\"scope\":\"https://www.googleapis.com/auth/firebase.messaging\"," +
            "\"aud\":\"https://oauth2.googleapis.com/token\"," +
            "\"exp\":" + exp + "," +
            "\"iat\":" + now + "}");

        String signingInput = header + "." + claims;
        Signature signer = Signature.getInstance("SHA256withRSA");
        signer.initSign(privateKey);
        signer.update(signingInput.getBytes(StandardCharsets.UTF_8));
        String signature = Base64.getUrlEncoder().withoutPadding().encodeToString(signer.sign());

        return signingInput + "." + signature;
    }

    // ── Crypto helpers ──

    private static PrivateKey loadPrivateKey(String pem) throws Exception {
        String key = pem
            .replace("-----BEGIN PRIVATE KEY-----", "")
            .replace("-----END PRIVATE KEY-----", "")
            .replace("-----BEGIN RSA PRIVATE KEY-----", "")
            .replace("-----END RSA PRIVATE KEY-----", "")
            .replaceAll("\\s", "");
        byte[] keyBytes = Base64.getDecoder().decode(key);
        PKCS8EncodedKeySpec spec = new PKCS8EncodedKeySpec(keyBytes);
        KeyFactory kf = KeyFactory.getInstance("RSA");
        return kf.generatePrivate(spec);
    }

    private static String base64url(String data) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(
            data.getBytes(StandardCharsets.UTF_8));
    }

    // ── Minimal JSON parser (no library needed, works in native) ──

    static String extractJsonField(String json, String fieldName) {
        String search = "\"" + fieldName + "\"";
        int idx = json.indexOf(search);
        if (idx < 0) return null;
        idx = json.indexOf(':', idx + search.length());
        if (idx < 0) return null;
        idx++;
        while (idx < json.length() && (json.charAt(idx) == ' ' ||
            json.charAt(idx) == '\n' || json.charAt(idx) == '\r')) {
            idx++;
        }
        if (idx >= json.length()) return null;

        if (json.charAt(idx) == '"') {
            return unescapeJson(json, idx + 1);
        } else {
            StringBuilder sb = new StringBuilder();
            for (int i = idx; i < json.length(); i++) {
                char c = json.charAt(i);
                if (c == ',' || c == '}' || c == ']' ||
                    c == ' ' || c == '\n' || c == '\r') break;
                sb.append(c);
            }
            return sb.toString();
        }
    }

    private static String unescapeJson(String json, int start) {
        StringBuilder sb = new StringBuilder();
        for (int i = start; i < json.length(); i++) {
            char c = json.charAt(i);
            if (c == '\\' && i + 1 < json.length()) {
                i++;
                char next = json.charAt(i);
                sb.append(switch (next) {
                    case '"', '\\', '/' -> next;
                    case 'n' -> '\n';
                    case 'r' -> '\r';
                    case 't' -> '\t';
                    default -> next;
                });
            } else if (c == '"') {
                return sb.toString();
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    private static String escapeJson(String s) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                case '\b' -> sb.append("\\b");
                case '\f' -> sb.append("\\f");
                default -> {
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        return sb.toString();
    }
}
