package com.midnightmunchies.application;

import com.midnightmunchies.domain.ord.Actor;
import com.midnightmunchies.domain.ord.Order;
import com.midnightmunchies.domain.ord.OrderLine;
import com.midnightmunchies.domain.ord.OrderStatus;
import com.midnightmunchies.domain.reg.UserRole;
import com.midnightmunchies.technicalservices.ord.repository.InMemoryOrderRepository;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.*;

class PreparationQueueTests {

    private static final Actor ACTOR = new Actor(UUID.randomUUID(), UserRole.RESTAURANT_STAFF);

    private static final Instant NOW = Instant.parse("2026-10-23T20:00:00.123456789Z");

    private final InMemoryOrderRepository orders = new InMemoryOrderRepository();
    private final PreparationQueue queue = new PreparationQueue(orders, Clock.fixed(NOW, ZoneOffset.UTC));
    private final UUID restaurantId = UUID.randomUUID();

    @Test
    void startsOrdersInTheOrderTheyWereAccepted() {
        Order a = enqueue(restaurantId);
        Order b = enqueue(restaurantId);
        Order c = enqueue(restaurantId);

        assertEquals(a.getId(), queue.startNext(restaurantId, ACTOR).getId());
        assertEquals(b.getId(), queue.startNext(restaurantId, ACTOR).getId());
        assertEquals(c.getId(), queue.startNext(restaurantId, ACTOR).getId());
        assertEquals(OrderStatus.PREPARING, a.getStatus());
        assertEquals(OrderStatus.PREPARING, b.getStatus());
        assertEquals(OrderStatus.PREPARING, c.getStatus());
    }

    @Test
    void acceptanceOrderWinsOverPlacementOrder() {
        Order placedFirst = accepted(restaurantId, NOW.minusSeconds(600));
        Order placedSecond = accepted(restaurantId, NOW.minusSeconds(300));
        queue.enqueueAccepted(placedSecond);
        queue.enqueueAccepted(placedFirst);

        assertEquals(placedSecond.getId(), queue.startNext(restaurantId, ACTOR).getId());
        assertEquals(placedFirst.getId(), queue.startNext(restaurantId, ACTOR).getId());
    }

    @Test
    void ordersAcceptedAtTheSameInstantKeepTheirOrderWithMicrosecondTimes() {
        Order a = enqueue(restaurantId);
        Order b = enqueue(restaurantId);
        Order c = enqueue(restaurantId);

        Instant expected = NOW.truncatedTo(ChronoUnit.MICROS);
        assertEquals(expected, a.getAcceptedAt());
        assertEquals(expected.plus(1, ChronoUnit.MICROS), b.getAcceptedAt());
        assertEquals(expected.plus(2, ChronoUnit.MICROS), c.getAcceptedAt());
        assertEquals(List.of(a, b, c), queue.view(restaurantId));
    }

    @Test
    void cancelledOrderIsSkippedWithoutBlockingLaterOrders() {
        Order a = enqueue(restaurantId);
        Order b = enqueue(restaurantId);
        Order c = enqueue(restaurantId);
        b.cancel();
        orders.save(b);

        assertEquals(a.getId(), queue.startNext(restaurantId, ACTOR).getId());
        assertEquals(c.getId(), queue.startNext(restaurantId, ACTOR).getId());
        assertEquals(OrderStatus.CANCELLED, b.getStatus());
    }

    @Test
    void laterOrderCannotSkipAnEarlierWaitingOrder() {
        Order earlier = enqueue(restaurantId);
        Order later = enqueue(restaurantId);

        Order started = queue.startNext(restaurantId, ACTOR);

        assertEquals(earlier.getId(), started.getId());
        assertEquals(OrderStatus.ACCEPTED, later.getStatus());
        assertEquals(List.of(later), queue.view(restaurantId));
    }

