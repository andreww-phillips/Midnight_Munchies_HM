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
import org.springframework.mock.web.MockHttpSession;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class OrderAcceptRejectServiceTests {

    private final InMemoryRestaurantRepository restaurants = new InMemoryRestaurantRepository();
    private final InMemoryOrderRepository orders = new InMemoryOrderRepository();
    private final AuthorizedOrdService service = new AuthorizedOrdService(
            new RealOrdService(restaurants, new InMemoryMenuItemRepository(), orders, new PreparationQueue(orders)),
            restaurants);

    private final UUID ownerId = UUID.randomUUID();
    private final UUID restaurantId = UUID.randomUUID();
    private final UUID otherRestaurantId = UUID.randomUUID();
    private final MockHttpSession owner = session(ownerId, UserRole.RESTAURANT_STAFF);

    {
        restaurants.save(new Restaurant(restaurantId, ownerId, "Diner", RestaurantStatus.OPEN, 0, 0));
        restaurants.save(new Restaurant(otherRestaurantId, UUID.randomUUID(), "Other", RestaurantStatus.OPEN, 0, 0));
    }

    @Test
    void ownerAcceptsPlacedOrder() {
        Order order = placed(restaurantId);

        Order accepted = service.acceptOrder(restaurantId, order.getId(), owner);

        assertEquals(OrderStatus.ACCEPTED, accepted.getStatus());
        assertEquals(OrderStatus.ACCEPTED, orders.findById(order.getId()).orElseThrow().getStatus());
    }

    @Test
    void ownerRejectsPlacedOrderWithReasonAndItIsTerminal() {
        Order order = placed(restaurantId);

        Order rejected = service.rejectOrder(restaurantId, order.getId(), "Kitchen closed", owner);

        assertEquals(OrderStatus.REJECTED, rejected.getStatus());
        assertEquals("Kitchen closed", orders.findById(order.getId()).orElseThrow().getRejectionReason());
        assertThrows(IllegalOrderTransitionException.class, () -> service.acceptOrder(restaurantId, order.getId(), owner));
        assertThrows(IllegalOrderTransitionException.class,
                () -> service.rejectOrder(restaurantId, order.getId(), "Again", owner));
        assertEquals(OrderStatus.REJECTED, orders.findById(order.getId()).orElseThrow().getStatus());
    }

    @Test
    void rejectionWithoutReasonIsRefusedAndOrderStaysPlaced() {
        Order order = placed(restaurantId);

        assertThrows(IllegalArgumentException.class, () -> service.rejectOrder(restaurantId, order.getId(), null, owner));
        assertThrows(IllegalArgumentException.class, () -> service.rejectOrder(restaurantId, order.getId(), " ", owner));

        Order stored = orders.findById(order.getId()).orElseThrow();
        assertEquals(OrderStatus.PLACED, stored.getStatus());
        assertNull(stored.getRejectionReason());
    }

    @Test
    void onlyPlacedOrdersCanBeAcceptedOrRejected() {
        Order accepted = placed(restaurantId);
        accepted.accept();
        orders.save(accepted);
        Order preparing = placed(restaurantId);
        preparing.accept();
        preparing.startPreparing();
        orders.save(preparing);

        for (Order order : List.of(accepted, preparing)) {
            OrderStatus before = order.getStatus();
            assertThrows(IllegalOrderTransitionException.class,
                    () -> service.acceptOrder(restaurantId, order.getId(), owner));
            assertThrows(IllegalOrderTransitionException.class,
                    () -> service.rejectOrder(restaurantId, order.getId(), "Too late", owner));
            assertEquals(before, orders.findById(order.getId()).orElseThrow().getStatus());
        }
    }

    @Test
    void otherRestaurantsStaffAreDeniedAndOrderIsUnchanged() {
        Order order = placed(restaurantId);
        MockHttpSession otherStaff = session(UUID.randomUUID(), UserRole.RESTAURANT_STAFF);

        assertThrows(AuthorizationException.class, () -> service.acceptOrder(restaurantId, order.getId(), otherStaff));
        assertThrows(AuthorizationException.class,
                () -> service.rejectOrder(restaurantId, order.getId(), "No", otherStaff));
        assertEquals(OrderStatus.PLACED, orders.findById(order.getId()).orElseThrow().getStatus());
    }

    @Test
    void staffCannotActOnAnotherRestaurantsOrderThroughTheirOwnRestaurant() {
        Order order = placed(otherRestaurantId);

        assertThrows(OrderNotFoundException.class, () -> service.acceptOrder(restaurantId, order.getId(), owner));
        assertThrows(OrderNotFoundException.class,
                () -> service.rejectOrder(restaurantId, order.getId(), "No", owner));
        assertEquals(OrderStatus.PLACED, orders.findById(order.getId()).orElseThrow().getStatus());
    }

    @Test
    void nonStaffAndAnonymousUsersAreDenied() {
        Order order = placed(restaurantId);

        for (UserRole role : List.of(UserRole.CUSTOMER, UserRole.COURIER, UserRole.PLATFORM_OPERATOR)) {
            MockHttpSession session = session(UUID.randomUUID(), role);
            assertThrows(AuthorizationException.class, () -> service.acceptOrder(restaurantId, order.getId(), session));
            assertThrows(AuthorizationException.class,
                    () -> service.rejectOrder(restaurantId, order.getId(), "No", session));
        }
        assertThrows(AuthenticationRequiredException.class,
                () -> service.acceptOrder(restaurantId, order.getId(), new MockHttpSession()));
        assertEquals(OrderStatus.PLACED, orders.findById(order.getId()).orElseThrow().getStatus());
    }

    @Test
    void unknownOrderIsNotFound() {
        assertThrows(OrderNotFoundException.class, () -> service.acceptOrder(restaurantId, UUID.randomUUID(), owner));
        assertThrows(OrderNotFoundException.class,
                () -> service.rejectOrder(restaurantId, UUID.randomUUID(), "No", owner));
    }

    private Order placed(UUID restaurant) {
        return orders.save(new Order(UUID.randomUUID(), UUID.randomUUID(), restaurant,
                List.of(new OrderLine(UUID.randomUUID(), 1, new BigDecimal("9.00"))), Instant.now()));
    }

    private static MockHttpSession session(UUID userId, UserRole role) {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("authenticatedUser", new UserAccount(userId.toString(), "user@example.com", "hash", role));
        return session;
    }
}
