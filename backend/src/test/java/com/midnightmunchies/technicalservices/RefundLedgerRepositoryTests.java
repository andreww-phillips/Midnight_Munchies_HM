package com.midnightmunchies.technicalservices;

import com.midnightmunchies.domain.ord.OrderLine;
import com.midnightmunchies.domain.ord.OrderStatus;
import com.midnightmunchies.domain.prc.Charge;
import com.midnightmunchies.domain.prc.LedgerEntry;
import com.midnightmunchies.domain.prc.Refund;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class RefundLedgerRepositoryTests {

    private final LedgerRepository repository = new LedgerRepository();
    private final Instant placedAt = Instant.parse("2026-11-15T01:00:00Z");
    private final Instant deliveredAt = placedAt.plusSeconds(600);
    private final Instant refundedAt = deliveredAt.plusSeconds(60);
    private final UUID operatorId = UUID.randomUUID();
    private final Charge charge = new Charge(new BigDecimal("24.70"), new BigDecimal("3.99"),
            new BigDecimal("1.50"), new BigDecimal("0.00"), new BigDecimal("30.19"));

    @Test
    void emptyLedgerHasNoRefunds() {
        assertTrue(repository.findAllRefunds().isEmpty());
        assertTrue(repository.findRefundByOrderId(UUID.randomUUID()).isEmpty());
    }

    @Test
    void refundIsSeparateAndLeavesOriginalSnapshotAndChargeUnchanged() {
        LedgerEntry original = saveOrder(charge.total(), charge);
        List<LedgerEntry> ordersBefore = repository.findAll();
        Refund refund = refund(original, UUID.randomUUID(), refundedAt, charge.total());

        assertSame(refund, repository.appendRefund(refund));

        assertEquals(refund, repository.findRefundByOrderId(original.orderId()).orElseThrow());
        assertEquals(List.of(refund), repository.findAllRefunds());
        assertEquals(ordersBefore, repository.findAll());
        assertSame(original, repository.findByOrderId(original.orderId()).orElseThrow());
        assertSame(charge, repository.findByOrderId(original.orderId()).orElseThrow().charge());
        assertEquals(OrderStatus.DELIVERED, original.status());
        assertEquals(charge.total(), original.totalCharge());
        assertFalse(original.timestamps().containsKey(OrderStatus.REFUNDED));
        assertTrue(repository.findRefundByOrderId(UUID.randomUUID()).isEmpty());
    }

    @Test
    void rejectsRepeatedAndReplacementRefundsForTheSameOrder() {
        LedgerEntry order = saveOrder(charge.total(), charge);
        Refund first = repository.appendRefund(refund(order, UUID.randomUUID(), refundedAt, charge.total()));
        Refund replacement = refund(order, UUID.randomUUID(), refundedAt.plusSeconds(1), charge.total());

        assertThrows(IllegalStateException.class, () -> repository.appendRefund(first));
        assertThrows(IllegalStateException.class, () -> repository.appendRefund(replacement));

        assertEquals(List.of(first), repository.findAllRefunds());
        assertSame(first, repository.findRefundByOrderId(order.orderId()).orElseThrow());
        assertSame(order, repository.findByOrderId(order.orderId()).orElseThrow());
    }

    @Test
    void refundIdsCannotBeReusedForAnotherOrder() {
        LedgerEntry firstOrder = saveOrder(charge.total(), charge);
        LedgerEntry secondOrder = saveOrder(charge.total(), charge);
        UUID refundId = UUID.randomUUID();
        Refund first = repository.appendRefund(refund(firstOrder, refundId, refundedAt, charge.total()));

        assertThrows(IllegalArgumentException.class,
                () -> repository.appendRefund(refund(secondOrder, refundId, refundedAt, charge.total())));

        assertEquals(List.of(first), repository.findAllRefunds());
        assertTrue(repository.findRefundByOrderId(secondOrder.orderId()).isEmpty());
    }

    @Test
    void rejectsRefundsWithoutALedgerOrderOrRecordedCharge() {
        Refund orphan = new Refund(UUID.randomUUID(), UUID.randomUUID(), charge.total(), refundedAt, operatorId);
        assertThrows(IllegalArgumentException.class, () -> repository.appendRefund(orphan));
        LedgerEntry unpriced = saveOrder(null, null);
        assertThrows(IllegalStateException.class,
                () -> repository.appendRefund(refund(unpriced, UUID.randomUUID(), refundedAt, BigDecimal.ZERO)));

        assertTrue(repository.findAllRefunds().isEmpty());
        assertSame(unpriced, repository.findByOrderId(unpriced.orderId()).orElseThrow());
    }

    @ParameterizedTest
    @ValueSource(strings = {"0.00", "30.18", "30.20"})
    void rejectsPartialOrExcessiveRefundsWithoutChangingTheCharge(String amount) {
        LedgerEntry order = saveOrder(charge.total(), charge);

        assertThrows(IllegalArgumentException.class, () -> repository.appendRefund(
                refund(order, UUID.randomUUID(), refundedAt, new BigDecimal(amount))));

        assertTrue(repository.findAllRefunds().isEmpty());
        assertSame(order, repository.findByOrderId(order.orderId()).orElseThrow());
    }

    @Test
    void acceptsEquivalentDecimalScalesWithoutRounding() {
        LedgerEntry order = saveOrder(charge.total(), charge);
        Refund refund = refund(order, UUID.randomUUID(), refundedAt, new BigDecimal("30.190"));

        repository.appendRefund(refund);

        assertEquals(new BigDecimal("30.190"), repository.findRefundByOrderId(order.orderId()).orElseThrow().amount());
        assertEquals(new BigDecimal("30.19"), order.totalCharge());
    }

    @Test
    void supportsARecordedZeroChargeAndALegacyTotalWithoutABreakdown() {
        LedgerEntry free = saveOrder(BigDecimal.ZERO, null);
        LedgerEntry legacy = saveOrder(new BigDecimal("30.705"), null);
        Refund zero = refund(free, UUID.randomUUID(), refundedAt, BigDecimal.ZERO);
        Refund historical = refund(legacy, UUID.randomUUID(), refundedAt.plusSeconds(1), new BigDecimal("30.705"));

        repository.appendRefund(zero);
        repository.appendRefund(historical);

        assertEquals(List.of(zero, historical), repository.findAllRefunds());
        assertNull(free.charge());
        assertNull(legacy.charge());
        assertEquals(new BigDecimal("30.705"), legacy.totalCharge());
    }

    @Test
    void rejectsRefundTimestampsBeforeRecordedOrderFacts() {
        LedgerEntry order = saveOrder(charge.total(), charge);
        for (Instant invalid : List.of(placedAt.minusSeconds(1), deliveredAt.minusSeconds(1))) {
            assertThrows(IllegalArgumentException.class,
                    () -> repository.appendRefund(refund(order, UUID.randomUUID(), invalid, charge.total())));
        }
        assertTrue(repository.findAllRefunds().isEmpty());
        assertSame(order, repository.findByOrderId(order.orderId()).orElseThrow());
    }

    @Test
    void acceptsARefundAtTheSameInstantAsDelivery() {
        LedgerEntry order = saveOrder(charge.total(), charge);
        Refund refund = refund(order, UUID.randomUUID(), deliveredAt, charge.total());

        assertEquals(refund, repository.appendRefund(refund));
    }

    @Test
    void refundQuerySnapshotsAreUnmodifiableAndOrderedByTimeThenRefundId() {
        LedgerEntry firstOrder = saveOrder(charge.total(), charge);
        LedgerEntry secondOrder = saveOrder(charge.total(), charge);
        LedgerEntry thirdOrder = saveOrder(charge.total(), charge);
        Refund higher = refund(firstOrder, UUID.fromString("00000000-0000-0000-0000-000000000002"),
                refundedAt, charge.total());
        Refund lower = refund(secondOrder, UUID.fromString("00000000-0000-0000-0000-000000000001"),
                refundedAt, charge.total());
        Refund later = refund(thirdOrder, UUID.randomUUID(), refundedAt.plusSeconds(1), charge.total());
        repository.appendRefund(higher);
        List<Refund> earlierResults = repository.findAllRefunds();
        repository.appendRefund(later);
        repository.appendRefund(lower);

        assertEquals(List.of(lower, higher, later), repository.findAllRefunds());
        assertEquals(List.of(higher), earlierResults);
        assertThrows(UnsupportedOperationException.class, earlierResults::clear);
    }

    @Test
    void concurrentRefundInsertsProduceExactlyOneRecord() throws Exception {
        LedgerEntry order = saveOrder(charge.total(), charge);
        CountDownLatch start = new CountDownLatch(1);
        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            Future<Boolean> first = executor.submit(() -> appendAfterStart(order, start));
            Future<Boolean> second = executor.submit(() -> appendAfterStart(order, start));
            start.countDown();

            assertNotEquals(first.get(5, TimeUnit.SECONDS), second.get(5, TimeUnit.SECONDS));
        }
        assertEquals(1, repository.findAllRefunds().size());
        assertTrue(repository.findRefundByOrderId(order.orderId()).isPresent());
        assertSame(order, repository.findByOrderId(order.orderId()).orElseThrow());
    }

    @Test
    void separateRepositoriesDoNotShareRefundRecords() {
        LedgerEntry order = saveOrder(charge.total(), charge);
        repository.appendRefund(refund(order, UUID.randomUUID(), refundedAt, charge.total()));

        assertTrue(new LedgerRepository().findAllRefunds().isEmpty());
    }

    @Test
    void nullArgumentsAreRejectedWithoutChangingAnyRecords() {
        assertThrows(NullPointerException.class, () -> repository.appendRefund(null));
        assertThrows(NullPointerException.class, () -> repository.findRefundByOrderId(null));
        assertTrue(repository.findAllRefunds().isEmpty());
        assertTrue(repository.findAll().isEmpty());
    }

    private boolean appendAfterStart(LedgerEntry order, CountDownLatch start) throws InterruptedException {
        if (!start.await(5, TimeUnit.SECONDS)) throw new AssertionError("Refund test did not start");
        try {
            repository.appendRefund(refund(order, UUID.randomUUID(), refundedAt, charge.total()));
            return true;
        } catch (IllegalStateException duplicate) {
            assertEquals("Order has already been refunded", duplicate.getMessage());
            return false;
        }
    }

    private Refund refund(LedgerEntry order, UUID refundId, Instant time, BigDecimal amount) {
        return new Refund(refundId, order.orderId(), amount, time, operatorId);
    }

    private LedgerEntry saveOrder(BigDecimal total, Charge breakdown) {
        return repository.save(new LedgerEntry(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), List.of(new OrderLine(UUID.randomUUID(), "Original meal", 2, new BigDecimal("12.35"))),
                OrderStatus.DELIVERED, placedAt,
                Map.of(OrderStatus.PLACED, placedAt, OrderStatus.DELIVERED, deliveredAt), total, breakdown));
    }
}
