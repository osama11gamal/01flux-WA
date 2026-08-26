package com.rmyndharis.fluxwa.errors;

/** 404 Not Found. */
public class FluxWaNotFoundError extends FluxWaApiError {
    public FluxWaNotFoundError(String message, int status, Object body, String errorKind) {
        super(message, status, body, errorKind);
    }
}
