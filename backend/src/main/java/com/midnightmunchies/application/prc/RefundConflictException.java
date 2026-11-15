package com.midnightmunchies.application.prc;

/** An expected refund eligibility conflict, distinct from an internal commit failure. */
public class RefundConflictException extends RuntimeException {
    public RefundConflictException(String message) {
        super(message);
    }
}
