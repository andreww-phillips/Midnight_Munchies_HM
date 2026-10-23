package com.midnightmunchies.application;

import com.midnightmunchies.domain.ord.Actor;
import com.midnightmunchies.domain.ord.Order;
import com.midnightmunchies.domain.ord.OrderLine;
import com.midnightmunchies.domain.ord.OrderStatus;
import com.midnightmunchies.domain.reg.UserRole;
import com.midnightmunchies.technicalservices.ord.repository.InMemoryOrderRepository;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class PreparationQueueListenerTests {

    private static final Actor STAFF = new Actor(UUID.randomUUID(), UserRole.RESTAURANT_STAFF);

    private final InMemoryOrderRepository orders = new InMemoryOrderRepository();
    private final OrderEventPublisher publisher = new OrderEventPublisher();
    private final OrderTransitions transitions = new OrderTransitions(orders, publisher);
    private final PreparationQueue queue = new PreparationQueue(orders, transitions);
    private final UUID restaurantId = UUID.randomUUID();

    {
        publisher.subscribe(new PreparationQueueListener(orders, queue));
    }

    @Test
    void acceptedOrdersJoinTheQueueAutomaticallyInAcceptanceOrder() {
        Order first = accept(order());
        Order second = accept(order());

        assertEquals(List.of(first, second), queue.view(restaurantId));
        assertNotNull(first.getAcceptedAt());
        assertTrue(second.getAcceptedAt().isAfter(first.getAcceptedAt()));
    }

    @Test
    void otherTransitionsDoNotTouchTheQueue() {
        Order rejected = order();
        rejected.reject("Out of stock");
        transitions.save(rejected, STAFF);
        Order cancelled = order();
        cancelled.cancel();
        transitions.save(cancelled, STAFF);

        assertTrue(queue.view(restaurantId).isEmpty());
        assertNull(rejected.getAcceptedAt());
    }

    @Test
    void alreadyQueuedOrderIsNotQueuedAgain() {
        Order order = order();
        order.accept();
        queue.enqueueAccepted(order);
        Instant firstAcceptedAt = order.getAcceptedAt();

        assertDoesNotThrow(() -> transitions.save(order, STAFF));
        assertEquals(firstAcceptedAt, order.getAcceptedAt());
        assertEquals(List.of(order), queue.view(restaurantId));
    }

    @Test
    void orderThatMovedOnBeforeTheEventIsNotQueued() {
        Order order = order();
        order.accept();
        order.cancel();

        transitions.save(order, STAFF);

        assertEquals(OrderStatus.CANCELLED, order.getStatus());
        assertNull(order.getAcceptedAt());
        assertTrue(queue.view(restaurantId).isEmpty());
    }

    @Test
    void fullFlowFromAcceptToReadyThroughTheQueue() {
        Order order = accept(order());

        Order started = queue.startNext(restaurantId, STAFF);
        Order ready = queue.markReady(started, STAFF);

        assertEquals(order.getId(), ready.getId());
        assertEquals(OrderStatus.READY, ready.getStatus());
        assertTrue(queue.view(restaurantId).isEmpty());
    }

    private Order accept(Order order) {
        order.accept();
        transitions.save(order, STAFF);
        return orders.findById(order.getId()).orElseThrow();
    }

    private Order order() {
        return orders.save(new Order(UUID.randomUUID(), UUID.randomUUID(), restaurantId,
                List.of(new OrderLine(UUID.randomUUID(), 1, BigDecimal.TEN)), Instant.now()));
    }
}
