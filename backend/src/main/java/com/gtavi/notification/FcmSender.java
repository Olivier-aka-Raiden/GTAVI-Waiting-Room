package com.gtavi.notification;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.messaging.*;
import io.quarkus.logging.Log;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Base64;
import java.util.Map;
import java.util.Optional;

/**
 * Sends push notifications via Firebase Cloud Messaging.
 * Initializes Firebase Admin SDK from service account JSON (portable, works anywhere).
 */
@ApplicationScoped
public class FcmSender {

    @ConfigProperty(name = "gtavi.fcm.enabled", defaultValue = "false")
    boolean enabled;

    @ConfigProperty(name = "gtavi.fcm.service-account-json")
    Optional<String> serviceAccountJson;

    private FirebaseMessaging messaging;
    private boolean initialized = false;

    @PostConstruct
    void init() {
        if (!enabled || serviceAccountJson.isEmpty()) {
            Log.info("FCM disabled — push notifications will not be sent");
            return;
        }

        try {
            String json = serviceAccountJson.get();
            // Accept both raw JSON and base64-encoded JSON
            if (!json.trim().startsWith("{")) {
                json = new String(Base64.getDecoder().decode(json));
            }

            FirebaseOptions options = FirebaseOptions.builder()
                .setCredentials(GoogleCredentials.fromStream(
                    new ByteArrayInputStream(json.getBytes())))
                .build();

            // Only initialize if not already done (e.g. by Quarkiverse extension)
            if (FirebaseApp.getApps().isEmpty()) {
                FirebaseApp.initializeApp(options);
            }

            messaging = FirebaseMessaging.getInstance();
            initialized = true;
            Log.infof("FCM initialized from service account JSON for project: %s",
                FirebaseApp.getInstance().getOptions().getProjectId());

        } catch (IOException e) {
            Log.error("Failed to initialize Firebase", e);
        }
    }

    /**
     * Send a notification to a single device.
     *
     * @param token  FCM device token
     * @param title  notification title
     * @param body   notification body
     * @param data   optional key-value data payload (for deep links)
     * @return the FCM message ID, or null if failed
     */
    public String send(String token, String title, String body, Map<String, String> data) {
        if (!enabled || !initialized) {
            Log.debugf("FCM disabled — skipping notification: %s", title);
            return "disabled";
        }

        try {
            Message.Builder builder = Message.builder()
                .setToken(token)
                .setNotification(Notification.builder()
                    .setTitle(title)
                    .setBody(body)
                    .build());

            if (data != null && !data.isEmpty()) {
                builder.putAllData(data);
            }

            // Android-specific config for high priority
            builder.setAndroidConfig(AndroidConfig.builder()
                .setPriority(AndroidConfig.Priority.HIGH)
                .build());

            String messageId = messaging.send(builder.build());
            Log.debugf("FCM sent: %s → %s...", title, token.substring(0, Math.min(8, token.length())));
            return messageId;

        } catch (FirebaseMessagingException e) {
            String errorCode = e.getMessagingErrorCode() != null
                ? e.getMessagingErrorCode().name() : "UNKNOWN";
            Log.errorf("FCM failed for token %s...: %s — %s",
                token.substring(0, Math.min(8, token.length())),
                errorCode, e.getMessage());

            if ("UNREGISTERED".equals(errorCode) || "INVALID_ARGUMENT".equals(errorCode)) {
                return "INVALID_TOKEN";
            }
            return null;
        }
    }

    public boolean isEnabled() {
        return enabled && initialized;
    }
}
