package com.midnightmunchies.application.ord;

import com.midnightmunchies.domain.ord.MenuItem;
import com.midnightmunchies.domain.ord.MenuItemRepository;
import com.midnightmunchies.domain.ord.OrderEventLog;
import com.midnightmunchies.domain.ord.OrderRepository;
import com.midnightmunchies.domain.ord.Restaurant;
import com.midnightmunchies.domain.ord.RestaurantRepository;
import com.midnightmunchies.technicalservices.InMemoryMenuItemRepository;
import com.midnightmunchies.technicalservices.InMemoryOrderEventLog;
import com.midnightmunchies.technicalservices.InMemoryOrderRepository;
import com.midnightmunchies.technicalservices.InMemoryRestaurantRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.List;

/** Restores the in-memory catalog captured once after startup seeding. */
@Service
@ConditionalOnProperty(name = "catalog.mode", havingValue = "memory", matchIfMissing = true)
public class OrdResetService {
    private final InMemoryRestaurantRepository restaurants;
    private final InMemoryMenuItemRepository menuItems;
    private final InMemoryOrderRepository orders;
    private final InMemoryOrderEventLog events;
    private final List<Restaurant> initialRestaurants;
    private final List<MenuItem> initialMenu;

    public OrdResetService(RestaurantRepository restaurants, MenuItemRepository menuItems,
                           OrderRepository orders, OrderEventLog events) {
        if (!(restaurants instanceof InMemoryRestaurantRepository memoryRestaurants)
                || !(menuItems instanceof InMemoryMenuItemRepository memoryMenu)
                || !(orders instanceof InMemoryOrderRepository memoryOrders)
                || !(events instanceof InMemoryOrderEventLog memoryEvents)) {
            throw new IllegalArgumentException("Simulator reset requires in-memory repositories");
        }
        this.restaurants = memoryRestaurants;
        this.menuItems = memoryMenu;
        this.orders = memoryOrders;
        this.events = memoryEvents;
        initialRestaurants = restaurants.findAll();
        initialMenu = menuItems.findAll();
    }

    /** Internal operation: invoke only while the application coordinator excludes other commands. */
    public void resetToInitialState() {
        orders.clear();
        events.clear();
        menuItems.clear();
        restaurants.clear();
        initialRestaurants.forEach(restaurants::save);
        initialMenu.forEach(menuItems::save);
    }
}
