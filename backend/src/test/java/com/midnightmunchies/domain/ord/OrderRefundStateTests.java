package com.midnightmunchies.domain.ord;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class OrderRefundStateTests {

    @Test
    void deliveryCanBeRefundedExactlyOnceWithARecordedTransition() {
        Order order = restore(OrderStatus.DELIVERED);
        order.refund();

        assertEquals(OrderStatus.REFUNDED, order.getStatus());
        assertEquals(List.of(new OrderTransition(OrderStatus.DELIVERED, OrderStatus.REFUNDED)), order.pullTransitions());
        assertThrows(IllegalOrderTransitionException.class, order::refund);
        assertFalse(order.hasPendingTransitions());
    }

    @ParameterizedTest
    @EnumSource(value = OrderStatus.class, names = "DELIVERED", mode = EnumSource.Mode.EXCLUDE)
    void noOtherStateCanBeRefunded(OrderStatus status) {
        Order order = restore(status);

        assertThrows(IllegalOrderTransitionException.class, order::refund);
        assertEquals(status, order.getStatus());
        assertFalse(order.hasPendingTransitions());
    }

    @Test
    void restoredRefundedOrderIsTerminalAndKeepsAcceptanceFacts() {
        Order order = restore(OrderStatus.REFUNDED);
        assertEquals(Instant.EPOCH.plusSeconds(1), order.getAcceptedAt());
        assertFalse(order.hasPendingTransitions());

        assertThrows(IllegalOrderTransitionException.class, order::accept);
        assertThrows(IllegalOrderTransitionException.class, () -> order.reject("Too late"));
        assertThrows(IllegalOrderTransitionException.class, order::startPreparing);
        assertThrows(IllegalOrderTransitionException.class, order::markReady);
        assertThrows(IllegalOrderTransitionException.class, order::confirmPickup);
        assertThrows(IllegalOrderTransitionException.class, order::confirmDelivery);
        assertThrows(IllegalOrderTransitionException.class, order::cancel);
        assertThrows(IllegalOrderTransitionException.class, order::refund);
        assertFalse(order.hasPendingTransitions());
    }

    private Order restore(OrderStatus status) {
        return Order.restore(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                List.of(new OrderLine(UUID.randomUUID(), "Meal", 1, BigDecimal.TEN)),
                Instant.EPOCH, status, Instant.EPOCH.plusSeconds(1));
    }
}
