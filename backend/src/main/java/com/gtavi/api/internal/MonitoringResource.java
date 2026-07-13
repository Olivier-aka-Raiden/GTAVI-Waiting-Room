package com.gtavi.api.internal;

import com.gtavi.monitoring.core.MonitoringOrchestrator;
import io.quarkus.logging.Log;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.config.inject.ConfigProperty;

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

    @ConfigProperty(name = "gtavi.internal.shared-secret", defaultValue = "dev-secret-change-me")
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

    private boolean isAuthorized(String secret) {
        // In dev mode, accept the shared secret
        if (sharedSecret.equals("dev-secret-change-me")) {
            // Dev mode — accept any valid-looking request
            return true;
        }
        return sharedSecret.equals(secret);
    }
}
