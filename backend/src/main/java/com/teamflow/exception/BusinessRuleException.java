package com.teamflow.exception;

/** Thrown when a request is well-formed and authorized but violates a
 * domain business rule (e.g. circular dependency, invalid status
 * transition, progress out of range). Maps to HTTP 400/409. */
public class BusinessRuleException extends RuntimeException {

    private final boolean conflict;

    public BusinessRuleException(String message) {
        this(message, false);
    }

    public BusinessRuleException(String message, boolean conflict) {
        super(message);
        this.conflict = conflict;
    }

    public boolean isConflict() {
        return conflict;
    }
}
