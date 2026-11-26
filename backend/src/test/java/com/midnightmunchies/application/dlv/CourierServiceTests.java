package com.midnightmunchies.application.dlv;

import com.midnightmunchies.application.ord.AuthorizationException;
import com.midnightmunchies.application.ord.OrderEventPublisher;
import com.midnightmunchies.application.ord.OrderTransitions;
import com.midnightmunchies.application.reg.AccountAuthorization;
import com.midnightmunchies.application.reg.AuthenticationRequiredException;
import com.midnightmunchies.domain.dlv.Assignment;
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
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.math.BigDecimal;
import java.time.Instant;
import java.nio.file.Path;
import java.nio.file.Files;
import java.util.List;
import java.util.Comparator;
import java.util.UUID;
import java.util.Arrays;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertThrowsExactly;
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
                new OrderTransitions(orders, new OrderEventPublisher()), new AccountAuthorization());
    }

    @ParameterizedTest(name = "{0}: missing account is rejected before delivery changes")
    @MethodSource("accountCommands")
    void rejectsMissingAccountsWithoutChangingDeliveryState(
            String name, UserRole requiredRole, String roleMessage, AccountCommand command) throws Exception {
        AuthenticationRequiredException failure = assertRejectedWithoutChangingDeliveryState(
                AuthenticationRequiredException.class, null, command);

        assertEquals("Authentication is required", failure.getMessage());
    }

    @ParameterizedTest(name = "{0}: {1} is refused before parsing the account ID")
    @MethodSource("wrongRoleCommands")
    void rejectsWrongRolesBeforeParsingAccountIdOrChangingDeliveryState(
            String name, UserRole wrongRole, String expectedMessage, AccountCommand command) throws Exception {
        // A role refusal must win over the malformed ID, as it does before R1.
        UserAccount wrongAccount = new UserAccount("not-a-uuid", "wrong@example.test", "hash", wrongRole);

        AuthorizationException failure = assertRejectedWithoutChangingDeliveryState(
                AuthorizationException.class, wrongAccount, command);

        assertEquals(expectedMessage, failure.getMessage());
    }

    @ParameterizedTest(name = "{0}: malformed courier ID is rejected without delivery changes")
    @MethodSource("courierCommands")
    void rejectsMalformedCourierIdsWithoutChangingDeliveryState(
            String name, AccountCommand command) throws Exception {
        UserAccount malformedCourier = new UserAccount("not-a-uuid", "courier@example.test", "hash", UserRole.COURIER);

        IllegalArgumentException failure = assertRejectedWithoutChangingDeliveryState(
                IllegalArgumentException.class, malformedCourier, command);

        assertEquals("Courier account has an invalid ID", failure.getMessage());
    }

    @Test
    void firstDashboardAccessCreatesAnOfflineCourierAtTheExistingStartingLocation() {
        UserAccount account = account(UserRole.COURIER);
        var expectedPlace = com.midnightmunchies.domain.geo.MontrealServiceArea.placeFor(id(account));
        assertTrue(couriers.findAll().isEmpty());

        CourierService.CourierDashboard dashboard = service.view(account);

        Courier stored = couriers.findById(id(account)).orElseThrow();
        assertEquals(1, couriers.findAll().size());
        assertEquals(CourierState.OFFLINE, dashboard.state());
        assertFalse(dashboard.onDuty());
        assertNull(dashboard.assignment());
        assertEquals(CourierState.OFFLINE, stored.getState());
        assertTrue(stored.getAssignedOrder().isEmpty());
        assertEquals(new Location(expectedPlace.latitude(), expectedPlace.longitude()), stored.getLocation());
        assertTrue(assignments.findActive().isEmpty());
        assertTrue(queue.view().isEmpty());
        assertTrue(orders.findAll().isEmpty());
    }

    @Test
    void repeatedDashboardAccessPreservesCourierLocationDutyAndExistingOffer() {
        UserAccount account = account(UserRole.COURIER);
        service.setDuty(account, true);
        Courier existing = couriers.findById(id(account)).orElseThrow();
        Location updatedLocation = new Location(45.51, -73.58);
        existing.updateLocation(updatedLocation);
        Order ready = order(true);
        service.onEvent(event(ready, OrderStatus.PREPARING, OrderStatus.READY));
        Assignment offered = assignments.findActiveByOrderId(ready.getId()).orElseThrow();

        CourierService.CourierDashboard first = service.view(account);
        CourierService.CourierDashboard second = service.view(account);

        assertEquals(first, second);
        assertSame(existing, couriers.findById(id(account)).orElseThrow());
        assertEquals(1, couriers.findAll().size());
        assertEquals(updatedLocation, existing.getLocation());
        assertTrue(second.onDuty());
        assertEquals(CourierState.IDLE, second.state());
        assertEquals(offered, second.assignment().assignment());
        assertEquals(List.of(offered), assignments.findActive());
        assertEquals(List.of(ready.getId()), queue.view());
        assertEquals(OrderStatus.READY, ready.getStatus());
    }

    @Test
    void operatorDashboardAccessDoesNotCreateACourier() {
        CourierService.OperatorDeliveriesView dashboard = service.operatorDeliveries(account(UserRole.PLATFORM_OPERATOR));

        assertTrue(dashboard.active().isEmpty());
        assertTrue(dashboard.queued().isEmpty());
        assertTrue(couriers.findAll().isEmpty());
        assertTrue(assignments.findActive().isEmpty());
        assertTrue(orders.findAll().isEmpty());
    }

    @Test
    void operatorAccountIdRemainsAStringDuringDashboardAccessAndReassignment() {
        UserAccount courier = account(UserRole.COURIER);
        service.setDuty(courier, true);
        Order ready = order(true);
        service.onEvent(event(ready, OrderStatus.PREPARING, OrderStatus.READY));
        service.accept(courier, ready.getId());
        UserAccount operator = new UserAccount("operator-id-is-not-a-uuid", "operator@example.test",
                "hash", UserRole.PLATFORM_OPERATOR);

        assertEquals(1, service.operatorDeliveries(operator).active().size());
        CourierService.ReassignmentView result = service.reassign(operator, ready.getId());

        assertEquals(operator.id(), result.previous().releasedBy());
        assertEquals(AssignmentStatus.REASSIGNED, result.previous().status());
        assertTrue(result.queued());
        assertNull(result.replacement());
        assertEquals(CourierState.OFFLINE, couriers.findById(id(courier)).orElseThrow().getState());
        assertEquals(1, couriers.findAll().size());
        assertEquals(List.of(ready.getId()), queue.view());
    }

    private static Stream<Arguments> courierCommands() {
        return Stream.of(
                Arguments.of("view", (AccountCommand) (service, account, orderId) -> service.view(account)),
                Arguments.of("setDuty(true)", (AccountCommand) (service, account, orderId) -> service.setDuty(account, true)),
                Arguments.of("setDuty(false)", (AccountCommand) (service, account, orderId) -> service.setDuty(account, false)),
                Arguments.of("accept", (AccountCommand) CourierService::accept),
                Arguments.of("decline", (AccountCommand) CourierService::decline),
                Arguments.of("pickUp", (AccountCommand) CourierService::pickUp),
                Arguments.of("deliver", (AccountCommand) CourierService::deliver));
    }

    private static Stream<Arguments> accountCommands() {
        Stream<Arguments> courier = courierCommands().map(arguments -> Arguments.of(
                arguments.get()[0], UserRole.COURIER, "Only couriers can access courier delivery commands",
                arguments.get()[1]));
        Stream<Arguments> operator = Stream.of(
                Arguments.of("operatorDeliveries", UserRole.PLATFORM_OPERATOR,
                        "Only platform operators can manage deliveries",
                        (AccountCommand) (service, account, orderId) -> service.operatorDeliveries(account)),
                Arguments.of("reassign", UserRole.PLATFORM_OPERATOR,
                        "Only platform operators can manage deliveries", (AccountCommand) CourierService::reassign));
        return Stream.concat(courier, operator);
    }

    private static Stream<Arguments> wrongRoleCommands() {
        return accountCommands().flatMap(arguments -> Arrays.stream(UserRole.values())
                .filter(role -> role != arguments.get()[1])
                .map(role -> Arguments.of(arguments.get()[0], role, arguments.get()[2], arguments.get()[3])));
    }

    /** Check refusals against populated state so clearing or replacing delivery data cannot pass unnoticed. */
    private <T extends Throwable> T assertRejectedWithoutChangingDeliveryState(
            Class<T> failureType, UserAccount rejectedAccount, AccountCommand command) throws Exception {
        UserAccount owner = account(UserRole.COURIER);
        service.setDuty(owner, true);
        Courier existing = couriers.findById(id(owner)).orElseThrow();
        Location originalLocation = existing.getLocation();
        Order ready = order(true);
        service.onEvent(event(ready, OrderStatus.PREPARING, OrderStatus.READY));
        Assignment offered = assignments.findActiveByOrderId(ready.getId()).orElseThrow();
        Path queueFile = tempDir.resolve("dispatch-queue.txt");
        String persistedQueue = Files.readString(queueFile);
        Executable operation = () -> command.run(service, rejectedAccount, ready.getId());

        T failure = assertThrowsExactly(failureType, operation);

        assertEquals(List.of(existing), couriers.findAll());
        assertSame(existing, couriers.findById(id(owner)).orElseThrow());
        assertEquals(originalLocation, existing.getLocation());
        assertEquals(CourierState.IDLE, existing.getState());
        assertTrue(existing.isOnDuty());
        assertTrue(existing.getAssignedOrder().isEmpty());
        assertEquals(List.of(offered), assignments.findActive());
        assertEquals(offered, assignments.findById(offered.id()).orElseThrow());
        assertEquals(List.of(ready.getId()), queue.view());
        assertEquals(persistedQueue, Files.readString(queueFile));
        assertEquals(List.of(ready), orders.findAll());
        assertEquals(OrderStatus.READY, ready.getStatus());
        return failure;
    }

    @FunctionalInterface
    private interface AccountCommand {
        void run(CourierService service, UserAccount account, UUID orderId);
    }

    @Test
    void failedQueueResetKeepsCourierAndAssignmentState() throws Exception {
        UserAccount courier = account(UserRole.COURIER);
        Order ready = order(true);
        service.setDuty(courier, true);
        service.onEvent(event(ready, OrderStatus.PREPARING, OrderStatus.READY));
        UUID assignmentId = service.view(courier).assignment().assignment().id();
        Files.createDirectory(tempDir.resolve("dispatch-queue.txt.tmp"));

        assertThrows(IllegalStateException.class, service::resetToInitialState);
        assertTrue(couriers.findById(id(courier)).isPresent());
        assertEquals(assignmentId, assignments.findActiveByOrderId(ready.getId()).orElseThrow().id());
        assertEquals(List.of(ready.getId()), queue.view());
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
        assertEquals(List.of(offered.getId(), waiting.getId()), view.queued());
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
        // These orders have no catalog restaurant, so dispatch falls back to the lowest courier ID.
        List<UserAccount> candidates = List.of(account(UserRole.COURIER), account(UserRole.COURIER)).stream()
                .sorted(Comparator.comparing(UserAccount::id)).toList();
        UserAccount firstCourier = candidates.get(0);
        UserAccount secondCourier = candidates.get(1);
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
