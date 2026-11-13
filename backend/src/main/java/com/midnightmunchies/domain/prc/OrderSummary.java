package com.midnightmunchies.domain.prc;

import com.midnightmunchies.domain.ord.OrderLine;
import com.midnightmunchies.domain.ord.OrderStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Immutable post-delivery view of the recorded purchase (R-PRC-02).
 * A missing charge is explicitly pending pricing, never a zero-cost receipt.
 * The ledger supplies monetary facts; constructing a summary never calculates fees.
 */
public final class OrderSummary {

    private final LedgerEntry order;

    public OrderSummary(LedgerEntry order) {
        this.order = Objects.requireNonNull(order, "order must not be null");
        if ((order.status() != OrderStatus.DELIVERED && order.status() != OrderStatus.REFUNDED)
                || !order.timestamps().containsKey(OrderStatus.DELIVERED)) {
            throw new IllegalArgumentException("An order summary requires a recorded delivery");
        }
    }

    public UUID getOrderId() {
        return order.orderId();
    }

    public UUID getCustomerId() {
        return order.customerId();
    }

    public UUID getRestaurantId() {
        return order.restaurantId();
    }

    public UUID getCourierId() {
        return order.courierId();
    }

    public List<OrderLine> getOrderLines() {
        return order.orderLines();
    }

    public OrderStatus getStatus() {
        return order.status();
    }

    public Instant getCreatedAt() {
        return order.createdAt();
    }

    public Instant getDeliveredAt() {
        return order.timestamps().get(OrderStatus.DELIVERED);
    }

    public Map<OrderStatus, Instant> getTimestamps() {
        return order.timestamps();
    }

    public BigDecimal getTotalCharge() {
        return order.totalCharge();
    }

    public Charge getCharge() {
        return order.charge();
    }

    public boolean isPricingPending() {
        return order.totalCharge() == null;
    }
}
