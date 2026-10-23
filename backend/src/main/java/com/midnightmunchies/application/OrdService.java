package com.midnightmunchies.application;

import com.midnightmunchies.domain.ord.MenuItem;
import com.midnightmunchies.domain.ord.Order;
import jakarta.servlet.http.HttpSession;

import java.util.List;
import java.util.UUID;

public interface OrdService {
    List<MenuItem> browseMenu(UUID restaurantId, HttpSession session);

    Order placeOrder(UUID restaurantId, List<OrderLineRequest> lines, HttpSession session);

    List<Order> viewOrders(HttpSession session);

    record OrderLineRequest(UUID menuItemId, int quantity) {
    }
}
