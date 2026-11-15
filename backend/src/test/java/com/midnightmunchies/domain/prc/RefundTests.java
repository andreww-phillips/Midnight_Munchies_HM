package com.midnightmunchies.domain.prc;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class RefundTests {

    private final UUID refundId = UUID.randomUUID();
    private final UUID orderId = UUID.randomUUID();
    private final UUID operatorId = UUID.randomUUID();
    private final Instant refundedAt = Instant.parse("2026-11-15T02:00:00Z");

    @ParameterizedTest
    @ValueSource(strings = {"0.00", "30.19", "30.705"})
    void preservesRecordedAmountWithoutRepricingOrRounding(String amount) {
        BigDecimal recorded = new BigDecimal(amount);
        Refund refund = new Refund(refundId, orderId, recorded, refundedAt, operatorId);

        assertEquals(refundId, refund.refundId());
        assertEquals(orderId, refund.orderId());
        assertEquals(recorded, refund.amount());
        assertEquals(refundedAt, refund.refundedAt());
        assertEquals(operatorId, refund.operatorId());
    }

    @Test
    void rejectsNegativeAmountsRatherThanAlteringTheChargeSign() {
        assertThrows(IllegalArgumentException.class,
                () -> new Refund(refundId, orderId, new BigDecimal("-0.01"), refundedAt, operatorId));
    }

    @ParameterizedTest
    @ValueSource(strings = {"refundId", "orderId", "amount", "refundedAt", "operatorId"})
    void requiresEveryRefundFact(String missing) {
        NullPointerException exception = assertThrows(NullPointerException.class,
                () -> new Refund(missing.equals("refundId") ? null : refundId,
                        missing.equals("orderId") ? null : orderId,
                        missing.equals("amount") ? null : new BigDecimal("30.19"),
                        missing.equals("refundedAt") ? null : refundedAt,
                        missing.equals("operatorId") ? null : operatorId));

        assertEquals(missing + " must not be null", exception.getMessage());
    }
}
