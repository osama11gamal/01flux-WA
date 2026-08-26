package com.rmyndharis.fluxwa.errors;

/** Thrown when a request exceeds the configured timeout. */
public class FluxWaTimeoutError extends FluxWaError {
    public FluxWaTimeoutError(long timeoutMs) {
        super("Request timed out after " + timeoutMs + "ms");
    }
}
