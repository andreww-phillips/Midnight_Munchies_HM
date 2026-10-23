package com.midnightmunchies.application;

import com.midnightmunchies.domain.ord.IllegalOrderTransitionException;
import com.midnightmunchies.domain.ord.Order;
import com.midnightmunchies.domain.ord.OrderLine;
import com.midnightmunchies.domain.ord.OrderStatus;
import com.midnightmunchies.domain.ord.Restaurant;
import com.midnightmunchies.domain.ord.RestaurantStatus;
import com.midnightmunchies.domain.reg.UserAccount;
import com.midnightmunchies.domain.reg.UserRole;
import com.midnightmunchies.technicalservices.ord.repository.InMemoryMenuItemRepository;
import com.midnightmunchies.technicalservices.ord.repository.InMemoryOrderRepository;
import com.midnightmunchies.technicalservices.ord.repository.InMemoryRestaurantRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.mock.web.MockHttpSession;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class MarkReadyAndCancelServiceTests {

    private final InMemoryRestaurantRepository restaurants = new InMemoryRestaurantRepository();
    private final InMemoryOrderRepository orders = new InMemoryOrderRepository();
    private final PreparationQueue queue = new PreparationQueue(orders);
    private final AuthorizedOrdService service = new AuthorizedOrdService(
            new RealOrdService(restaurants, new InMemoryMenuItemRepository(), orders, queue), restaurants);

    private final UUID ownerId = UUID.randomUUID();
    private final UUID restaurantId = UUID.randomUUID();
    private final UUID otherRestaurantId = UUID.randomUUID();
    private final UUID customerId = UUID.randomUUID();
    private final MockHttpSession owner = session(ownerId, UserRole.RESTAURANT_STAFF);
    private final MockHttpSession customer = session(customerId, UserRole.CUSTOMER);
    private final MockHttpSession operator = session(UUID.randomUUID(), UserRole.PLATFORM_OPERATOR);

    {
        restaurants.save(new Restaurant(restaurantId, ownerId, "Diner", RestaurantStatus.OPEN, 0, 0));
        restaurants.save(new Restaurant(otherRestaurantId, UUID.randomUUID(), "Other", RestaurantStatus.OPEN, 0, 0));
    }

    @Test
    void ownerMarksPreparingOrderReady() {
        Order order = preparing(restaurantId);

        Order ready = service.markOrderReady(restaurantId, order.getId(), owner);

        assertEquals(OrderStatus.READY, ready.getStatus());
    }

    @Test
    void onlyPreparingOrdersCanBeMarkedReady() {
        Order placed = placed(restaurantId, customerId);
        Order accepted = accepted(restaurantId);
        Order ready = preparing(restaurantId);
        service.markOrderReady(restaurantId, ready.getId(), owner);

        assertThrows(IllegalOrderTransitionException.class, () -> service.markOrderReady(restaurantId, placed.getId(), owner));
        assertThrows(IllegalOrderTransitionException.class, () -> service.markOrderReady(restaurantId, accepted.getId(), owner));
        assertThrows(IllegalOrderTransitionException.class, () -> service.markOrderReady(restaurantId, ready.getId(), owner));
        assertEquals(OrderStatus.PLACED, placed.getStatus());
        assertEquals(OrderStatus.ACCEPTED, accepted.getStatus());
    }

    @Test
    void pausedRestaurantCanStillMarkOrdersReady() {
        Order order = preparing(restaurantId);
        restaurants.save(new Restaurant(restaurantId, ownerId, "Diner", RestaurantStatus.PAUSED, 0, 0));

        assertEquals(OrderStatus.READY, service.markOrderReady(restaurantId, order.getId(), owner).getStatus());
    }

    @Test
    void markReadyIsDeniedToOtherStaffAndOtherRoles() {
        Order order = preparing(restaurantId);
        MockHttpSession otherStaff = session(UUID.randomUUID(), UserRole.RESTAURANT_STAFF);

        assertThrows(AuthorizationException.class, () -> service.markOrderReady(restaurantId, order.getId(), otherStaff));
        assertThrows(AuthorizationException.class, () -> service.markOrderReady(restaurantId, order.getId(), customer));
        assertThrows(AuthorizationException.class, () -> service.markOrderReady(restaurantId, order.getId(), operator));
        assertThrows(AuthenticationRequiredException.class,
                () -> service.markOrderReady(restaurantId, order.getId(), new MockHttpSession()));
        assertEquals(OrderStatus.PREPARING, order.getStatus());
    }

    @Test
    void markReadyReportsMissingOrOtherRestaurantsOrdersAsNotFound() {
        Order otherRestaurantOrder = preparing(otherRestaurantId);

        assertThrows(OrderNotFoundException.class,
                () -> service.markOrderReady(restaurantId, otherRestaurantOrder.getId(), owner));
        assertThrows(OrderNotFoundException.class, () -> service.markOrderReady(restaurantId, UUID.randomUUID(), owner));
        assertEquals(OrderStatus.PREPARING, otherRestaurantOrder.getStatus());
    }

    @Test
    void customerCancelsOwnPlacedOrder() {
        Order order = placed(restaurantId, customerId);

        assertEquals(OrderStatus.CANCELLED, service.cancelOrder(order.getId(), customer).getStatus());
    }

    @Test
    void customerCannotCancelOwnOrderAfterItIsPlaced() {
        Order accepted = accepted(restaurantId, customerId);
        Order preparing = preparing(restaurantId, customerId);

        assertThrows(IllegalOrderTransitionException.class, () -> service.cancelOrder(accepted.getId(), customer));
        assertThrows(IllegalOrderTransitionException.class, () -> service.cancelOrder(preparing.getId(), customer));
        assertEquals(OrderStatus.ACCEPTED, accepted.getStatus());
        assertEquals(OrderStatus.PREPARING, preparing.getStatus());
        assertEquals(List.of(accepted), queue.view(restaurantId));
    }

    @Test
    void customerCannotSeeOrCancelSomeoneElsesOrder() {
        Order someoneElses = placed(restaurantId, UUID.randomUUID());

        OrderNotFoundException error = assertThrows(OrderNotFoundException.class,
                () -> service.cancelOrder(someoneElses.getId(), customer));
        assertEquals("Order does not exist", error.getMessage());
        assertEquals(OrderStatus.PLACED, someoneElses.getStatus());
    }

    @Test
    void operatorCancelsPlacedAcceptedAndPreparingOrders() {
        Order placed = placed(restaurantId, customerId);
        Order accepted = accepted(restaurantId);
        Order preparing = preparing(restaurantId);

        assertEquals(OrderStatus.CANCELLED, service.cancelOrder(placed.getId(), operator).getStatus());
        assertEquals(OrderStatus.CANCELLED, service.cancelOrder(accepted.getId(), operator).getStatus());
        assertEquals(OrderStatus.CANCELLED, service.cancelOrder(preparing.getId(), operator).getStatus());
    }

    @Test
    void operatorCannotCancelReadyOrders() {
        Order ready = preparing(restaurantId);
        service.markOrderReady(restaurantId, ready.getId(), owner);

        assertThrows(IllegalOrderTransitionException.class, () -> service.cancelOrder(ready.getId(), operator));
        assertEquals(OrderStatus.READY, ready.getStatus());
    }

    @Test
    void operatorCancellationUpdatesThePreparationQueue() {
        Order a = accepted(restaurantId);
        Order b = accepted(restaurantId);
        Order c = accepted(restaurantId);

        service.cancelOrder(a.getId(), operator);

        assertEquals(List.of(b, c), service.viewPreparationQueue(restaurantId, owner));
        assertEquals(b.getId(), service.startNextPreparation(restaurantId, owner).getId());
    }

    @ParameterizedTest
    @EnumSource(value = UserRole.class, names = {"RESTAURANT_STAFF", "COURIER"})
    void staffAndCouriersCannotCancel(UserRole role) {
        Order order = placed(restaurantId, customerId);
        MockHttpSession session = session(role == UserRole.RESTAURANT_STAFF ? ownerId : UUID.randomUUID(), role);

        assertThrows(AuthorizationException.class, () -> service.cancelOrder(order.getId(), session));
        assertEquals(OrderStatus.PLACED, order.getStatus());
    }

    @Test
    void cancelRequiresLoginAndAnExistingOrder() {
        Order order = placed(restaurantId, customerId);

        assertThrows(AuthenticationRequiredException.class, () -> service.cancelOrder(order.getId(), new MockHttpSession()));
        assertThrows(OrderNotFoundException.class, () -> service.cancelOrder(UUID.randomUUID(), operator));
        assertThrows(IllegalArgumentException.class, () -> service.cancelOrder(null, operator));
    }

    private Order placed(UUID restaurant, UUID customer) {
        return orders.save(new Order(UUID.randomUUID(), customer, restaurant,
                List.of(new OrderLine(UUID.randomUUID(), 1, BigDecimal.TEN)), Instant.now()));
    }

    private Order accepted(UUID restaurant) {
        return accepted(restaurant, UUID.randomUUID());
    }

    private Order accepted(UUID restaurant, UUID customer) {
        Order order = placed(restaurant, customer);
        order.accept();
        return queue.enqueueAccepted(order);
    }

    private Order preparing(UUID restaurant) {
        return preparing(restaurant, UUID.randomUUID());
    }

    private Order preparing(UUID restaurant, UUID customer) {
        Order order = accepted(restaurant, customer);
        order.startPreparing();
        return orders.save(order);
    }

    private static MockHttpSession session(UUID userId, UserRole role) {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("authenticatedUser",
                new UserAccount(userId.toString(), "user@example.com", "hash", role));
        return session;
    }
}
