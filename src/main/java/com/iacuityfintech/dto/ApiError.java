package com.iacuityfintech.dto;

/** Uniform JSON error body: { "error": "NODE_NOT_FOUND", "message": "..." }. */
public record ApiError(String error, String message) {
}
