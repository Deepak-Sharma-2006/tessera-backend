package com.studencollabfin.server.exception;

/**
 * Exception thrown when a user lacks permission to perform an action
 */
public class PermissionDeniedException extends RuntimeException {
    public PermissionDeniedException(String message) {
        super(message);
    }

    public PermissionDeniedException(String message, Throwable cause) {
        super(message, cause);
    }
}
