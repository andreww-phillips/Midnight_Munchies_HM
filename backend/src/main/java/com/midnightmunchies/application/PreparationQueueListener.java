package com.midnightmunchies.application;

import com.midnightmunchies.domain.ord.Order;
import com.midnightmunchies.domain.ord.OrderEvent;
import com.midnightmunchies.domain.ord.OrderEventListener;
import com.midnightmunchies.domain.ord.OrderStatus;
import com.midnightmunchies.domain.ord.repository.OrderRepository;
import org.springframework.stereotype.Component;

import java.util.Objects;

@Component
public class PreparationQueueListener implements OrderEventListener {

    private final OrderRepository orderRepository;
    private final PreparationQueue preparationQueue;

    public PreparationQueueListener(OrderRepository orderRepository, PreparationQueue preparationQueue) {
        this.orderRepository = Objects.requireNonNull(orderRepository, "orderRepository must not be null");
        this.preparationQueue = Objects.requireNonNull(preparationQueue, "preparationQueue must not be null");
    }

    @Override
    public void onEvent(OrderEvent event) {
        if (event.toStatus() != OrderStatus.ACCEPTED) {
            return;
        }
        orderRepository.findById(event.orderId())
                .filter(order -> order.getStatus() == OrderStatus.ACCEPTED)
                .filter(order -> order.getAcceptedAt() == null)
                .ifPresent(this::enqueue);
    }

    private void enqueue(Order order) {
        preparationQueue.enqueueAccepted(order);
    }
}
