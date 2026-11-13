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
import com.midnightmunchies.domain.ord.Restaurant;
import com.midnightmunchies.domain.ord.RestaurantStatus;
import com.midnightmunchies.domain.reg.UserAccount;
import com.midnightmunchies.domain.reg.UserRole;
import com.midnightmunchies.technicalservices.AssignmentRepository;
import com.midnightmunchies.technicalservices.CourierRepository;
import com.midnightmunchies.technicalservices.InMemoryOrderRepository;
import com.midnightmunchies.technicalservices.InMemoryRestaurantRepository;
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
    private InMemoryRestaurantRepository restaurants;
    private CourierService service;

    @BeforeEach
    void setUp() {
        orders = new InMemoryOrderRepository();
        couriers = new CourierRepository();
        assignments = new AssignmentRepository();
        queue = new DispatchQueueService(tempDir.resolve("dispatch-queue.txt"));
        restaurants = new InMemoryRestaurantRepository();
        service = new CourierService(couriers, assignments, orders, restaurants, queue,
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
        assertEquals(List.of(order.getId()), queue.view());

        service.accept(courier, order.getId());
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

        CourierService.ReassignmentView result = service.reassign(operator, order.getId());

        assertFalse(result.queued());
        assertEquals(id(second), result.replacement().courierId());
        assertEquals(AssignmentStatus.OFFERED, result.replacement().status());
        assertEquals(List.of(order.getId()), queue.view());
        assertEquals(AssignmentStatus.REASSIGNED, result.previous().status());
        assertEquals(operator.id(), result.previous().releasedBy());
        assertEquals(CourierState.OFFLINE, service.view(first).state());
        assertEquals(CourierState.IDLE, service.view(second).state());
        assertEquals(OrderStatus.READY, orders.findById(order.getId()).orElseThrow().getStatus());
    }

    @Test
    void reassignmentOffersTheOrderToTheNearestOtherIdleCourier() {
        Restaurant restaurant = restaurants.save(new Restaurant(UUID.randomUUID(), "Reassign " + UUID.randomUUID(),
                RestaurantStatus.OPEN, 45.50, -73.57));
        UserAccount stalled = account(UserRole.COURIER);
        couriers.save(new Courier(id(stalled), new Location(45.50, -73.57)));
        service.setDuty(stalled, true);
        Order order = order(true, restaurant.getId());
        service.onEvent(event(order, OrderStatus.PREPARING, OrderStatus.READY));
        service.accept(stalled, order.getId());

        UserAccount far = account(UserRole.COURIER);
        UserAccount near = account(UserRole.COURIER);
        couriers.save(new Courier(id(far), new Location(45.60, -73.70)));
        couriers.save(new Courier(id(near), new Location(45.51, -73.58)));
        service.setDuty(far, true);
        service.setDuty(near, true);

        CourierService.ReassignmentView result = service.reassign(account(UserRole.PLATFORM_OPERATOR), order.getId());

        assertEquals(id(near), result.replacement().courierId());
    }

    @Test
    void reassignmentQueuesTheOrderWhenNoOtherCourierIsIdle() {
        UserAccount stalled = account(UserRole.COURIER);
        service.setDuty(stalled, true);
        Order order = order(true);
        service.onEvent(event(order, OrderStatus.PREPARING, OrderStatus.READY));

        CourierService.ReassignmentView result = service.reassign(account(UserRole.PLATFORM_OPERATOR), order.getId());

        assertTrue(result.queued());
        assertEquals(null, result.replacement());
        assertEquals(List.of(order.getId()), queue.view());
        assertEquals(CourierState.OFFLINE, service.view(stalled).state());
        assertTrue(assignments.findActiveByOrderId(order.getId()).isEmpty());

        UserAccount next = account(UserRole.COURIER);
        service.setDuty(next, true);
        assertEquals(id(next), assignments.findActiveByOrderId(order.getId()).orElseThrow().courierId());
        assertEquals(List.of(order.getId()), queue.view());
        service.accept(next, order.getId());
        assertTrue(queue.view().isEmpty());
    }

    @Test
    void onlyOperatorsCanReassignAndPickedUpOrdersCannotBeReassigned() {
        UserAccount courier = account(UserRole.COURIER);
        service.setDuty(courier, true);
        Order order = order(true);
        service.onEvent(event(order, OrderStatus.PREPARING, OrderStatus.READY));
        service.accept(courier, order.getId());

        assertThrows(com.midnightmunchies.application.ord.AuthorizationException.class,
                () -> service.reassign(courier, order.getId()));
        assertThrows(com.midnightmunchies.application.ord.AuthorizationException.class,
                () -> service.reassign(account(UserRole.RESTAURANT_STAFF), order.getId()));
        assertThrows(com.midnightmunchies.application.reg.AuthenticationRequiredException.class,
                () -> service.reassign(null, order.getId()));

        service.pickUp(courier, order.getId());
        assertThrows(DeliveryConflictException.class,
                () -> service.reassign(account(UserRole.PLATFORM_OPERATOR), order.getId()));
        assertEquals(CourierState.CARRYING, service.view(courier).state());
    }

    @Test
    void reassigningAnOrderWithoutActiveAssignmentIsNotFound() {
        Order order = order(true);

        assertThrows(DeliveryNotFoundException.class,
                () -> service.reassign(account(UserRole.PLATFORM_OPERATOR), order.getId()));
    }

    @Test
    void operatorSeesActiveDeliveriesAndQueuedOrders() {
        UserAccount courier = account(UserRole.COURIER);
        service.setDuty(courier, true);
        Order offered = order(true);
        Order waiting = order(true);
        service.onEvent(event(offered, OrderStatus.PREPARING, OrderStatus.READY));
        service.onEvent(event(waiting, OrderStatus.PREPARING, OrderStatus.READY));

        CourierService.OperatorDeliveriesView view = service.operatorDeliveries(account(UserRole.PLATFORM_OPERATOR));

        assertEquals(1, view.active().size());
        CourierService.ActiveDeliveryView row = view.active().get(0);
        assertEquals(offered.getId(), row.orderId());
        assertEquals(id(courier), row.courierId());
        assertEquals(AssignmentStatus.OFFERED, row.status());
        assertTrue(row.reassignable());
        assertTrue(row.waitingSeconds() >= 0);
        assertEquals(List.of(waiting.getId()), view.queued());
        assertThrows(com.midnightmunchies.application.ord.AuthorizationException.class,
                () -> service.operatorDeliveries(courier));
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

        assertEquals(List.of(first.getId(), second.getId()), queue.view());
        service.accept(courier, first.getId());
        assertEquals(List.of(second.getId()), queue.view());
        service.pickUp(courier, first.getId());
        service.deliver(courier, first.getId());

        CourierService.AssignmentView next = service.view(courier).assignment();
        assertEquals(second.getId(), next.order().getId());
        assertEquals(AssignmentStatus.OFFERED, next.assignment().status());
        assertEquals(List.of(second.getId()), queue.view());
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
        assertEquals(List.of(order.getId()), queue.view());
        assertEquals(CourierState.IDLE, service.view(declining).state());
        service.accept(nextCourier, order.getId());
        assertTrue(queue.view().isEmpty());
    }

    @Test
    void keepsFifoOrdersQueuedWhileOfferedAndOnlyDequeuesEachOnAcceptance() {
        UserAccount firstCourier = account(UserRole.COURIER);
        UserAccount secondCourier = account(UserRole.COURIER);
        service.setDuty(firstCourier, true);
        service.setDuty(secondCourier, true);
        Order first = order(true);
        Order second = order(true);

        service.onEvent(event(first, OrderStatus.PREPARING, OrderStatus.READY));
        service.onEvent(event(second, OrderStatus.PREPARING, OrderStatus.READY));

        assertEquals(List.of(first.getId(), second.getId()), queue.view());
        assertEquals(id(firstCourier), assignments.findActiveByOrderId(first.getId()).orElseThrow().courierId());
        assertEquals(id(secondCourier), assignments.findActiveByOrderId(second.getId()).orElseThrow().courierId());

        service.accept(firstCourier, first.getId());

        assertEquals(List.of(second.getId()), queue.view());
        service.accept(secondCourier, second.getId());
        assertTrue(queue.view().isEmpty());
    }

    @Test
    void readyOrderIsOfferedToTheNearestIdleCourier() {
        Restaurant restaurant = restaurants.save(new Restaurant(UUID.randomUUID(), "Dispatch " + UUID.randomUUID(),
                RestaurantStatus.OPEN, 45.50, -73.57));
        UserAccount far = account(UserRole.COURIER);
        UserAccount near = account(UserRole.COURIER);
        couriers.save(new Courier(id(far), new Location(45.60, -73.70)));
        couriers.save(new Courier(id(near), new Location(45.51, -73.58)));
        service.setDuty(far, true);
        service.setDuty(near, true);

        Order order = order(true, restaurant.getId());
        service.onEvent(event(order, OrderStatus.PREPARING, OrderStatus.READY));

        assertEquals(id(near), assignments.findActiveByOrderId(order.getId()).orElseThrow().courierId());
    }

    @Test
    void newCouriersStartInsideTheCityInsteadOfAtTheOrigin() {
        UserAccount courier = account(UserRole.COURIER);

        service.setDuty(courier, true);

        Location location = couriers.findById(id(courier)).orElseThrow().getLocation();
        assertTrue(com.midnightmunchies.domain.geo.MontrealServiceArea.contains(location.latitude(), location.longitude()));
        assertEquals(com.midnightmunchies.domain.geo.MontrealServiceArea.placeFor(id(courier)).latitude(), location.latitude());
    }

    private Order order(boolean isReady) {
        return order(isReady, UUID.randomUUID());
    }

    private Order order(boolean isReady, UUID restaurantId) {
        Order order = new Order(UUID.randomUUID(), UUID.randomUUID(), restaurantId,
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
