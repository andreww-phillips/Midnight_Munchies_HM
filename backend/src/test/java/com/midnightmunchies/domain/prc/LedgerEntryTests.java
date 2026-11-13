package com.midnightmunchies.domain.prc;

import com.midnightmunchies.domain.ord.MenuItem;
import com.midnightmunchies.domain.ord.MenuItemStatus;
import com.midnightmunchies.domain.ord.OrderLine;
import com.midnightmunchies.domain.ord.OrderStatus;
import com.midnightmunchies.domain.reg.UserRole;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class LedgerEntryTests {

    private final UUID orderId = UUID.randomUUID();
    private final UUID customerId = UUID.randomUUID();
    private final UUID restaurantId = UUID.randomUUID();
    private final UUID courierId = UUID.randomUUID();
    private final Instant createdAt = Instant.parse("2026-11-13T01:00:00Z");
    private final OrderLine line = new OrderLine(UUID.randomUUID(), "Poutine", 2, new BigDecimal("12.35"));

    @Test
    void keepsPurchaseFactsWhenTheMenuAndSourceCollectionsChange() {
        List<OrderLine> lines = new ArrayList<>(List.of(line));
        Map<OrderStatus, Instant> times = new EnumMap<>(OrderStatus.class);
        times.put(OrderStatus.PLACED, createdAt);
        times.put(OrderStatus.DELIVERED, createdAt.plusSeconds(600));
        LedgerEntry entry = new LedgerEntry(orderId, customerId, restaurantId, courierId,
                lines, OrderStatus.DELIVERED, createdAt, times, new BigDecimal("30.70"));

        MenuItem changedMenuItem = new MenuItem(line.getMenuItemId(), restaurantId,
                "Loaded Poutine", new BigDecimal("18.00"), MenuItemStatus.SOLD_OUT);
        lines.clear();
        times.clear();

        assertEquals("Poutine", entry.orderLines().getFirst().getMenuItemName());
        assertEquals(2, entry.orderLines().getFirst().getQuantity());
        assertEquals(new BigDecimal("12.35"), entry.orderLines().getFirst().getUnitPrice());
        assertNotEquals(changedMenuItem.getPrice(), entry.orderLines().getFirst().getUnitPrice());
        assertEquals(createdAt.plusSeconds(600), entry.timestamps().get(OrderStatus.DELIVERED));
        assertEquals(new BigDecimal("30.70"), entry.totalCharge());
        assertThrows(UnsupportedOperationException.class, () -> entry.orderLines().clear());
        assertThrows(UnsupportedOperationException.class, () -> entry.timestamps().clear());
    }

    @Test
    void distinguishesAnUnrecordedChargeFromARecordedZeroChargeWithoutRounding() {
        assertNull(entry(OrderStatus.DELIVERED, null).totalCharge());
        assertEquals(BigDecimal.ZERO, entry(OrderStatus.DELIVERED, BigDecimal.ZERO).totalCharge());
        assertEquals(new BigDecimal("30.705"), entry(OrderStatus.DELIVERED, new BigDecimal("30.705")).totalCharge());
    }

    @ParameterizedTest
    @EnumSource(OrderStatus.class)
    void canRepresentEveryOrderStateWithoutEnforcingOrChangingItsLifecycle(OrderStatus status) {
        LedgerEntry entry = entry(status, null);

        assertEquals(status, entry.status());
        assertEquals(createdAt, entry.timestamps().get(OrderStatus.PLACED));
        assertTrue(entry.timestamps().containsKey(status));
    }

    @Test
    void allowsOrdersThatNeverHadACourierOrCharge() {
        LedgerEntry entry = new LedgerEntry(orderId, customerId, restaurantId, null,
                List.of(line), OrderStatus.CANCELLED, createdAt,
                Map.of(OrderStatus.PLACED, createdAt, OrderStatus.CANCELLED, createdAt.plusSeconds(10)), null);

        assertNull(entry.courierId());
        assertNull(entry.totalCharge());
        assertEquals(List.of(), new CourierHistoryScope().filter(List.of(entry),
                new HistorySession(courierId, UserRole.COURIER, null)));
    }

    @Test
    void worksWithTheExistingHistoryStrategiesWithoutChangingThem() {
        LedgerEntry mine = entry(OrderStatus.DELIVERED, new BigDecimal("30.70"));
        LedgerEntry anotherCustomers = new LedgerEntry(UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), UUID.randomUUID(), List.of(line), OrderStatus.PLACED, createdAt,
                Map.of(OrderStatus.PLACED, createdAt), null);
        List<LedgerEntry> entries = List.of(mine, anotherCustomers);

        assertEquals(List.of(mine), new CustomerHistoryScope().filter(entries,
                new HistorySession(customerId, UserRole.CUSTOMER, null)));
        assertEquals(List.of(mine), new RestaurantHistoryScope().filter(entries,
                new HistorySession(UUID.randomUUID(), UserRole.RESTAURANT_STAFF, restaurantId)));
        assertEquals(List.of(mine), new CourierHistoryScope().filter(entries,
                new HistorySession(courierId, UserRole.COURIER, null)));
        assertEquals(entries, new OperatorHistoryScope().filter(entries,
                new HistorySession(UUID.randomUUID(), UserRole.PLATFORM_OPERATOR, null)));
    }

    @Test
    void rejectsNegativeChargesInsteadOfRepresentingARefundAsAnAlteredCharge() {
        assertThrows(IllegalArgumentException.class,
                () -> entry(OrderStatus.DELIVERED, new BigDecimal("-0.01")));
    }

    @Test
    void requiresPlacementAndCurrentStateTimestampsWithoutInventingIntermediateTimes() {
        assertThrows(IllegalArgumentException.class, () -> withTimes(Map.of()));
        assertThrows(IllegalArgumentException.class,
                () -> withTimes(Map.of(OrderStatus.PLACED, createdAt)));
        assertThrows(IllegalArgumentException.class,
                () -> withTimes(Map.of(OrderStatus.PLACED, createdAt.plusSeconds(1),
                        OrderStatus.DELIVERED, createdAt.plusSeconds(600))));
        assertThrows(IllegalArgumentException.class,
                () -> withTimes(Map.of(OrderStatus.PLACED, createdAt,
                        OrderStatus.DELIVERED, createdAt.minusSeconds(1))));
    }

    @Test
    void cannotRecordAnOrderWithNoItems() {
        assertThrows(IllegalArgumentException.class, () -> new LedgerEntry(orderId, customerId,
                restaurantId, null, List.of(), OrderStatus.PLACED, createdAt,
                Map.of(OrderStatus.PLACED, createdAt), null));
    }

    private LedgerEntry withTimes(Map<OrderStatus, Instant> times) {
        return new LedgerEntry(orderId, customerId, restaurantId, courierId, List.of(line),
                OrderStatus.DELIVERED, createdAt, times, null);
    }

    private LedgerEntry entry(OrderStatus status, BigDecimal charge) {
        Map<OrderStatus, Instant> times = new EnumMap<>(OrderStatus.class);
        times.put(OrderStatus.PLACED, createdAt);
        if (status != OrderStatus.PLACED) {
            times.put(status, createdAt.plusSeconds(600));
        }
        return new LedgerEntry(orderId, customerId, restaurantId, courierId, List.of(line),
                status, createdAt, times, charge);
    }
}
