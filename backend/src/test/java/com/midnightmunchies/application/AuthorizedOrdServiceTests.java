package com.midnightmunchies.application;

import com.midnightmunchies.domain.ord.MenuItem;
import com.midnightmunchies.domain.ord.MenuItemStatus;
import com.midnightmunchies.domain.ord.Order;
import com.midnightmunchies.domain.ord.OrderLine;
import com.midnightmunchies.domain.ord.Restaurant;
import com.midnightmunchies.domain.ord.RestaurantStatus;
import com.midnightmunchies.domain.reg.UserAccount;
import com.midnightmunchies.domain.reg.UserRole;
import com.midnightmunchies.technicalservices.ord.repository.InMemoryMenuItemRepository;
import com.midnightmunchies.technicalservices.ord.repository.InMemoryOrderRepository;
import com.midnightmunchies.technicalservices.ord.repository.InMemoryRestaurantRepository;
import jakarta.servlet.http.HttpSession;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpSession;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AuthorizedOrdServiceTests {
    private final InMemoryRestaurantRepository restaurants = new InMemoryRestaurantRepository();
    private final InMemoryMenuItemRepository menuItems = new InMemoryMenuItemRepository();
    private final InMemoryOrderRepository orders = new InMemoryOrderRepository();
    private final AuthorizedOrdService service = new AuthorizedOrdService(
            new RealOrdService(restaurants, menuItems, orders),
            restaurants
    );

    @Test
    void unauthenticatedMenuBrowsingIsRejectedBeforeForwarding() {
        UUID restaurantId = UUID.randomUUID();
        restaurants.save(new Restaurant(restaurantId, "Diner", RestaurantStatus.OPEN, 0, 0));

        assertThrows(AuthenticationRequiredException.class,
                () -> service.browseMenu(restaurantId, new MockHttpSession()));
    }

    @Test
    void deniedRoleCannotPlaceOrderAndDoesNotChangeState() {
        UUID restaurantId = UUID.randomUUID();
        UUID itemId = UUID.randomUUID();
        restaurants.save(new Restaurant(restaurantId, "Diner", RestaurantStatus.OPEN, 0, 0));
        menuItems.save(new MenuItem(itemId, restaurantId, "Burger", new BigDecimal("10.00"),
                MenuItemStatus.AVAILABLE));
        MockHttpSession session = session(UUID.randomUUID(), UserRole.RESTAURANT_STAFF);

        assertThrows(AuthorizationException.class, () -> service.placeOrder(
                restaurantId,
                List.of(new OrdService.OrderLineRequest(itemId, 1)),
                session
        ));
        assertEquals(0, orders.findAll().size());
    }

    @Test
    void customerOnlyReceivesOwnedOrders() {
        UUID customerId = UUID.randomUUID();
        UUID restaurantId = UUID.randomUUID();
        restaurants.save(new Restaurant(restaurantId, "Diner", RestaurantStatus.OPEN, 0, 0));
        MockHttpSession session = session(customerId, UserRole.CUSTOMER);

        Order ownedOrder = new Order(UUID.randomUUID(), customerId, restaurantId,
                List.of(new OrderLine(UUID.randomUUID(), 1, BigDecimal.TEN)), Instant.now());
        Order otherOrder = new Order(UUID.randomUUID(), UUID.randomUUID(), restaurantId,
                List.of(new OrderLine(UUID.randomUUID(), 1, BigDecimal.TEN)), Instant.now());
        orders.save(ownedOrder);
        orders.save(otherOrder);

        assertEquals(List.of(ownedOrder), service.viewOrders(session));
    }

    private MockHttpSession session(UUID userId, UserRole role) {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("authenticatedUser",
                new UserAccount(userId.toString(), "user@example.com", "hash", role));
        return session;
    }
}
