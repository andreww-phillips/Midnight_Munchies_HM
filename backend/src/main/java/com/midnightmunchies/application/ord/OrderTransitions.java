package com.midnightmunchies.application.ord;

import com.midnightmunchies.domain.ord.Actor;
import com.midnightmunchies.domain.ord.Order;
import com.midnightmunchies.domain.ord.OrderEvent;
import com.midnightmunchies.domain.ord.OrderTransition;
import com.midnightmunchies.domain.ord.OrderRepository;
import com.midnightmunchies.domain.ord.OrderStatus;
import com.midnightmunchies.domain.reg.UserRole;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Consumer;

@Component
public class OrderTransitions {

    private final OrderRepository orderRepository;
    private final OrderEventPublisher publisher;
    private final Clock clock;

    @Autowired
    public OrderTransitions(OrderRepository orderRepository, OrderEventPublisher publisher) {
        this(orderRepository, publisher, Clock.systemUTC());
    }

    public OrderTransitions(OrderRepository orderRepository, OrderEventPublisher publisher, Clock clock) {
        this.orderRepository = Objects.requireNonNull(orderRepository, "orderRepository must not be null");
        this.publisher = Objects.requireNonNull(publisher, "publisher must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    public Order save(Order order, Actor actor) {
        Objects.requireNonNull(order, "order must not be null");
        Objects.requireNonNull(actor, "actor must not be null");
        Order saved = orderRepository.save(order);
        List<OrderEvent> events = new ArrayList<>();
        for (OrderTransition transition : saved.pullTransitions()) {
            events.add(OrderEvent.of(UUID.randomUUID(), saved.getId(), saved.getRestaurantId(),
                    transition.from(), transition.to(), clock.instant(), actor));
        }
        events.forEach(publisher::publish);
        return saved;
    }

    /**
     * Stages the refund on a replacement so a failed coordinated commit cannot mutate
     * the stored Order reference. The caller serializes billing updates and publishes
     * the returned event only after commit. No independent refund HTTP route exists in ORD.
     */
    public OrderEvent refund(UUID orderId, Actor actor, Instant at, Consumer<OrderEvent> commitBilling) {
        Objects.requireNonNull(actor, "actor must not be null");
        Objects.requireNonNull(at, "at must not be null");
        Objects.requireNonNull(commitBilling, "commitBilling must not be null");
        if (actor.role() != UserRole.PLATFORM_OPERATOR) {
            throw new AuthorizationException("Only platform operators can refund orders");
        }
        Order original = orderRepository.findById(orderId)
                .orElseThrow(() -> new OrderNotFoundException("Order does not exist"));
        if (original.hasPendingTransitions()) {
            throw new IllegalStateException("Order has unrecorded transitions");
        }
        Order replacement = Order.restore(original.getId(), original.getCustomerId(), original.getRestaurantId(),
                original.getOrderLines(), original.getCreatedAt(), original.getStatus(), original.getAcceptedAt());
        replacement.refund();
        replacement.pullTransitions();
        OrderEvent event = OrderEvent.of(UUID.randomUUID(), orderId, original.getRestaurantId(),
                original.getStatus(), OrderStatus.REFUNDED, at, actor);
        try {
            orderRepository.save(replacement);
            commitBilling.accept(event);
        } catch (RuntimeException failure) {
            try {
                orderRepository.save(original);
            } catch (RuntimeException rollbackFailure) {
                failure.addSuppressed(rollbackFailure);
            }
            throw failure;
        }
        return event;
    }
}
