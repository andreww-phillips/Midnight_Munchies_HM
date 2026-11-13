package com.midnightmunchies.application.prc;

import com.midnightmunchies.application.ord.OrderEventPublisher;
import com.midnightmunchies.application.ord.OrderTransitions;
import com.midnightmunchies.domain.ord.Actor;
import com.midnightmunchies.domain.ord.Order;
import com.midnightmunchies.domain.ord.OrderEvent;
import com.midnightmunchies.domain.ord.OrderLine;
import com.midnightmunchies.domain.ord.OrderStatus;
import com.midnightmunchies.domain.prc.LedgerEntry;
import com.midnightmunchies.domain.reg.UserRole;
import com.midnightmunchies.technicalservices.InMemoryOrderRepository;
import com.midnightmunchies.technicalservices.LedgerRepository;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class OrderLedgerServiceTests {

    private final Instant createdAt = Instant.parse("2026-11-13T01:00:00Z");
    private final InMemoryOrderRepository orders = new InMemoryOrderRepository();
    private final LedgerRepository ledger = new LedgerRepository();
    private final OrderLedgerService service = new OrderLedgerService(orders, ledger);
    private final Order order = orders.save(new Order(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
            List.of(new OrderLine(UUID.randomUUID(), "Poutine", 2, new BigDecimal("12.35"))), createdAt));
    private final UUID courierId = UUID.randomUUID();

    @Test
    void recordsPlacementFactsWithoutAChargeCourierOrDomainMutation() {
        LedgerEntry placed = service.recordCreated(order);

        assertEquals(order.getCustomerId(), placed.customerId());
        assertEquals(order.getRestaurantId(), placed.restaurantId());
        assertEquals(order.getOrderLines(), placed.orderLines());
        assertEquals(OrderStatus.PLACED, placed.status());
        assertEquals(Map.of(OrderStatus.PLACED, createdAt), placed.timestamps());
        assertNull(placed.totalCharge());
        assertNull(placed.courierId());
        assertEquals(OrderStatus.PLACED, order.getStatus());
        assertFalse(order.hasPendingTransitions());
    }

    @Test
    void repeatedOrLateCreationRecordingDoesNotResetAnExistingRecord() {
        service.recordCreated(order);
        service.onEvent(event(OrderStatus.PLACED, OrderStatus.ACCEPTED, 10));
        LedgerEntry accepted = current();

        assertEquals(accepted, service.recordCreated(order));
        assertEquals(accepted, service.recordCreated(order));
        assertEquals(List.of(accepted), ledger.findAll());
    }

    @Test
    void keepsAllObservedTimestampsAndUsesTheCourierOnlyAfterAHandoff() {
        service.recordCreated(order);
        service.onEvent(event(OrderStatus.PLACED, OrderStatus.ACCEPTED, 10));
        service.onEvent(event(OrderStatus.ACCEPTED, OrderStatus.PREPARING, 20));
        service.onEvent(event(OrderStatus.PREPARING, OrderStatus.READY, 30));
        assertNull(current().courierId());
        service.onEvent(event(OrderStatus.READY, OrderStatus.PICKED_UP, 40));
        assertEquals(courierId, current().courierId());
        service.onEvent(event(OrderStatus.PICKED_UP, OrderStatus.DELIVERED, 50));

        assertEquals(OrderStatus.DELIVERED, current().status());
        assertEquals(6, current().timestamps().size());
        assertEquals(createdAt.plusSeconds(40), current().timestamps().get(OrderStatus.PICKED_UP));
        assertEquals(createdAt.plusSeconds(50), current().timestamps().get(OrderStatus.DELIVERED));
        assertEquals(courierId, current().courierId());
        assertNull(current().totalCharge());
        // The observer never drives the authoritative order's state.
        assertEquals(OrderStatus.PLACED, order.getStatus());
    }

    @Test
    void replayingAnEarlierEventDoesNotDuplicateRecordsOrUndoDelivery() {
        OrderEvent accepted = event(OrderStatus.PLACED, OrderStatus.ACCEPTED, 10);
        service.onEvent(accepted);
        OrderEvent delivered = event(OrderStatus.PICKED_UP, OrderStatus.DELIVERED, 50);
        service.onEvent(delivered);
        LedgerEntry completed = current();

        service.onEvent(accepted);
        service.onEvent(delivered);

        assertEquals(List.of(completed), ledger.findAll());
    }

    @Test
    void delayedEarlierStatesFillTimestampGapsWithoutRegressingTheLatestState() {
        service.onEvent(event(OrderStatus.PICKED_UP, OrderStatus.DELIVERED, 50));
        service.onEvent(event(OrderStatus.PLACED, OrderStatus.ACCEPTED, 10));

        assertEquals(OrderStatus.DELIVERED, current().status());
        assertEquals(createdAt.plusSeconds(10), current().timestamps().get(OrderStatus.ACCEPTED));
        assertEquals(createdAt.plusSeconds(50), current().timestamps().get(OrderStatus.DELIVERED));
    }

    @Test
    void processesBatchedTransitionsEvenWhenTheyShareATimestamp() {
        OrderEventPublisher publisher = new OrderEventPublisher();
        publisher.subscribe(service);
        OrderTransitions transitions = new OrderTransitions(orders, publisher,
                Clock.fixed(createdAt.plusSeconds(10), ZoneOffset.UTC));
        order.accept();
        order.startPreparing();
        order.markReady();

        transitions.save(order, new Actor(UUID.randomUUID(), UserRole.RESTAURANT_STAFF));

        assertEquals(OrderStatus.READY, current().status());
        assertEquals(4, current().timestamps().size());
        assertFalse(order.hasPendingTransitions());
    }

    @Test
    void equalTimeDelayedEventsDoNotUndoAnAuthoritativeLaterState() {
        order.accept();
        order.startPreparing();
        order.markReady();
        service.onEvent(event(OrderStatus.PREPARING, OrderStatus.READY, 10));
        service.onEvent(event(OrderStatus.PLACED, OrderStatus.ACCEPTED, 10));

        assertEquals(OrderStatus.READY, current().status());
        assertTrue(current().timestamps().containsKey(OrderStatus.ACCEPTED));
    }

    @Test
    void canStartFromAnEventForAnOrderWithoutAnEarlierLedgerRecord() {
        service.onEvent(event(OrderStatus.READY, OrderStatus.PICKED_UP, 40));

        assertEquals(OrderStatus.PICKED_UP, current().status());
        assertEquals(Map.of(OrderStatus.PLACED, createdAt, OrderStatus.PICKED_UP,
                createdAt.plusSeconds(40)), current().timestamps());
        assertEquals(courierId, current().courierId());
    }

    @Test
    void lifecycleUpdatesPreserveAnAlreadyRecordedChargeAndEarlierSnapshots() {
        LedgerEntry placed = service.recordCreated(order);
        ledger.save(new LedgerEntry(placed.orderId(), placed.customerId(), placed.restaurantId(), null,
                placed.orderLines(), placed.status(), placed.createdAt(), placed.timestamps(), new BigDecimal("30.70")));
        service.onEvent(event(OrderStatus.PLACED, OrderStatus.ACCEPTED, 10));

        assertEquals(new BigDecimal("30.70"), current().totalCharge());
        assertEquals(OrderStatus.ACCEPTED, current().status());
        assertEquals(Map.of(OrderStatus.PLACED, createdAt), placed.timestamps());
        assertNull(placed.totalCharge());
    }

    @Test
    void rejectsMissingOrdersAndMismatchedRestaurantsWithoutCreatingRecords() {
        OrderEvent missing = new OrderEvent(UUID.randomUUID(), UUID.randomUUID(), order.getRestaurantId(),
                OrderStatus.PLACED, OrderStatus.ACCEPTED, createdAt.plusSeconds(10),
                UUID.randomUUID(), UserRole.RESTAURANT_STAFF);
        OrderEvent wrongRestaurant = new OrderEvent(UUID.randomUUID(), order.getId(), UUID.randomUUID(),
                OrderStatus.PLACED, OrderStatus.ACCEPTED, createdAt.plusSeconds(10),
                UUID.randomUUID(), UserRole.RESTAURANT_STAFF);

        assertThrows(IllegalArgumentException.class, () -> service.onEvent(missing));
        assertThrows(IllegalArgumentException.class, () -> service.onEvent(wrongRestaurant));
        assertTrue(ledger.findAll().isEmpty());
    }

    private LedgerEntry current() {
        return ledger.findByOrderId(order.getId()).orElseThrow();
    }

    private OrderEvent event(OrderStatus from, OrderStatus to, long seconds) {
        boolean handoff = to == OrderStatus.PICKED_UP || to == OrderStatus.DELIVERED;
        return new OrderEvent(UUID.randomUUID(), order.getId(), order.getRestaurantId(), from, to,
                createdAt.plusSeconds(seconds), handoff ? courierId : UUID.randomUUID(),
                handoff ? UserRole.COURIER : UserRole.RESTAURANT_STAFF);
    }
}
