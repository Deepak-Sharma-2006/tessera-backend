package com.studencollabfin.server.exception;

/**
 * Exception thrown when a user is banned from a pod
 */
public class BannedFromPodException extends RuntimeException {
    public BannedFromPodException(String message) {
        super(message);
    }

    public BannedFromPodException(String message, Throwable cause) {
        super(message, cause);
    }
}
