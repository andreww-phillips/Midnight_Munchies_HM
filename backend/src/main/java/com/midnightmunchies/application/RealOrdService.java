package com.midnightmunchies.application;

import com.midnightmunchies.domain.ord.MenuItem;
import com.midnightmunchies.domain.ord.MenuItemStatus;
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

import java.math.BigDecimal;
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
    public List<Restaurant> browseRestaurants(HttpSession session) {
        return restaurantRepository.findAll();
    }

    @Override
    public List<MenuItem> browseMenu(UUID restaurantId, HttpSession session) {
        return menuItemRepository.findByRestaurantId(restaurantId);
    }

    @Override
    public MenuItem addMenuItem(UUID restaurantId, String name, BigDecimal price, HttpSession session) {
        requireRestaurant(restaurantId);
        requireItemDetails(name, price);
        MenuItem item = new MenuItem(UUID.randomUUID(), restaurantId, name, price, MenuItemStatus.AVAILABLE);
        return menuItemRepository.save(item);
    }

    @Override
    public MenuItem editMenuItem(UUID restaurantId, UUID menuItemId, String name, BigDecimal price, HttpSession session) {
        MenuItem current = requireMenuItem(restaurantId, menuItemId);
        requireItemDetails(name, price);
        MenuItem replacement = new MenuItem(current.getId(), current.getRestaurantId(), name, price, current.getStatus(),
                current.getImage(), current.getDescription());
        return menuItemRepository.save(replacement);
    }

    @Override
    public void removeMenuItem(UUID restaurantId, UUID menuItemId, HttpSession session) {
        MenuItem current = requireMenuItem(restaurantId, menuItemId);
        menuItemRepository.deleteById(current.getId());
    }

    @Override
    public MenuItem changeMenuItemAvailability(UUID restaurantId, UUID menuItemId, MenuItemStatus status, HttpSession session) {
        MenuItem current = requireMenuItem(restaurantId, menuItemId);
        if (status == null) {
            throw new IllegalArgumentException("Menu item status is required");
        }
        MenuItem replacement = new MenuItem(current.getId(), current.getRestaurantId(),
                current.getName(), current.getPrice(), status, current.getImage(), current.getDescription());
        return menuItemRepository.save(replacement);
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
            if (line == null || line.menuItemId() == null) {
                throw new IllegalArgumentException("Each order line must reference a menu item");
            }
            MenuItem item = menuItemRepository.findById(line.menuItemId())
                    .orElseThrow(() -> new IllegalArgumentException("Menu item does not exist"));
            if (!item.getRestaurantId().equals(restaurantId)
                    || item.getStatus() != com.midnightmunchies.domain.ord.MenuItemStatus.AVAILABLE) {
                throw new IllegalArgumentException("Menu item is unavailable for this restaurant");
            }
            return new OrderLine(item.getId(), item.getName(), line.quantity(), item.getPrice());
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

    private void requireRestaurant(UUID restaurantId) {
        if (restaurantId == null || restaurantRepository.findById(restaurantId).isEmpty()) {
            throw new IllegalArgumentException("Restaurant does not exist");
        }
    }

    private MenuItem requireMenuItem(UUID restaurantId, UUID menuItemId) {
        requireRestaurant(restaurantId);
        if (menuItemId == null) {
            throw new IllegalArgumentException("Menu item ID is required");
        }
        MenuItem item = menuItemRepository.findById(menuItemId)
                .orElseThrow(() -> new MenuItemNotFoundException("Menu item does not exist for this restaurant"));
        if (!item.getRestaurantId().equals(restaurantId)) {
            throw new MenuItemNotFoundException("Menu item does not exist for this restaurant");
        }
        return item;
    }

    private void requireItemDetails(String name, BigDecimal price) {
        if (name == null || price == null) {
            throw new IllegalArgumentException("Menu item name and price are required");
        }
    }
}
