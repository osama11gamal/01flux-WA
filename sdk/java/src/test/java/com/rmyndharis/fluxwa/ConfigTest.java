package com.rmyndharis.fluxwa;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.rmyndharis.fluxwa.errors.FluxWaError;
import com.rmyndharis.fluxwa.http.HttpMethod;
import com.rmyndharis.fluxwa.http.HttpTransport;
import com.rmyndharis.fluxwa.model.SuccessResult;
import com.rmyndharis.fluxwa.support.MockTransport;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class ConfigTest {
    private static ClientConfig.Builder base() {
        return ClientConfig.builder().baseUrl("http://h").apiKey("flx_k1_x");
    }

    @Test
    void rejectsZeroAndNegativeTimeout() {
        assertThrows(IllegalArgumentException.class, () -> base().timeout(Duration.ZERO).build());
        assertThrows(IllegalArgumentException.class, () -> base().timeout(Duration.ofSeconds(-1)).build());
    }

    @Test
    void rejectsMalformedBaseUrl() {
        assertThrows(IllegalArgumentException.class,
            () -> ClientConfig.builder().baseUrl("http://my host:2785").apiKey("flx_k1_x").build());
    }

    @Test
    void rejectsApiKeyWithInteriorControlChar() {
        assertThrows(IllegalArgumentException.class,
            () -> ClientConfig.builder().baseUrl("http://h").apiKey("owa\nk1").build());
    }

    @Test
    void trimsWhitespaceFromBaseUrlAndApiKey() {
        // A trailing newline (e.g. key read from a file/env) must be tolerated, not fatal.
        MockTransport tx = new MockTransport().respond(200, "{\"valid\":true}");
        FluxWaClient c = new FluxWaClient(
            ClientConfig.builder().baseUrl("http://h ").apiKey(" flx_k1_x\n").transport(tx).build());
        c.auth();
        assertEquals("flx_k1_x", tx.lastRequest().headers().get("X-API-Key"));
        assertTrue(tx.lastRequest().url().startsWith("http://h/"));
    }

    @Test
    void transportIllegalArgumentIsWrappedAsFluxWaError() {
        HttpTransport bad = req -> {
            throw new IllegalArgumentException("restricted header name: \"Host\"");
        };
        FluxWaClient c = new FluxWaClient(base().transport(bad).build());
        FluxWaError e = assertThrows(FluxWaError.class,
            () -> c.request(HttpMethod.GET, "/x", null, null, SuccessResult.class));
        assertTrue(e.getMessage().contains("Invalid request"));
    }
}
