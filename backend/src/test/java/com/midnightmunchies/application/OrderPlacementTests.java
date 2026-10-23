package com.midnightmunchies.application;

import com.midnightmunchies.domain.ord.MenuItem;
import com.midnightmunchies.domain.ord.MenuItemStatus;
import com.midnightmunchies.domain.ord.Order;
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
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OrderPlacementTests {
    private final InMemoryRestaurantRepository restaurants = new InMemoryRestaurantRepository();
    private final InMemoryMenuItemRepository menuItems = new InMemoryMenuItemRepository();
    private final InMemoryOrderRepository orders = new InMemoryOrderRepository();
    private final AuthorizedOrdService service = new AuthorizedOrdService(
            new RealOrdService(restaurants, menuItems, orders),
            restaurants
    );

    private final UUID customerId = UUID.randomUUID();
    private final UUID staffId = UUID.randomUUID();
    private final UUID restaurantId = UUID.randomUUID();
    private final UUID burgerId = UUID.randomUUID();
    private final UUID friesId = UUID.randomUUID();
    private final MockHttpSession customer = session(customerId, UserRole.CUSTOMER);

    OrderPlacementTests() {
        restaurants.save(new Restaurant(restaurantId, staffId, "Diner", RestaurantStatus.OPEN, 0, 0));
        menuItems.save(new MenuItem(burgerId, restaurantId, "Burger", new BigDecimal("10.00"), MenuItemStatus.AVAILABLE));
        menuItems.save(new MenuItem(friesId, restaurantId, "Fries", new BigDecimal("4.50"), MenuItemStatus.AVAILABLE));
    }

    @Test
    void customerPlacesOrderWithMultipleItemsAndItStartsPlaced() {
        Order order = service.placeOrder(restaurantId, List.of(
                new OrdService.OrderLineRequest(burgerId, 2),
                new OrdService.OrderLineRequest(friesId, 1)), customer);

        assertNotNull(order.getId());
        assertEquals(OrderStatus.PLACED, order.getStatus());
        assertEquals(customerId, order.getCustomerId());
        assertEquals(restaurantId, order.getRestaurantId());
        assertEquals(2, order.getOrderLines().size());
        assertEquals(new BigDecimal("10.00"), order.getOrderLines().get(0).getUnitPrice());
        assertEquals(order, orders.findById(order.getId()).orElseThrow());
    }

    @Test
    void eachOrderReceivesItsOwnId() {
        Order first = place(burgerId, 1);
        Order second = place(burgerId, 1);

        assertTrue(!first.getId().equals(second.getId()));
        assertEquals(2, orders.findAll().size());
    }

    @Test
    void placedOrderAppearsInRestaurantsIncomingOrders() {
        Order order = place(burgerId, 1);

        assertEquals(List.of(order), service.viewOrders(session(staffId, UserRole.RESTAURANT_STAFF)));
        assertEquals(List.of(), service.viewOrders(session(UUID.randomUUID(), UserRole.RESTAURANT_STAFF)));
    }

    @Test
    void onlyAuthenticatedCustomersCanPlaceOrders() {
        List<OrdService.OrderLineRequest> lines = List.of(new OrdService.OrderLineRequest(burgerId, 1));

        assertThrows(AuthenticationRequiredException.class,
                () -> service.placeOrder(restaurantId, lines, new MockHttpSession()));
        for (UserRole role : List.of(UserRole.RESTAURANT_STAFF, UserRole.COURIER, UserRole.PLATFORM_OPERATOR)) {
            assertThrows(AuthorizationException.class,
                    () -> service.placeOrder(restaurantId, lines, session(UUID.randomUUID(), role)));
        }
        assertEquals(0, orders.findAll().size());
    }

    @Test
    void invalidQuantitiesAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> place(burgerId, 0));
        assertThrows(IllegalArgumentException.class, () -> place(burgerId, -3));
        assertEquals(0, orders.findAll().size());
    }

    @Test
    void emptyOrMalformedLinesAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> service.placeOrder(restaurantId, List.of(), customer));
        assertThrows(IllegalArgumentException.class, () -> service.placeOrder(restaurantId, null, customer));
        assertThrows(IllegalArgumentException.class, () -> service.placeOrder(restaurantId,
                Arrays.asList(new OrdService.OrderLineRequest(burgerId, 1), null), customer));
        assertThrows(IllegalArgumentException.class, () -> service.placeOrder(restaurantId,
                List.of(new OrdService.OrderLineRequest(null, 1)), customer));
        assertEquals(0, orders.findAll().size());
    }

    @Test
    void restaurantMustExistAndBeOpen() {
        UUID pausedId = UUID.randomUUID();
        UUID closedId = UUID.randomUUID();
        restaurants.save(new Restaurant(pausedId, "Paused", RestaurantStatus.PAUSED, 0, 0));
        restaurants.save(new Restaurant(closedId, "Closed", RestaurantStatus.CLOSED, 0, 0));
        menuItems.save(new MenuItem(UUID.randomUUID(), pausedId, "Soup", BigDecimal.ONE, MenuItemStatus.AVAILABLE));
        List<OrdService.OrderLineRequest> lines = List.of(new OrdService.OrderLineRequest(burgerId, 1));

        assertThrows(IllegalArgumentException.class, () -> service.placeOrder(UUID.randomUUID(), lines, customer));
        assertThrows(IllegalArgumentException.class, () -> service.placeOrder(pausedId, lines, customer));
        assertThrows(IllegalArgumentException.class, () -> service.placeOrder(closedId, lines, customer));
        assertEquals(0, orders.findAll().size());
    }

    @Test
    void menuItemsMustExistBeAvailableAndBelongToTheRestaurant() {
        UUID soldOutId = UUID.randomUUID();
        menuItems.save(new MenuItem(soldOutId, restaurantId, "Special", BigDecimal.TEN, MenuItemStatus.SOLD_OUT));
        UUID otherRestaurantId = UUID.randomUUID();
        UUID otherItemId = UUID.randomUUID();
        restaurants.save(new Restaurant(otherRestaurantId, "Other", RestaurantStatus.OPEN, 0, 0));
        menuItems.save(new MenuItem(otherItemId, otherRestaurantId, "Pasta", BigDecimal.TEN, MenuItemStatus.AVAILABLE));

        assertThrows(IllegalArgumentException.class, () -> place(UUID.randomUUID(), 1));
        assertThrows(IllegalArgumentException.class, () -> place(soldOutId, 1));
        assertThrows(IllegalArgumentException.class, () -> place(otherItemId, 1));
        assertThrows(IllegalArgumentException.class, () -> service.placeOrder(restaurantId, List.of(
                new OrdService.OrderLineRequest(burgerId, 1),
                new OrdService.OrderLineRequest(soldOutId, 1)), customer));
        assertEquals(0, orders.findAll().size());
    }

    private Order place(UUID menuItemId, int quantity) {
        return service.placeOrder(restaurantId, List.of(new OrdService.OrderLineRequest(menuItemId, quantity)), customer);
    }

    private MockHttpSession session(UUID userId, UserRole role) {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("authenticatedUser",
                new UserAccount(userId.toString(), "user@example.com", "hash", role));
        return session;
    }
}
