package com.midnightmunchies.technicalservices.ord;

import com.midnightmunchies.domain.ord.MenuItem;
import com.midnightmunchies.domain.ord.MenuItemStatus;
import com.midnightmunchies.domain.ord.Restaurant;
import com.midnightmunchies.domain.ord.RestaurantStatus;
import com.midnightmunchies.domain.ord.repository.MenuItemRepository;
import com.midnightmunchies.domain.ord.repository.RestaurantRepository;

import java.math.BigDecimal;
import java.util.UUID;

public class DemoCatalog {
    private static final UUID BURGER_RESTAURANT = UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final UUID PIZZA_RESTAURANT = UUID.fromString("10000000-0000-0000-0000-000000000002");
    // Matches restaurant@example.com in AuthService.
    private static final UUID BURGER_OWNER = UUID.fromString("30000000-0000-0000-0000-000000000001");

    public DemoCatalog(RestaurantRepository restaurants, MenuItemRepository menuItems) {
        restaurants.save(new Restaurant(BURGER_RESTAURANT, BURGER_OWNER, "Bobs Burgers", RestaurantStatus.OPEN, 45.5, -73.56));
        restaurants.save(new Restaurant(PIZZA_RESTAURANT, "Pizza Palace", RestaurantStatus.OPEN, 45.5, -73.56));
        menuItems.save(new MenuItem(UUID.fromString("20000000-0000-0000-0000-000000000001"), BURGER_RESTAURANT, "Midnight Burger", new BigDecimal("12.99"), MenuItemStatus.AVAILABLE));
        menuItems.save(new MenuItem(UUID.fromString("20000000-0000-0000-0000-000000000002"), BURGER_RESTAURANT, "Loaded Fries", new BigDecimal("5.49"), MenuItemStatus.AVAILABLE));
        menuItems.save(new MenuItem(UUID.fromString("20000000-0000-0000-0000-000000000003"), PIZZA_RESTAURANT, "Pepperoni Pizza", new BigDecimal("16.99"), MenuItemStatus.AVAILABLE));
        menuItems.save(new MenuItem(UUID.fromString("20000000-0000-0000-0000-000000000004"), PIZZA_RESTAURANT, "Garlic Knots", new BigDecimal("6.99"), MenuItemStatus.AVAILABLE));
    }
}
