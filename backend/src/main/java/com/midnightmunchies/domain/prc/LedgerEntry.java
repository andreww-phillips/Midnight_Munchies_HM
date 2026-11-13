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
 * Immutable snapshot of one order for the R-PRC-03 ledger.
 * Item details come from the order's placement snapshots, never the live menu.
 * A null totalCharge means no charge has been recorded; zero means a recorded free order.
 * Pricing supplies the charge later; this record does not calculate fees or round money.
 * Refund transactions must be recorded separately, preserving the original totalCharge.
 */
public record LedgerEntry(
        UUID orderId,
        UUID customerId,
        UUID restaurantId,
        UUID courierId,
        List<OrderLine> orderLines,
        OrderStatus status,
        Instant createdAt,
        Map<OrderStatus, Instant> timestamps,
        BigDecimal totalCharge
) implements HistoryEntry {

    public LedgerEntry {
        Objects.requireNonNull(orderId, "orderId must not be null");
        Objects.requireNonNull(customerId, "customerId must not be null");
        Objects.requireNonNull(restaurantId, "restaurantId must not be null");
        Objects.requireNonNull(status, "status must not be null");
        Objects.requireNonNull(createdAt, "createdAt must not be null");
        orderLines = List.copyOf(Objects.requireNonNull(orderLines, "orderLines must not be null"));
        if (orderLines.isEmpty()) {
            throw new IllegalArgumentException("orderLines must contain at least one line");
        }
        timestamps = Map.copyOf(Objects.requireNonNull(timestamps, "timestamps must not be null"));
        if (!createdAt.equals(timestamps.get(OrderStatus.PLACED))) {
            throw new IllegalArgumentException("PLACED timestamp must match createdAt");
        }
        if (!timestamps.containsKey(status)) {
            throw new IllegalArgumentException("A timestamp is required for the current status");
        }
        if (timestamps.values().stream().anyMatch(timestamp -> timestamp.isBefore(createdAt))) {
            throw new IllegalArgumentException("Order timestamps must not precede createdAt");
        }
        if (totalCharge != null && totalCharge.signum() < 0) {
            throw new IllegalArgumentException("totalCharge must not be negative");
        }
    }
}
