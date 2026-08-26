package com.rmyndharis.fluxwa.errors;

/** 409 Conflict — typically an engine-not-ready condition from the backend. */
public class FluxWaConflictError extends FluxWaApiError {
    public FluxWaConflictError(String message, int status, Object body, String errorKind) {
        super(message, status, body, errorKind);
    }
}
