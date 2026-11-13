package com.midnightmunchies.application.dlv;

import com.midnightmunchies.domain.dlv.AssignmentStatus;
import com.midnightmunchies.domain.ord.Actor;
import com.midnightmunchies.domain.ord.Order;
import com.midnightmunchies.domain.ord.OrderEvent;
import com.midnightmunchies.domain.ord.OrderLine;
import com.midnightmunchies.domain.ord.OrderRepository;
import com.midnightmunchies.domain.ord.OrderStatus;
import com.midnightmunchies.domain.reg.UserAccount;
import com.midnightmunchies.domain.reg.UserRole;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
class CourierDispatchIntegrationTests {
    @Autowired
    private CourierService courierService;

    @Autowired
    private DispatchQueueService dispatchQueue;

    @Autowired
    private OrderRepository orders;

    @Autowired
    private com.midnightmunchies.application.ord.OrderEventPublisher events;

    @BeforeEach
    void clearQueue() {
        dispatchQueue.view().forEach(dispatchQueue::remove);
    }

    @Test
    void readyOrderQueuedByPublishedEventIsOfferedWhenCourierComesOnDuty() {
        Order first = readyOrder();
        Order second = readyOrder();
        events.publish(readyEvent(first));
        events.publish(readyEvent(second));

        assertEquals(List.of(first.getId(), second.getId()), dispatchQueue.view());

        UserAccount courier = courierAccount();
        courierService.setDuty(courier, true);

        assertEquals(List.of(second.getId()), dispatchQueue.view());
        CourierService.AssignmentView offer = courierService.view(courier).assignment();
        assertEquals(first.getId(), offer.order().getId());
        assertEquals(AssignmentStatus.OFFERED, offer.assignment().status());
        assertEquals(OrderStatus.READY, orders.findById(first.getId()).orElseThrow().getStatus());
        assertTrue(dispatchQueue.view().stream().noneMatch(first.getId()::equals));
    }

    private Order readyOrder() {
        Order order = new Order(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                List.of(new OrderLine(UUID.randomUUID(), "Meal", 1, new BigDecimal("8.50"))), Instant.now());
        order.accept();
        order.startPreparing();
        order.markReady();
        order.pullTransitions();
        return orders.save(order);
    }

    private OrderEvent readyEvent(Order order) {
        return OrderEvent.of(UUID.randomUUID(), order.getId(), order.getRestaurantId(),
                OrderStatus.PREPARING, OrderStatus.READY, Instant.now(),
                new Actor(UUID.randomUUID(), UserRole.RESTAURANT_STAFF));
    }

    private UserAccount courierAccount() {
        return new UserAccount(UUID.randomUUID().toString(), "courier@example.test", "hash", UserRole.COURIER);
    }
}