    @Test
    void eachRestaurantHasItsOwnQueue() {
        UUID otherRestaurantId = UUID.randomUUID();
        Order mine = enqueue(restaurantId);
        Order theirs = enqueue(otherRestaurantId);

        assertEquals(List.of(mine), queue.view(restaurantId));
        assertEquals(List.of(theirs), queue.view(otherRestaurantId));
        assertEquals(theirs.getId(), queue.startNext(otherRestaurantId, ACTOR).getId());
        assertEquals(List.of(mine), queue.view(restaurantId));
    }

    @Test
    void acceptsAnyNumberOfOrdersWithoutCapacityRejection() {
        List<Order> enqueued = new ArrayList<>();
        for (int i = 0; i < 500; i++) {
            enqueued.add(enqueue(restaurantId));
        }

        assertEquals(enqueued, queue.view(restaurantId));
        for (Order expected : enqueued) {
            assertEquals(expected.getId(), queue.startNext(restaurantId, ACTOR).getId());
        }
        assertTrue(queue.view(restaurantId).isEmpty());
    }

    @Test
    void emptyQueueThrows() {
        assertThrows(EmptyPreparationQueueException.class, () -> queue.startNext(restaurantId, ACTOR));

        enqueue(restaurantId);
        queue.startNext(restaurantId, ACTOR);
        assertThrows(EmptyPreparationQueueException.class, () -> queue.startNext(restaurantId, ACTOR));
    }

    @Test
    void onlyAcceptedOrdersCanBeEnqueued() {
        Order placed = orders.save(order(restaurantId, NOW));
        assertThrows(IllegalArgumentException.class, () -> queue.enqueueAccepted(placed));

        Order rejected = order(restaurantId, NOW);
        rejected.reject("Out of stock");
        assertThrows(IllegalArgumentException.class, () -> queue.enqueueAccepted(rejected));

        assertThrows(NullPointerException.class, () -> queue.enqueueAccepted(null));
        assertTrue(queue.view(restaurantId).isEmpty());
    }

    @Test
    void sameOrderCannotBeEnqueuedTwice() {
        Order order = enqueue(restaurantId);

        assertThrows(IllegalStateException.class, () -> queue.enqueueAccepted(order));
        assertEquals(List.of(order), queue.view(restaurantId));
    }

    @Test
    void concurrentStartRequestsNeverStartTheSameOrderTwice() throws Exception {
        PreparationQueue liveQueue = new PreparationQueue(orders);
        int count = 50;
        Set<UUID> expected = new HashSet<>();
        for (int i = 0; i < count; i++) {
            expected.add(liveQueue.enqueueAccepted(accepted(restaurantId, NOW)).getId());
        }

        ExecutorService pool = Executors.newFixedThreadPool(8);
        CountDownLatch go = new CountDownLatch(1);
        List<Callable<UUID>> tasks = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            tasks.add(() -> {
                go.await();
                return liveQueue.startNext(restaurantId, ACTOR).getId();
            });
        }
        List<Future<UUID>> futures = new ArrayList<>();
        for (Callable<UUID> task : tasks) {
            futures.add(pool.submit(task));
        }
        go.countDown();
        Set<UUID> started = new HashSet<>();
        for (Future<UUID> future : futures) {
            started.add(future.get());
        }
        pool.shutdown();

        assertEquals(expected, started);
        assertTrue(liveQueue.view(restaurantId).isEmpty());
    }

    private Order enqueue(UUID restaurant) {
        return queue.enqueueAccepted(accepted(restaurant, NOW));
    }

    private Order accepted(UUID restaurant, Instant createdAt) {
        Order order = order(restaurant, createdAt);
        order.accept();
        return order;
    }

    private Order order(UUID restaurant, Instant createdAt) {
        return new Order(UUID.randomUUID(), UUID.randomUUID(), restaurant,
                List.of(new OrderLine(UUID.randomUUID(), 1, new BigDecimal("7.50"))), createdAt);
    }
}
