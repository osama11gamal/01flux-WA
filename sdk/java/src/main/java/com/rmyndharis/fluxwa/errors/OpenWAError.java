package com.rmyndharis.fluxwa.errors;

/** Base class for every error thrown by the SDK. */
public class FluxWaError extends RuntimeException {
    public FluxWaError(String message) {
        super(message);
    }
}
