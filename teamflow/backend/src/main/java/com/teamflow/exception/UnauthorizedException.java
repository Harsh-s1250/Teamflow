package com.teamflow.exception;

/** Thrown when an authenticated user is not permitted to perform the
 * requested operation on the requested resource (role or ownership
 * mismatch). Maps to HTTP 403. Distinct from Spring Security's own
 * AuthenticationException, which covers missing/invalid credentials
 * (HTTP 401). */
public class UnauthorizedException extends RuntimeException {
    public UnauthorizedException(String message) {
        super(message);
    }
}
