package com.midnightmunchies.domain.prc;

import com.midnightmunchies.domain.ord.OrderLine;
import com.midnightmunchies.domain.ord.OrderStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class OrderSummaryTests {

    private final Instant createdAt = Instant.parse("2026-11-13T01:00:00Z");
    private final Instant deliveredAt = createdAt.plusSeconds(600);
    private final OrderLine line = new OrderLine(UUID.randomUUID(), "Poutine", 2, new BigDecimal("12.35"));

    @Test
    void exposesTheOriginalPurchaseAndRecordedDeliveryWithoutRecalculatingTheTotal() {
        LedgerEntry entry = delivered(new BigDecimal("30.705"));
        OrderSummary summary = new OrderSummary(entry);

        assertEquals(entry.orderId(), summary.getOrderId());
        assertEquals(entry.customerId(), summary.getCustomerId());
        assertEquals(entry.restaurantId(), summary.getRestaurantId());
        assertEquals(entry.courierId(), summary.getCourierId());
        assertEquals(List.of(line), summary.getOrderLines());
        assertEquals(OrderStatus.DELIVERED, summary.getStatus());
        assertEquals(createdAt, summary.getCreatedAt());
        assertEquals(deliveredAt, summary.getDeliveredAt());
        assertEquals(entry.timestamps(), summary.getTimestamps());
        assertEquals(new BigDecimal("30.705"), summary.getTotalCharge());
        assertFalse(summary.isPricingPending());
        assertThrows(UnsupportedOperationException.class, () -> summary.getOrderLines().clear());
        assertThrows(UnsupportedOperationException.class, () -> summary.getTimestamps().clear());
    }

    @Test
    void distinguishesPendingPricingFromAnExplicitlyRecordedFreeOrder() {
        OrderSummary pending = new OrderSummary(delivered(null));
        OrderSummary free = new OrderSummary(delivered(BigDecimal.ZERO));

        assertTrue(pending.isPricingPending());
        assertNull(pending.getTotalCharge());
        assertFalse(free.isPricingPending());
        assertEquals(BigDecimal.ZERO, free.getTotalCharge());
    }

    @ParameterizedTest
    @EnumSource(value = OrderStatus.class, names = {"DELIVERED", "REFUNDED"}, mode = EnumSource.Mode.EXCLUDE)
    void refusesUndeliveredOrdersEvenIfTheyContainAnInconsistentDeliveryTimestamp(OrderStatus status) {
        Map<OrderStatus, Instant> times = new java.util.EnumMap<>(OrderStatus.class);
        times.put(OrderStatus.PLACED, createdAt);
        times.put(OrderStatus.DELIVERED, deliveredAt);
        if (status != OrderStatus.PLACED) {
            times.put(status, createdAt.plusSeconds(10));
        }
        LedgerEntry entry = new LedgerEntry(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                null, List.of(line), status, createdAt, times, null);

        assertThrows(IllegalArgumentException.class, () -> new OrderSummary(entry));
    }

    @Test
    void aFutureRefundCanShowTheOriginalDeliveredPurchaseAndCharge() {
        LedgerEntry delivered = delivered(new BigDecimal("30.70"));
        LedgerEntry refunded = new LedgerEntry(delivered.orderId(), delivered.customerId(), delivered.restaurantId(),
                delivered.courierId(), delivered.orderLines(), OrderStatus.REFUNDED, createdAt,
                Map.of(OrderStatus.PLACED, createdAt, OrderStatus.DELIVERED, deliveredAt,
                        OrderStatus.REFUNDED, deliveredAt.plusSeconds(60)), delivered.totalCharge());
        OrderSummary summary = new OrderSummary(refunded);

        assertEquals(OrderStatus.REFUNDED, summary.getStatus());
        assertEquals(deliveredAt, summary.getDeliveredAt());
        assertEquals(new BigDecimal("30.70"), summary.getTotalCharge());
    }

    @Test
    void refusesARefundedRecordWithoutEvidenceOfDelivery() {
        LedgerEntry entry = new LedgerEntry(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                null, List.of(line), OrderStatus.REFUNDED, createdAt,
                Map.of(OrderStatus.PLACED, createdAt, OrderStatus.REFUNDED, deliveredAt), null);

        assertThrows(IllegalArgumentException.class, () -> new OrderSummary(entry));
    }

    @Test
    void requiresALedgerRecord() {
        assertThrows(NullPointerException.class, () -> new OrderSummary(null));
    }

    private LedgerEntry delivered(BigDecimal charge) {
        return new LedgerEntry(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                List.of(line), OrderStatus.DELIVERED, createdAt,
                Map.of(OrderStatus.PLACED, createdAt, OrderStatus.DELIVERED, deliveredAt), charge);
    }
}
