package com.gtavi.api.publicapi;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.time.OffsetDateTime;
import java.util.Map;

/**
 * Health check endpoint.
 */
@Path("/api")
@Produces(MediaType.APPLICATION_JSON)
public class HealthResource {

    @GET
    @Path("/health")
    public Response health() {
        return Response.ok(Map.of(
            "status", "ok",
            "timestamp", OffsetDateTime.now().toString(),
            "version", "1.0.0-SNAPSHOT"
        )).build();
    }
}
