package com.teamflow.exception;

/** Thrown when a requested resource (project, task, user, ...) does not
 * exist, or - for security - should be treated as not existing for the
 * current caller (see UnauthorizedException for the distinction). */
public class ResourceNotFoundException extends RuntimeException {
    public ResourceNotFoundException(String message) {
        super(message);
    }
}
