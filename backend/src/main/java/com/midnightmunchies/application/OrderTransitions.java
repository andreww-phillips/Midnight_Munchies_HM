package com.midnightmunchies.application;

import com.midnightmunchies.domain.ord.Actor;
import com.midnightmunchies.domain.ord.Order;
import com.midnightmunchies.domain.ord.OrderEvent;
import com.midnightmunchies.domain.ord.OrderTransition;
import com.midnightmunchies.domain.ord.repository.OrderRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

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
}
