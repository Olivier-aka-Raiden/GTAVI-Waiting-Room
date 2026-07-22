package com.gtavi.api.internal;

import com.gtavi.monitoring.core.MonitoringOrchestrator;
import com.gtavi.notification.fcm.FcmHttpSender;
import io.quarkus.logging.Log;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Map;

/**
 * Internal API for triggering monitoring jobs.
 * Protected by shared secret (dev) or OIDC (prod via Cloud Scheduler).
 */
@Path("/internal/jobs")
@Produces(MediaType.APPLICATION_JSON)
public class MonitoringResource {

    @Inject
    MonitoringOrchestrator orchestrator;

    @Inject
    FcmHttpSender fcmSender;

    @ConfigProperty(name = "gtavi.internal.shared-secret")
    String sharedSecret;

    @POST
    @Path("/check-updates")
    public Response checkUpdates(
        @HeaderParam("X-Internal-Secret") String secret,
        @QueryParam("source") String source
    ) {
        if (!isAuthorized(secret)) {
            return Response.status(Response.Status.UNAUTHORIZED)
                .entity(Map.of("error", "unauthorized"))
                .build();
        }

        try {
            var summary = source != null
                ? orchestrator.runCheck(java.util.Set.of(source))
                : orchestrator.runCheck();

            Log.infof("Monitoring run complete: %d checked, %d success, %d failed, %d events",
                summary.checkedSources(), summary.successfulSources(),
                summary.failedSources(), summary.eventsCreated());

            return Response.ok(Map.of(
                "status", "completed",
                "checkedSources", summary.checkedSources(),
                "successfulSources", summary.successfulSources(),
                "failedSources", summary.failedSources(),
                "eventsCreated", summary.eventsCreated(),
                "startedAt", summary.startedAt().toString(),
                "finishedAt", summary.finishedAt().toString()
            )).build();

        } catch (Exception e) {
            Log.error("Monitoring run failed", e);
            return Response.serverError()
                .entity(Map.of("error", e.getMessage()))
                .build();
        }
    }

    @GET
    @Path("/monitoring/status")
    public Response getStatus(@HeaderParam("X-Internal-Secret") String secret) {
        if (!isAuthorized(secret)) {
            return Response.status(Response.Status.UNAUTHORIZED)
                .entity(Map.of("error", "unauthorized"))
                .build();
        }

        return Response.ok(Map.of(
            "status", "ok",
            "message", "Internal monitoring API is operational"
        )).build();
    }

    @POST
    @Path("/test-notification")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response testNotification(
        @HeaderParam("X-Internal-Secret") String secret,
        Map<String, String> request
    ) {
        if (!isAuthorized(secret)) {
            return Response.status(Response.Status.UNAUTHORIZED)
                .entity(Map.of("error", "unauthorized"))
                .build();
        }

        String token = request.get("pushToken");
        String title = request.getOrDefault("title", "\uD83D\uDCE2 GTA VI Test Notification");
        String body = request.getOrDefault("body", "This is a test notification from the GTA VI Waiting Room backend.");

        if (token == null || token.isBlank()) {
            return Response.status(Response.Status.BAD_REQUEST)
                .entity(Map.of("error", "pushToken is required"))
                .build();
        }

        if (!fcmSender.isEnabled()) {
            return Response.ok(Map.of(
                "status", "skipped",
                "reason", "FCM not enabled or not initialized",
                "fcmEnabled", fcmSender.isEnabled()
            )).build();
        }

        String result = fcmSender.send(token, title, body, Map.of(
            "test", "true",
            "sentAt", java.time.Instant.now().toString()
        ));

        if (result == null) {
            return Response.serverError()
                .entity(Map.of("status", "failed", "error", "FCM send returned null — check server logs"))
                .build();
        }

        if ("INVALID_TOKEN".equals(result)) {
            return Response.status(Response.Status.BAD_REQUEST)
                .entity(Map.of("status", "invalid_token", "error", "FCM rejected the token"))
                .build();
        }

        return Response.ok(Map.of(
            "status", "sent",
            "messageId", result,
            "token", token.substring(0, 8) + "..."
        )).build();
    }

    private boolean isAuthorized(String secret) {
        if (secret == null || secret.isBlank() || sharedSecret == null || sharedSecret.isBlank()) {
            return false;
        }
        return MessageDigest.isEqual(
            sharedSecret.getBytes(StandardCharsets.UTF_8),
            secret.getBytes(StandardCharsets.UTF_8));
    }
}
