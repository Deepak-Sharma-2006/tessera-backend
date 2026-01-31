package com.studencollabfin.server.exception;

/**
 * Exception thrown when a user tries to rejoin a pod before cooldown expires
 */
public class CooldownException extends RuntimeException {
    private final int minutesRemaining;

    public CooldownException(String message, int minutesRemaining) {
        super(message);
        this.minutesRemaining = minutesRemaining;
    }

    public CooldownException(String message) {
        super(message);
        this.minutesRemaining = 0;
    }

    public int getMinutesRemaining() {
        return minutesRemaining;
    }
}
