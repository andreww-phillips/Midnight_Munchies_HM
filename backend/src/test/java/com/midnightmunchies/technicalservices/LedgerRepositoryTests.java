package com.midnightmunchies.technicalservices;

import com.midnightmunchies.domain.ord.OrderLine;
import com.midnightmunchies.domain.ord.OrderStatus;
import com.midnightmunchies.domain.prc.LedgerEntry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class LedgerRepositoryTests {

    private final LedgerRepository repository = new LedgerRepository();
    private final UUID orderId = UUID.randomUUID();
    private final UUID customerId = UUID.randomUUID();
    private final UUID restaurantId = UUID.randomUUID();
    private final UUID courierId = UUID.randomUUID();
    private final Instant createdAt = Instant.parse("2026-11-13T01:00:00Z");
    private final OrderLine line = new OrderLine(UUID.randomUUID(), "Poutine", 2, new BigDecimal("12.35"));

    @Test
    void anEmptyLedgerHasNoRecordsAndMissingOrdersAreAbsent() {
        assertTrue(repository.findAll().isEmpty());
        assertTrue(repository.findByOrderId(orderId).isEmpty());
    }

    @Test
    void retrievesACompletePurchaseRecordByOrderIdWithoutCalculatingItsCharge() {
        LedgerEntry entry = entry(orderId, createdAt, OrderStatus.DELIVERED, new BigDecimal("30.70"));

        assertEquals(entry, repository.save(entry));
        assertEquals(entry, repository.findByOrderId(orderId).orElseThrow());
        assertEquals(List.of(entry), repository.findAll());
        assertTrue(repository.findByOrderId(UUID.randomUUID()).isEmpty());
    }

    @Test
    void repeatedSavesKeepExactlyOneRecordPerOrderAndPreserveOtherOrders() {
        LedgerEntry first = entry(orderId, createdAt, OrderStatus.PLACED, null);
        LedgerEntry other = entry(UUID.randomUUID(), createdAt.plusSeconds(1), OrderStatus.CANCELLED, null);
        repository.save(first);
        repository.save(other);
        repository.save(first);
        repository.save(first);

        assertEquals(List.of(first, other), repository.findAll());
    }

    @Test
    void aNewStateReplacesTheStoredSnapshotWithoutChangingEarlierQueryResults() {
        LedgerEntry placed = entry(orderId, createdAt, OrderStatus.PLACED, null);
        repository.save(placed);
        List<LedgerEntry> earlierResults = repository.findAll();
        LedgerEntry delivered = entry(orderId, createdAt, OrderStatus.DELIVERED, null);
        repository.save(delivered);

        assertEquals(delivered, repository.findByOrderId(orderId).orElseThrow());
        assertEquals(List.of(delivered), repository.findAll());
        assertEquals(List.of(placed), earlierResults);
        assertEquals(OrderStatus.PLACED, earlierResults.getFirst().status());
    }

    @ParameterizedTest
    @ValueSource(strings = {"0.00", "30.70"})
    void pricingCanSupplyAChargeLaterIncludingAnExplicitZero(String amount) {
        repository.save(entry(orderId, createdAt, OrderStatus.DELIVERED, null));
        assertNull(repository.findByOrderId(orderId).orElseThrow().totalCharge());
        BigDecimal charge = new BigDecimal(amount);
        LedgerEntry priced = entry(orderId, createdAt, OrderStatus.DELIVERED, charge);

        repository.save(priced);

        assertEquals(charge, repository.findByOrderId(orderId).orElseThrow().totalCharge());
        assertEquals(1, repository.findAll().size());
    }

    @Test
    void rejectsChargeChangesOrRemovalWithoutChangingTheStoredRecord() {
        LedgerEntry charged = entry(orderId, createdAt, OrderStatus.DELIVERED, new BigDecimal("30.70"));
        repository.save(charged);

        assertThrows(IllegalArgumentException.class,
                () -> repository.save(entry(orderId, createdAt, OrderStatus.DELIVERED, null)));
        assertThrows(IllegalArgumentException.class,
                () -> repository.save(entry(orderId, createdAt, OrderStatus.DELIVERED, BigDecimal.ZERO)));
        assertThrows(IllegalArgumentException.class,
                () -> repository.save(entry(orderId, createdAt, OrderStatus.DELIVERED, new BigDecimal("31.70"))));

        assertEquals(charged, repository.findByOrderId(orderId).orElseThrow());
        assertEquals(List.of(charged), repository.findAll());
    }

    @Test
    void comparesChargesByValueSoEquivalentDecimalScalesAreAccepted() {
        repository.save(entry(orderId, createdAt, OrderStatus.DELIVERED, new BigDecimal("30.70")));
        LedgerEntry sameCharge = entry(orderId, createdAt, OrderStatus.DELIVERED, new BigDecimal("30.700"));

        repository.save(sameCharge);

        assertEquals(0, new BigDecimal("30.70").compareTo(
                repository.findByOrderId(orderId).orElseThrow().totalCharge()));
        assertEquals(1, repository.findAll().size());
    }

    @Test
    void queryResultsAreUnmodifiableAndDoNotGrowWhenNewOrdersAreSaved() {
        LedgerEntry first = entry(orderId, createdAt, OrderStatus.PLACED, null);
        repository.save(first);
        List<LedgerEntry> snapshot = repository.findAll();

        repository.save(entry(UUID.randomUUID(), createdAt.plusSeconds(1), OrderStatus.PLACED, null));

        assertEquals(List.of(first), snapshot);
        assertThrows(UnsupportedOperationException.class, snapshot::clear);
        assertEquals(2, repository.findAll().size());
    }

    @Test
    void ordersResultsByCreationTimeThenOrderIdInsteadOfSaveOrder() {
        UUID lowerId = UUID.fromString("00000000-0000-0000-0000-000000000001");
        UUID higherId = UUID.fromString("00000000-0000-0000-0000-000000000002");
        LedgerEntry earlier = entry(higherId, createdAt, OrderStatus.PLACED, null);
        LedgerEntry tied = entry(lowerId, createdAt, OrderStatus.PLACED, null);
        LedgerEntry later = entry(UUID.randomUUID(), createdAt.plusSeconds(1), OrderStatus.PLACED, null);
        repository.save(later);
        repository.save(earlier);
        repository.save(tied);

        assertEquals(List.of(tied, earlier, later), repository.findAll());
    }

    @Test
    void separateRepositoryInstancesDoNotShareInMemoryRecords() {
        repository.save(entry(orderId, createdAt, OrderStatus.PLACED, null));

        assertTrue(new LedgerRepository().findAll().isEmpty());
    }

    @Test
    void rejectsNullArgumentsWithoutChangingTheLedger() {
        assertThrows(NullPointerException.class, () -> repository.save(null));
        assertThrows(NullPointerException.class, () -> repository.findByOrderId(null));
        assertTrue(repository.findAll().isEmpty());
    }

    private LedgerEntry entry(UUID id, Instant placedAt, OrderStatus status, BigDecimal charge) {
        Map<OrderStatus, Instant> times = status == OrderStatus.PLACED
                ? Map.of(OrderStatus.PLACED, placedAt)
                : Map.of(OrderStatus.PLACED, placedAt, status, placedAt.plusSeconds(600));
        UUID carriedBy = status == OrderStatus.DELIVERED ? courierId : null;
        return new LedgerEntry(id, customerId, restaurantId, carriedBy, List.of(line),
                status, placedAt, times, charge);
    }
}
