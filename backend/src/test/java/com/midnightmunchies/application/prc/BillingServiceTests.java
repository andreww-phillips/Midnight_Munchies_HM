package com.midnightmunchies.application.prc;

import com.midnightmunchies.domain.ord.OrderLine;
import com.midnightmunchies.domain.ord.OrderStatus;
import com.midnightmunchies.domain.prc.LedgerEntry;
import com.midnightmunchies.domain.prc.OrderSummary;
import com.midnightmunchies.technicalservices.LedgerRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class BillingServiceTests {

    private final LedgerRepository ledger = new LedgerRepository();
    private final BillingService billing = new BillingService(ledger);
    private final Instant createdAt = Instant.parse("2026-11-13T01:00:00Z");
    private final UUID orderId = UUID.randomUUID();
    private final UUID customerId = UUID.randomUUID();
    private final UUID restaurantId = UUID.randomUUID();
    private final UUID courierId = UUID.randomUUID();
    private final OrderLine line = new OrderLine(UUID.randomUUID(), "Poutine", 2, new BigDecimal("12.35"));

    @Test
    void aMissingOrderHasNoSummaryAndDoesNotCreateALedgerRecord() {
        assertTrue(billing.findSummary(orderId).isEmpty());
        assertTrue(ledger.findAll().isEmpty());
    }

    @ParameterizedTest
    @EnumSource(value = OrderStatus.class, names = {"DELIVERED", "REFUNDED"}, mode = EnumSource.Mode.EXCLUDE)
    void noSummaryExistsBeforeDeliveryOrForRejectedAndCancelledOrders(OrderStatus status) {
        LedgerEntry entry = entry(status, null);
        ledger.save(entry);

        assertTrue(billing.findSummary(orderId).isEmpty());
        assertEquals(List.of(entry), ledger.findAll());
    }

    @Test
    void deliveryProvidesAnUnpricedSummaryWhileIssue50IsPending() {
        LedgerEntry delivered = entry(OrderStatus.DELIVERED, null);
        ledger.save(delivered);

        OrderSummary summary = billing.findSummary(orderId).orElseThrow();

        assertEquals(orderId, summary.getOrderId());
        assertEquals(customerId, summary.getCustomerId());
        assertEquals(courierId, summary.getCourierId());
        assertEquals(delivered.timestamps().get(OrderStatus.DELIVERED), summary.getDeliveredAt());
        assertEquals(List.of(line), summary.getOrderLines());
        assertTrue(summary.isPricingPending());
        assertNull(summary.getTotalCharge());
    }

    @ParameterizedTest
    @ValueSource(strings = {"0.00", "30.70"})
    void laterPricingIsVisibleOnTheNextReadWithoutChangingEarlierSummaries(String amount) {
        ledger.save(entry(OrderStatus.DELIVERED, null));
        OrderSummary pending = billing.findSummary(orderId).orElseThrow();
        BigDecimal charge = new BigDecimal(amount);
        ledger.save(entry(OrderStatus.DELIVERED, charge));

        OrderSummary priced = billing.findSummary(orderId).orElseThrow();

        assertFalse(priced.isPricingPending());
        assertEquals(charge, priced.getTotalCharge());
        assertEquals(pending.getOrderLines(), priced.getOrderLines());
        assertEquals(pending.getDeliveredAt(), priced.getDeliveredAt());
        assertTrue(pending.isPricingPending());
        assertNull(pending.getTotalCharge());
        assertEquals(1, ledger.findAll().size());
    }

    @Test
    void repeatedSummaryReadsNeverRepriceOrChangeTheStoredPurchase() {
        LedgerEntry delivered = entry(OrderStatus.DELIVERED, new BigDecimal("30.70"));
        ledger.save(delivered);
        List<LedgerEntry> before = ledger.findAll();

        for (int i = 0; i < 3; i++) {
            assertEquals(new BigDecimal("30.70"), billing.findSummary(orderId).orElseThrow().getTotalCharge());
        }

        assertEquals(before, ledger.findAll());
        assertSame(delivered, ledger.findByOrderId(orderId).orElseThrow());
    }

    @Test
    void futureRefundStatusCanRetainTheOriginalDeliveryAndChargeInTheSummary() {
        ledger.save(entry(OrderStatus.REFUNDED, new BigDecimal("30.70")));

        OrderSummary summary = billing.findSummary(orderId).orElseThrow();

        assertEquals(OrderStatus.REFUNDED, summary.getStatus());
        assertEquals(createdAt.plusSeconds(600), summary.getDeliveredAt());
        assertEquals(new BigDecimal("30.70"), summary.getTotalCharge());
    }

    @Test
    void aRefundedRecordWithoutARecordedDeliveryDoesNotProduceASummary() {
        ledger.save(new LedgerEntry(orderId, customerId, restaurantId, courierId, List.of(line),
                OrderStatus.REFUNDED, createdAt,
                Map.of(OrderStatus.PLACED, createdAt, OrderStatus.REFUNDED, createdAt.plusSeconds(660)), null));

        assertTrue(billing.findSummary(orderId).isEmpty());
    }

    @Test
    void requiresAnOrderIdAndLedgerDependency() {
        assertThrows(NullPointerException.class, () -> billing.findSummary(null));
        assertThrows(NullPointerException.class, () -> new BillingService(null));
    }

    private LedgerEntry entry(OrderStatus status, BigDecimal charge) {
        Map<OrderStatus, Instant> times = new java.util.EnumMap<>(OrderStatus.class);
        times.put(OrderStatus.PLACED, createdAt);
        if (status != OrderStatus.PLACED) {
            times.put(status, createdAt.plusSeconds(600));
        }
        if (status == OrderStatus.REFUNDED) {
            times.put(OrderStatus.DELIVERED, createdAt.plusSeconds(600));
            times.put(OrderStatus.REFUNDED, createdAt.plusSeconds(660));
        }
        return new LedgerEntry(orderId, customerId, restaurantId, courierId, List.of(line),
                status, createdAt, times, charge);
    }
}
