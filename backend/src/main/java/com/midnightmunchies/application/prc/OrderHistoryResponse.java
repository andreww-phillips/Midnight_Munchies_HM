package com.midnightmunchies.application.prc;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.midnightmunchies.domain.ord.OrderStatus;
import com.midnightmunchies.domain.prc.Charge;
import com.midnightmunchies.domain.prc.LedgerEntry;
import com.midnightmunchies.domain.prc.Refund;
import com.midnightmunchies.domain.reg.UserRole;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Explicit history projection: filtering an order does not authorize all its billing fields. */
public record OrderHistoryResponse(
        UUID orderId,
        UUID customerId,
        UUID restaurantId,
        UUID courierId,
        List<Line> orderLines,
        OrderStatus status,
        Instant createdAt,
        Map<OrderStatus, Instant> timestamps,
        @JsonInclude(JsonInclude.Include.NON_NULL) Billing billing,
        @JsonInclude(JsonInclude.Include.NON_NULL) String rejectionReason
) {
    public OrderHistoryResponse {
        orderLines = List.copyOf(orderLines);
        timestamps = Map.copyOf(timestamps);
    }

    static OrderHistoryResponse from(LedgerEntry entry, UserRole role, List<Refund> refunds, String rejectionReason) {
        boolean courier = role == UserRole.COURIER;
        boolean canViewBilling = role == UserRole.CUSTOMER || role == UserRole.PLATFORM_OPERATOR;
        List<Line> lines = entry.orderLines().stream()
                .map(line -> new Line(line.getMenuItemId(), line.getMenuItemName(), line.getQuantity(),
                        courier ? null : line.getUnitPrice()))
                .toList();
        Billing billing = canViewBilling
                ? new Billing(entry.totalCharge(), entry.charge(), entry.totalCharge() == null, refunds) : null;
        return new OrderHistoryResponse(entry.orderId(), entry.customerId(), entry.restaurantId(),
                entry.courierId(), lines, entry.status(), entry.createdAt(), entry.timestamps(), billing, rejectionReason);
    }

    public record Line(UUID menuItemId, String menuItemName, int quantity,
                       @JsonInclude(JsonInclude.Include.NON_NULL) BigDecimal unitPrice) {
    }

    /** A null total means unrecorded pricing; a recorded zero is a real charge. */
    public record Billing(BigDecimal totalCharge, Charge charge, boolean pricingPending, List<Refund> refunds) {
        public Billing {
            refunds = List.copyOf(refunds);
        }
    }
}
