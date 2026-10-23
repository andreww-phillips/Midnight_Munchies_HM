package com.midnightmunchies.application;

import com.midnightmunchies.domain.ord.IllegalOrderTransitionException;
import com.midnightmunchies.domain.ord.Actor;
import com.midnightmunchies.domain.ord.Order;
import com.midnightmunchies.domain.ord.OrderLine;
import com.midnightmunchies.domain.ord.OrderStatus;
import com.midnightmunchies.domain.reg.UserRole;
import com.midnightmunchies.technicalservices.ord.repository.InMemoryOrderRepository;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.*;

class PreparationQueueReadyAndCancelTests {

    private static final Actor ACTOR = new Actor(UUID.randomUUID(), UserRole.RESTAURANT_STAFF);

    private final InMemoryOrderRepository orders = new InMemoryOrderRepository();
    private final PreparationQueue queue = new PreparationQueue(orders);
    private final UUID restaurantId = UUID.randomUUID();

    @Test
    void markReadyMovesPreparingOrderToReady() {
        enqueue();
        Order preparing = queue.startNext(restaurantId, ACTOR);

        Order ready = queue.markReady(preparing, ACTOR);

        assertEquals(OrderStatus.READY, ready.getStatus());
        assertEquals(OrderStatus.READY, orders.findById(preparing.getId()).orElseThrow().getStatus());
    }

    @Test
    void markReadyRejectsOrdersThatAreNotPreparing() {
        Order placed = orders.save(order());
        Order accepted = enqueue();
        Order ready = enqueue();
        queue.startNext(restaurantId, ACTOR);
        queue.markReady(accepted, ACTOR);
        queue.startNext(restaurantId, ACTOR);
        queue.markReady(ready, ACTOR);

        assertThrows(IllegalOrderTransitionException.class, () -> queue.markReady(placed, ACTOR));
        assertThrows(IllegalOrderTransitionException.class, () -> queue.markReady(ready, ACTOR));
        assertEquals(OrderStatus.PLACED, placed.getStatus());
        assertEquals(OrderStatus.READY, ready.getStatus());
    }

    @Test
    void cancellingAQueuedOrderRemovesItFromTheQueue() {
        Order a = enqueue();
        Order b = enqueue();
        Order c = enqueue();

        queue.cancel(a, ACTOR);

        assertEquals(OrderStatus.CANCELLED, a.getStatus());
        assertEquals(List.of(b, c), queue.view(restaurantId));
        assertEquals(b.getId(), queue.startNext(restaurantId, ACTOR).getId());
        assertEquals(c.getId(), queue.startNext(restaurantId, ACTOR).getId());
    }

    @Test
    void cancellingAPreparingOrderDoesNotChangeTheWaitingQueue() {
        Order a = enqueue();
        Order b = enqueue();
        queue.startNext(restaurantId, ACTOR);

        queue.cancel(a, ACTOR);

        assertEquals(OrderStatus.CANCELLED, a.getStatus());
        assertEquals(List.of(b), queue.view(restaurantId));
    }

    @Test
    void readyAndTerminalOrdersCannotBeCancelled() {
        Order ready = enqueue();
        queue.startNext(restaurantId, ACTOR);
        queue.markReady(ready, ACTOR);
        Order cancelled = orders.save(order());
        queue.cancel(cancelled, ACTOR);

        assertThrows(IllegalOrderTransitionException.class, () -> queue.cancel(ready, ACTOR));
        assertThrows(IllegalOrderTransitionException.class, () -> queue.cancel(cancelled, ACTOR));
        assertEquals(OrderStatus.READY, ready.getStatus());
    }

    @Test
    void unsavedOrdersAreReportedAsNotFound() {
        Order unsaved = order();

        assertThrows(OrderNotFoundException.class, () -> queue.cancel(unsaved, ACTOR));
        assertThrows(OrderNotFoundException.class, () -> queue.markReady(unsaved, ACTOR));
        assertThrows(NullPointerException.class, () -> queue.cancel(null, ACTOR));
        assertThrows(NullPointerException.class, () -> queue.markReady(null, ACTOR));
    }

    @Test
    void concurrentCancelAndStartNeverStartACancelledOrder() throws Exception {
        for (int round = 0; round < 200; round++) {
            Order first = enqueue();
            Order second = enqueue();
            ExecutorService pool = Executors.newFixedThreadPool(2);
            CountDownLatch go = new CountDownLatch(1);
            Future<?> cancel = pool.submit(() -> {
                go.await();
                return queue.cancel(first, ACTOR);
            });
            Future<Order> start = pool.submit(() -> {
                go.await();
                return queue.startNext(restaurantId, ACTOR);
            });
            go.countDown();
            Order started = start.get();
            List<Throwable> cancelErrors = new ArrayList<>();
            try {
                cancel.get();
            } catch (Exception exception) {
                cancelErrors.add(exception.getCause());
            }
            pool.shutdown();

            assertTrue(cancelErrors.isEmpty());
            assertEquals(OrderStatus.CANCELLED, orders.findById(first.getId()).orElseThrow().getStatus());
            Order storedSecond = orders.findById(second.getId()).orElseThrow();
            if (started.getId().equals(second.getId())) {
                assertEquals(OrderStatus.PREPARING, storedSecond.getStatus());
                queue.cancel(storedSecond, ACTOR);
            } else {
                assertEquals(first.getId(), started.getId());
                assertEquals(List.of(second), queue.view(restaurantId));
                queue.cancel(storedSecond, ACTOR);
            }
            assertTrue(queue.view(restaurantId).isEmpty());
        }
    }

    private Order enqueue() {
        Order order = order();
        order.accept();
        return queue.enqueueAccepted(order);
    }

    private Order order() {
        return new Order(UUID.randomUUID(), UUID.randomUUID(), restaurantId,
                List.of(new OrderLine(UUID.randomUUID(), 1, new BigDecimal("6.00"))), Instant.now());
    }
}
