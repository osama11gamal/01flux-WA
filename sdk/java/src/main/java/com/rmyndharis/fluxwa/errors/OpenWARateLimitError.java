package com.rmyndharis.fluxwa.errors;

/** 429 Too Many Requests — rate limited. */
public class FluxWaRateLimitError extends FluxWaApiError {
    public FluxWaRateLimitError(String message, int status, Object body, String errorKind) {
        super(message, status, body, errorKind);
    }
}
