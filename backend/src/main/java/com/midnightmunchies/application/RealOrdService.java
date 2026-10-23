package com.midnightmunchies.application;

import com.midnightmunchies.domain.ord.MenuItem;
import com.midnightmunchies.domain.ord.Order;
import com.midnightmunchies.domain.ord.OrderLine;
import com.midnightmunchies.domain.ord.Restaurant;
import com.midnightmunchies.domain.ord.RestaurantStatus;
import com.midnightmunchies.domain.ord.repository.MenuItemRepository;
import com.midnightmunchies.domain.ord.repository.OrderRepository;
import com.midnightmunchies.domain.ord.repository.RestaurantRepository;
import com.midnightmunchies.domain.reg.UserAccount;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service("realOrdService")
public class RealOrdService implements OrdService {
    private static final String SESSION_USER = "authenticatedUser";

    private final RestaurantRepository restaurantRepository;
    private final MenuItemRepository menuItemRepository;
    private final OrderRepository orderRepository;

    public RealOrdService(
            RestaurantRepository restaurantRepository,
            MenuItemRepository menuItemRepository,
            OrderRepository orderRepository
    ) {
        this.restaurantRepository = restaurantRepository;
        this.menuItemRepository = menuItemRepository;
        this.orderRepository = orderRepository;
    }

    @Override
    public List<MenuItem> browseMenu(UUID restaurantId, HttpSession session) {
        return menuItemRepository.findByRestaurantId(restaurantId);
    }

    @Override
    public Order placeOrder(UUID restaurantId, List<OrderLineRequest> lines, HttpSession session) {
        UserAccount user = currentUser(session);
        Restaurant restaurant = restaurantRepository.findById(restaurantId)
                .orElseThrow(() -> new IllegalArgumentException("Restaurant does not exist"));
        if (restaurant.getStatus() != RestaurantStatus.OPEN) {
            throw new IllegalArgumentException("Restaurant is not open");
        }
        if (lines == null || lines.isEmpty()) {
            throw new IllegalArgumentException("At least one order line is required");
        }

        List<OrderLine> orderLines = lines.stream().map(line -> {
            MenuItem item = menuItemRepository.findById(line.menuItemId())
                    .orElseThrow(() -> new IllegalArgumentException("Menu item does not exist"));
            if (!item.getRestaurantId().equals(restaurantId)
                    || item.getStatus() != com.midnightmunchies.domain.ord.MenuItemStatus.AVAILABLE) {
                throw new IllegalArgumentException("Menu item is unavailable for this restaurant");
            }
            return new OrderLine(item.getId(), line.quantity(), item.getPrice());
        }).toList();

        Order order = new Order(UUID.randomUUID(), UUID.fromString(user.id()), restaurantId, orderLines, Instant.now());
        return orderRepository.save(order);
    }

    @Override
    public List<Order> viewOrders(HttpSession session) {
        return orderRepository.findAll();
    }

    private UserAccount currentUser(HttpSession session) {
        return (UserAccount) session.getAttribute(SESSION_USER);
    }
}
