package com.rmyndharis.fluxwa.resources;

import com.rmyndharis.fluxwa.FluxWaClient;
import com.rmyndharis.fluxwa.http.HttpMethod;
import com.rmyndharis.fluxwa.model.HealthReadyResponse;
import com.rmyndharis.fluxwa.model.HealthResponse;

/** Health resource — connectivity and readiness probes. */
public final class HealthResource {
    private final FluxWaClient client;

    public HealthResource(FluxWaClient client) {
        this.client = client;
    }

    /** General health (also returns the running version). */
    public HealthResponse check() {
        return client.request(HttpMethod.GET, "/api/health", null, null, HealthResponse.class);
    }

    /** Kubernetes liveness probe. */
    public HealthResponse live() {
        return client.request(HttpMethod.GET, "/api/health/live", null, null, HealthResponse.class);
    }

    /** Kubernetes readiness probe — checks both DB connections. */
    public HealthReadyResponse ready() {
        return client.request(HttpMethod.GET, "/api/health/ready", null, null, HealthReadyResponse.class);
    }
}
