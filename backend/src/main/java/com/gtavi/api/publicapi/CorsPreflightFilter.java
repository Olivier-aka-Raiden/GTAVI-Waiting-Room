package com.gtavi.api.publicapi;

import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.container.PreMatching;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.Provider;

import java.io.IOException;

/**
 * Intercepts OPTIONS preflight requests BEFORE RESTEasy routing.
 * Returns CORS headers immediately so the browser proceeds with PUT/POST.
 */
@Provider
@PreMatching
public class CorsPreflightFilter implements ContainerRequestFilter {

    @Override
    public void filter(ContainerRequestContext ctx) throws IOException {
        if (!"OPTIONS".equalsIgnoreCase(ctx.getMethod())) return;

        ctx.abortWith(Response.ok()
            .header("Access-Control-Allow-Origin", "*")
            .header("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS")
            .header("Access-Control-Allow-Headers", "Content-Type, Authorization")
            .header("Access-Control-Max-Age", "86400")
            .build());
    }
}
