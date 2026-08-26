package com.rmyndharis.fluxwa.errors;

/** 401 Unauthorized — missing or invalid API key. */
public class FluxWaAuthError extends FluxWaApiError {
    public FluxWaAuthError(String message, int status, Object body, String errorKind) {
        super(message, status, body, errorKind);
    }
}
