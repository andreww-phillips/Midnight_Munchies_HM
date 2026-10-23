package com.midnightmunchies.application;

import com.midnightmunchies.domain.ord.Order;
import com.midnightmunchies.domain.ord.OrderStatus;
import com.midnightmunchies.domain.ord.repository.OrderRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class PreparationQueue {

    private final OrderRepository orderRepository;
    private final Clock clock;
    private final Map<UUID, Object> locks = new ConcurrentHashMap<>();

    @Autowired
    public PreparationQueue(OrderRepository orderRepository) {
        this(orderRepository, Clock.systemUTC());
    }

    public PreparationQueue(OrderRepository orderRepository, Clock clock) {
        this.orderRepository = Objects.requireNonNull(orderRepository, "orderRepository must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    public Order enqueueAccepted(Order order) {
        Objects.requireNonNull(order, "order must not be null");
        if (order.getStatus() != OrderStatus.ACCEPTED) {
            throw new IllegalArgumentException("Only ACCEPTED orders can join the preparation queue");
        }
        synchronized (lockFor(order.getRestaurantId())) {
            order.recordAcceptedAt(nextAcceptedAt(order.getRestaurantId()));
            return orderRepository.save(order);
        }
    }

    public Order startNext(UUID restaurantId) {
        Objects.requireNonNull(restaurantId, "restaurantId must not be null");
        synchronized (lockFor(restaurantId)) {
            List<Order> queue = orderRepository.findPreparationQueue(restaurantId);
            if (queue.isEmpty()) {
                throw new EmptyPreparationQueueException("No accepted orders are waiting to be prepared");
            }
            Order next = queue.get(0);
            next.startPreparing();
            return orderRepository.save(next);
        }
    }

    public Order markReady(Order order) {
        Objects.requireNonNull(order, "order must not be null");
        synchronized (lockFor(order.getRestaurantId())) {
            Order current = reload(order);
            current.markReady();
            return orderRepository.save(current);
        }
    }

    public Order cancel(Order order) {
        Objects.requireNonNull(order, "order must not be null");
        synchronized (lockFor(order.getRestaurantId())) {
            Order current = reload(order);
            current.cancel();
            return orderRepository.save(current);
        }
    }

    public List<Order> view(UUID restaurantId) {
        Objects.requireNonNull(restaurantId, "restaurantId must not be null");
        return orderRepository.findPreparationQueue(restaurantId);
    }

    private Order reload(Order order) {
        return orderRepository.findById(order.getId())
                .orElseThrow(() -> new OrderNotFoundException("Order does not exist"));
    }

    private Object lockFor(UUID restaurantId) {
        return locks.computeIfAbsent(restaurantId, id -> new Object());
    }

    private Instant nextAcceptedAt(UUID restaurantId) {
        Instant now = clock.instant().truncatedTo(ChronoUnit.MICROS);
        List<Order> queue = orderRepository.findPreparationQueue(restaurantId);
        if (queue.isEmpty()) {
            return now;
        }
        Instant last = queue.get(queue.size() - 1).getAcceptedAt();
        return now.isAfter(last) ? now : last.plus(1, ChronoUnit.MICROS);
    }
}
