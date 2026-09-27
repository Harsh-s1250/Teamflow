package com.teamflow.exception;

import java.time.Instant;

/** Consistent shape for every error response returned by the API. Never
 * includes stack traces, SQL, or any internal implementation detail. */
public record ApiError(
        Instant timestamp,
        int status,
        String error,
        String message,
        String path
) {
    public static ApiError of(int status, String error, String message, String path) {
        return new ApiError(Instant.now(), status, error, message, path);
    }
}
