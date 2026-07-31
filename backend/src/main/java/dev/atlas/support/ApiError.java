package dev.atlas.support;

public record ApiError(String code, String message, String requestId) {}
