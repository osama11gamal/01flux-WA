package com.rmyndharis.fluxwa.errors;

/** 501 Not Implemented — the active engine does not support this operation. */
public class FluxWaNotImplementedError extends FluxWaApiError {
    public FluxWaNotImplementedError(String message, int status, Object body, String errorKind) {
        super(message, status, body, errorKind);
    }
}
