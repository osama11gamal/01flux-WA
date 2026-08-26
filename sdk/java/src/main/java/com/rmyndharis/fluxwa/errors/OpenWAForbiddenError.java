package com.rmyndharis.fluxwa.errors;

/** 403 Forbidden — the API key's role is insufficient for this endpoint. */
public class FluxWaForbiddenError extends FluxWaApiError {
    public FluxWaForbiddenError(String message, int status, Object body, String errorKind) {
        super(message, status, body, errorKind);
    }
}
