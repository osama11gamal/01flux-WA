package com.rmyndharis.fluxwa.model;

/** Result of validating the configured API key. */
public record AuthValidateResponse(boolean valid, String role) {}
