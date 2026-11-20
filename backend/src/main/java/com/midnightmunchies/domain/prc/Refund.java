package com.midnightmunchies.domain.prc;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Separate, immutable credit; the original order charge is never reduced or replaced. */
public record Refund(
        UUID refundId,
        UUID orderId,
        BigDecimal amount,
        Instant refundedAt,
        UUID operatorId
) {
    public Refund {
        Objects.requireNonNull(refundId, "refundId must not be null");
        Objects.requireNonNull(orderId, "orderId must not be null");
        Objects.requireNonNull(amount, "amount must not be null");
        Objects.requireNonNull(refundedAt, "refundedAt must not be null");
        Objects.requireNonNull(operatorId, "operatorId must not be null");
        if (amount.signum() < 0) {
            throw new IllegalArgumentException("Refund amount must not be negative");
        }
    }
}
