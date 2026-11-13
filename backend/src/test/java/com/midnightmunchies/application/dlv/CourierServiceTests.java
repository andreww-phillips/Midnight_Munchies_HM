package com.midnightmunchies.application.dlv;

import com.midnightmunchies.application.ord.OrderEventPublisher;
import com.midnightmunchies.application.ord.OrderTransitions;
import com.midnightmunchies.domain.dlv.AssignmentStatus;
import com.midnightmunchies.domain.dlv.Courier;
import com.midnightmunchies.domain.dlv.CourierState;
import com.midnightmunchies.domain.dlv.Location;
import com.midnightmunchies.domain.ord.Actor;
import com.midnightmunchies.domain.ord.Order;
import com.midnightmunchies.domain.ord.OrderEvent;
import com.midnightmunchies.domain.ord.OrderLine;
import com.midnightmunchies.domain.ord.OrderStatus;
import com.midnightmunchies.domain.reg.UserAccount;
import com.midnightmunchies.domain.reg.UserRole;
import com.midnightmunchies.technicalservices.AssignmentRepository;
import com.midnightmunchies.technicalservices.CourierRepository;
import com.midnightmunchies.technicalservices.InMemoryOrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.math.BigDecimal;
import java.time.Instant;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CourierServiceTests {
    @TempDir
    Path tempDir;

    private InMemoryOrderRepository orders;
    private CourierRepository couriers;
    private AssignmentRepository assignments;
    private DispatchQueueService queue;
    private CourierService service;

    @BeforeEach
    void setUp() {
        orders = new InMemoryOrderRepository();
        couriers = new CourierRepository();
        assignments = new AssignmentRepository();
        queue = new DispatchQueueService(tempDir.resolve("dispatch-queue.txt"));
        service = new CourierService(couriers, assignments, orders, queue,
                new OrderTransitions(orders, new OrderEventPublisher()));
    }

    @Test
    void keepsUnreadyOrdersOutOfOffersAndRetriesQueuedReadyOrdersWhenCourierGoesOnDuty() {
        UserAccount courier = account(UserRole.COURIER);
        Courier model = new Courier(id(courier), new Location(0, 0));
        couriers.save(model);
        Order order = order(false);

        service.onEvent(event(order, OrderStatus.PLACED, OrderStatus.ACCEPTED));
        assertTrue(queue.view().isEmpty());
        assertEquals(null, service.view(courier).assignment());

        ready(order);
        service.onEvent(event(order, OrderStatus.PREPARING, OrderStatus.READY));
        assertEquals(List.of(order.getId()), queue.view());
        assertEquals(null, service.view(courier).assignment());

        CourierService.CourierDashboard dashboard = service.setDuty(courier, true);
        assertEquals(CourierState.IDLE, dashboard.state());
        assertEquals(AssignmentStatus.OFFERED, dashboard.assignment().assignment().status());
        assertTrue(queue.view().isEmpty());
    }

    @Test
    void onlyOffersToIdleOnDutyCouriersAndOperatorCanReassignAnAcceptedOrder() {
        UserAccount first = account(UserRole.COURIER);
        UserAccount second = account(UserRole.COURIER);
        UserAccount operator = account(UserRole.PLATFORM_OPERATOR);
        Order order = order(true);
        service.onEvent(event(order, OrderStatus.PREPARING, OrderStatus.READY));

        service.setDuty(first, true);
        CourierService.AssignmentView offered = service.view(first).assignment();
        assertEquals(id(first), offered.assignment().courierId());

        service.accept(first, order.getId());
        service.setDuty(second, true);
        assertThrows(com.midnightmunchies.application.ord.AuthorizationException.class,
                () -> service.accept(second, order.getId()));

        CourierService.AssignmentView reassigned = service.reassign(operator, order.getId(), id(second));
        assertEquals(id(second), reassigned.assignment().courierId());
        assertEquals(AssignmentStatus.OFFERED, reassigned.assignment().status());
        assertEquals(CourierState.IDLE, service.view(first).state());
        assertEquals(CourierState.IDLE, service.view(second).state());
    }

    @Test
    void preventsGoingOfflineWhileAssignedAndCompletesPickupAndDelivery() {
        UserAccount courier = account(UserRole.COURIER);
        Order order = order(true);
        service.setDuty(courier, true);
        service.onEvent(event(order, OrderStatus.PREPARING, OrderStatus.READY));
        service.accept(courier, order.getId());

        service.pickUp(courier, order.getId());
        assertEquals(OrderStatus.PICKED_UP, orders.findById(order.getId()).orElseThrow().getStatus());
        assertThrows(DeliveryConflictException.class, () -> service.setDuty(courier, false));

        CourierService.AssignmentView delivered = service.deliver(courier, order.getId());
        assertEquals(AssignmentStatus.DELIVERED, delivered.assignment().status());
        assertEquals(OrderStatus.DELIVERED, delivered.order().getStatus());
        assertEquals(CourierState.IDLE, service.view(courier).state());
        assertTrue(service.view(courier).onDuty());
    }

    @Test
    void retriesTheDispatchQueueWhenDeliveryMakesCourierIdle() {
        UserAccount courier = account(UserRole.COURIER);
        Order first = order(true);
        Order second = order(true);
        service.setDuty(courier, true);
        service.onEvent(event(first, OrderStatus.PREPARING, OrderStatus.READY));
        service.onEvent(event(second, OrderStatus.PREPARING, OrderStatus.READY));

        assertEquals(List.of(second.getId()), queue.view());
        service.accept(courier, first.getId());
        service.pickUp(courier, first.getId());
        service.deliver(courier, first.getId());

        CourierService.AssignmentView next = service.view(courier).assignment();
        assertEquals(second.getId(), next.order().getId());
        assertEquals(AssignmentStatus.OFFERED, next.assignment().status());
        assertFalse(queue.view().contains(second.getId()));
    }

    @Test
    void declinedOfferIsRetriedForAnotherIdleCourierAndRemainsQueuedOtherwise() {
        UserAccount declining = account(UserRole.COURIER);
        UserAccount nextCourier = account(UserRole.COURIER);
        Order order = order(true);
        service.setDuty(declining, true);
        service.onEvent(event(order, OrderStatus.PREPARING, OrderStatus.READY));
        service.setDuty(nextCourier, true);

        service.decline(declining, order.getId());

        assertEquals(id(nextCourier),
                assignments.findActiveByOrderId(order.getId()).orElseThrow().courierId());
        assertTrue(queue.view().isEmpty());
        assertEquals(CourierState.IDLE, service.view(declining).state());
    }

    private Order order(boolean isReady) {
        Order order = new Order(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                List.of(new OrderLine(UUID.randomUUID(), 1, new BigDecimal("8.50"))), Instant.now());
        if (isReady) {
            ready(order);
        }
        order.pullTransitions();
        return orders.save(order);
    }

    private void ready(Order order) {
        order.accept();
        order.startPreparing();
        order.markReady();
    }

    private OrderEvent event(Order order, OrderStatus from, OrderStatus to) {
        return OrderEvent.of(UUID.randomUUID(), order.getId(), order.getRestaurantId(), from, to,
                Instant.now(), new Actor(UUID.randomUUID(), UserRole.RESTAURANT_STAFF));
    }

    private UserAccount account(UserRole role) {
        return new UserAccount(UUID.randomUUID().toString(), role + "@example.test", "hash", role);
    }

    private UUID id(UserAccount account) {
        return UUID.fromString(account.id());
    }
}
