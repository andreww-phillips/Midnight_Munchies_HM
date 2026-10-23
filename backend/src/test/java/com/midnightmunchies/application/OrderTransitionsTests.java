package com.midnightmunchies.application;

import com.midnightmunchies.domain.ord.Actor;
import com.midnightmunchies.domain.ord.IllegalOrderTransitionException;
import com.midnightmunchies.domain.ord.Order;
import com.midnightmunchies.domain.ord.OrderEvent;
import com.midnightmunchies.domain.ord.OrderLine;
import com.midnightmunchies.domain.ord.OrderStatus;
import com.midnightmunchies.domain.ord.repository.OrderRepository;
import com.midnightmunchies.domain.reg.UserRole;
import com.midnightmunchies.technicalservices.ord.repository.InMemoryOrderRepository;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class OrderTransitionsTests {

    private static final Instant NOW = Instant.parse("2026-10-23T20:00:00Z");
    private static final Actor STAFF = new Actor(UUID.randomUUID(), UserRole.RESTAURANT_STAFF);

    private final InMemoryOrderRepository orders = new InMemoryOrderRepository();
    private final OrderEventPublisher publisher = new OrderEventPublisher();
    private final List<OrderEvent> received = new ArrayList<>();
    private final OrderTransitions transitions = new OrderTransitions(orders, publisher, Clock.fixed(NOW, ZoneOffset.UTC));

    {
        publisher.subscribe(received::add);
    }

    @Test
    void saveTurnsEachTransitionIntoAPublishedEvent() {
        Order order = order();
        order.accept();
        order.startPreparing();

        transitions.save(order, STAFF);

        assertEquals(2, received.size());
        OrderEvent first = received.get(0);
        assertEquals(order.getId(), first.orderId());
        assertEquals(order.getRestaurantId(), first.restaurantId());
        assertEquals(OrderStatus.PLACED, first.fromStatus());
        assertEquals(OrderStatus.ACCEPTED, first.toStatus());
        assertEquals(NOW, first.occurredAt());
        assertEquals(STAFF.userId(), first.actorId());
        assertEquals(UserRole.RESTAURANT_STAFF, first.actorRole());
        assertEquals(OrderStatus.ACCEPTED, received.get(1).fromStatus());
        assertEquals(OrderStatus.PREPARING, received.get(1).toStatus());
        assertFalse(order.hasPendingTransitions());
    }

    @Test
    void everyEventGetsAUniqueId() {
        Set<UUID> ids = new HashSet<>();
        for (int i = 0; i < 200; i++) {
            Order order = order();
            order.accept();
            transitions.save(order, STAFF);
        }
        received.forEach(event -> ids.add(event.eventId()));

        assertEquals(200, received.size());
        assertEquals(200, ids.size());
    }

    @Test
    void savingWithoutTransitionsPublishesNothing() {
        transitions.save(order(), STAFF);

        assertTrue(received.isEmpty());
    }

    @Test
    void eventsArePublishedOnlyAfterTheOrderIsSaved() {
        Order order = order();
        order.accept();
        List<OrderStatus> storedStatusWhenNotified = new ArrayList<>();
        publisher.subscribe(event -> storedStatusWhenNotified.add(
                orders.findById(event.orderId()).orElseThrow().getStatus()));

        transitions.save(order, STAFF);

        assertEquals(List.of(OrderStatus.ACCEPTED), storedStatusWhenNotified);
    }

    @Test
    void failedSavePublishesNothing() {
        OrderRepository failing = mock(OrderRepository.class);
        when(failing.save(any())).thenThrow(new IllegalStateException("storage down"));
        OrderTransitions failingTransitions = new OrderTransitions(failing, publisher);
        Order order = order();
        order.accept();

        assertThrows(IllegalStateException.class, () -> failingTransitions.save(order, STAFF));
        assertTrue(received.isEmpty());
    }

    @Test
    void failedTransitionPublishesNothing() {
        Order order = order();

        assertThrows(IllegalOrderTransitionException.class, order::markReady);
        transitions.save(order, STAFF);

        assertTrue(received.isEmpty());
    }

    @Test
    void actorAndOrderAreRequired() {
        assertThrows(NullPointerException.class, () -> transitions.save(order(), null));
        assertThrows(NullPointerException.class, () -> transitions.save(null, STAFF));
    }

    private Order order() {
        return new Order(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                List.of(new OrderLine(UUID.randomUUID(), 1, BigDecimal.ONE)), NOW);
    }
}
