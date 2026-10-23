package com.midnightmunchies.application;

import com.midnightmunchies.domain.ord.MenuItem;
import com.midnightmunchies.domain.ord.MenuItemStatus;
import com.midnightmunchies.domain.ord.Order;
import com.midnightmunchies.domain.ord.OrderEvent;
import com.midnightmunchies.domain.ord.Restaurant;
import jakarta.servlet.http.HttpSession;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public interface OrdService {
    List<Restaurant> browseRestaurants(HttpSession session);

    List<MenuItem> browseMenu(UUID restaurantId, HttpSession session);

    MenuItem addMenuItem(UUID restaurantId, String name, BigDecimal price, HttpSession session);

    MenuItem editMenuItem(UUID restaurantId, UUID menuItemId, String name, BigDecimal price, HttpSession session);

    void removeMenuItem(UUID restaurantId, UUID menuItemId, HttpSession session);

    MenuItem changeMenuItemAvailability(UUID restaurantId, UUID menuItemId, MenuItemStatus status, HttpSession session);

    Order placeOrder(UUID restaurantId, List<OrderLineRequest> lines, HttpSession session);

    List<Order> viewOrders(HttpSession session);

    Order startNextPreparation(UUID restaurantId, HttpSession session);

    List<Order> viewPreparationQueue(UUID restaurantId, HttpSession session);

    Order markOrderReady(UUID restaurantId, UUID orderId, HttpSession session);

    Order cancelOrder(UUID orderId, HttpSession session);

    List<OrderEvent> viewOrderEvents(UUID orderId, HttpSession session);

    record OrderLineRequest(UUID menuItemId, int quantity) {
    }
}
