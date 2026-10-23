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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpSession;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RealOrdServiceTests {
    private final InMemoryRestaurantRepository restaurants = new InMemoryRestaurantRepository();
    private final InMemoryMenuItemRepository menuItems = new InMemoryMenuItemRepository();
    private final InMemoryOrderRepository orders = new InMemoryOrderRepository();
    private final RealOrdService service = new RealOrdService(restaurants, menuItems, orders);
    private final MockHttpSession session = new MockHttpSession();
    private final UUID restaurantId = UUID.randomUUID();
    private final MenuItem original = new MenuItem(UUID.randomUUID(), restaurantId, "Poutine",
            new BigDecimal("12.35"), MenuItemStatus.AVAILABLE);

    @BeforeEach
    void setUp() {
        restaurants.save(new Restaurant(restaurantId, "Diner", RestaurantStatus.OPEN, 0, 0));
        menuItems.save(original);
        session.setAttribute("authenticatedUser",
                new UserAccount(UUID.randomUUID().toString(), "customer@example.com", "hash", UserRole.CUSTOMER));
    }

    @Test
    void placedOrderRetainsOriginalItemNameAndPriceAfterMenuReplacement() {
        Order placed = service.placeOrder(restaurantId,
                List.of(new OrdService.OrderLineRequest(original.getId(), 2)), session);
        MenuItem replacement = new MenuItem(original.getId(), restaurantId, "Loaded Poutine",
                new BigDecimal("15.00"), MenuItemStatus.SOLD_OUT);

        menuItems.save(replacement);

        assertEquals(List.of(replacement), service.browseMenu(restaurantId, session));
        assertOriginalItemDetails(orders.findById(placed.getId()).orElseThrow());
        assertOriginalItemDetails(service.viewOrders(session).getFirst());
    }

    @Test
    void placedOrderRetainsOriginalItemDetailsAfterMenuRemoval() {
        Order placed = service.placeOrder(restaurantId,
                List.of(new OrdService.OrderLineRequest(original.getId(), 2)), session);

        assertTrue(menuItems.deleteById(original.getId()));

        assertTrue(service.browseMenu(restaurantId, session).isEmpty());
        assertOriginalItemDetails(orders.findById(placed.getId()).orElseThrow());
        assertOriginalItemDetails(service.viewOrders(session).getFirst());
    }

    private void assertOriginalItemDetails(Order order) {
        assertEquals(restaurantId, order.getRestaurantId());
        assertEquals(1, order.getOrderLines().size());
        OrderLine line = order.getOrderLines().getFirst();
        assertEquals(original.getId(), line.getMenuItemId());
        assertEquals("Poutine", line.getMenuItemName());
        assertEquals(2, line.getQuantity());
        assertEquals(new BigDecimal("12.35"), line.getUnitPrice());
    }
}
