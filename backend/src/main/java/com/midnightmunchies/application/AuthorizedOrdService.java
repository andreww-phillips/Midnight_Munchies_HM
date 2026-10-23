package com.midnightmunchies.application;

import com.midnightmunchies.domain.ord.MenuItem;
import com.midnightmunchies.domain.ord.MenuItemStatus;
import com.midnightmunchies.domain.ord.Order;
import com.midnightmunchies.domain.ord.OrderEvent;
import com.midnightmunchies.domain.ord.Restaurant;
import com.midnightmunchies.domain.ord.repository.RestaurantRepository;
import com.midnightmunchies.domain.reg.UserAccount;
import com.midnightmunchies.domain.reg.UserRole;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Service
public class AuthorizedOrdService implements OrdService {
    private static final String SESSION_USER = "authenticatedUser";

    private final OrdService realService;
    private final RestaurantRepository restaurantRepository;

    public AuthorizedOrdService(
            @Qualifier("realOrdService") OrdService realService,
            RestaurantRepository restaurantRepository
    ) {
        this.realService = realService;
        this.restaurantRepository = restaurantRepository;
    }

    @Override
    public List<Restaurant> browseRestaurants(HttpSession session) {
        requireAuthenticated(session);
        return realService.browseRestaurants(session);
    }

    @Override
    public List<MenuItem> browseMenu(UUID restaurantId, HttpSession session) {
        requireAuthenticated(session);
        requireRestaurant(restaurantId);
        return realService.browseMenu(restaurantId, session);
    }

    @Override
    public MenuItem addMenuItem(UUID restaurantId, String name, BigDecimal price, HttpSession session) {
        requireRestaurantOwner(restaurantId, session);
        return realService.addMenuItem(restaurantId, name, price, session);
    }

    @Override
    public MenuItem editMenuItem(UUID restaurantId, UUID menuItemId, String name, BigDecimal price, HttpSession session) {
        requireRestaurantOwner(restaurantId, session);
        return realService.editMenuItem(restaurantId, menuItemId, name, price, session);
    }

    @Override
    public void removeMenuItem(UUID restaurantId, UUID menuItemId, HttpSession session) {
        requireRestaurantOwner(restaurantId, session);
        realService.removeMenuItem(restaurantId, menuItemId, session);
    }

    @Override
    public MenuItem changeMenuItemAvailability(UUID restaurantId, UUID menuItemId, MenuItemStatus status, HttpSession session) {
        requireRestaurantOwner(restaurantId, session);
        return realService.changeMenuItemAvailability(restaurantId, menuItemId, status, session);
    }

    @Override
    public Order placeOrder(UUID restaurantId, List<OrderLineRequest> lines, HttpSession session) {
        UserAccount user = requireAuthenticated(session);
        if (user.role() != UserRole.CUSTOMER) {
            throw new AuthorizationException("Only customers can place orders");
        }
        requireRestaurant(restaurantId);
        return realService.placeOrder(restaurantId, lines, session);
    }

    @Override
    public List<Order> viewOrders(HttpSession session) {
        UserAccount user = requireAuthenticated(session);
        if (user.role() == UserRole.CUSTOMER) {
            UUID customerId = userId(user);
            return realService.viewOrders(session).stream()
                    .filter(order -> order.getCustomerId().equals(customerId))
                    .toList();
        }
        if (user.role() == UserRole.RESTAURANT_STAFF) {
            List<UUID> ownedRestaurants = restaurantRepository.findAll().stream()
                    .filter(restaurant -> userId(user).equals(restaurant.getOwnerId()))
                    .map(Restaurant::getId)
                    .toList();
            return realService.viewOrders(session).stream()
                    .filter(order -> ownedRestaurants.contains(order.getRestaurantId()))
                    .toList();
        }
        if (user.role() == UserRole.PLATFORM_OPERATOR) {
            return realService.viewOrders(session);
        }
        throw new AuthorizationException("This role cannot view orders");
    }

    @Override
    public Order acceptOrder(UUID restaurantId, UUID orderId, HttpSession session) {
        requireOrderOwner(restaurantId, session);
        return realService.acceptOrder(restaurantId, orderId, session);
    }

    @Override
    public Order rejectOrder(UUID restaurantId, UUID orderId, String reason, HttpSession session) {
        requireOrderOwner(restaurantId, session);
        return realService.rejectOrder(restaurantId, orderId, reason, session);
    }

    @Override
    public Order startNextPreparation(UUID restaurantId, HttpSession session) {
        requireQueueOwner(restaurantId, session);
        return realService.startNextPreparation(restaurantId, session);
    }

    @Override
    public List<Order> viewPreparationQueue(UUID restaurantId, HttpSession session) {
        requireQueueOwner(restaurantId, session);
        return realService.viewPreparationQueue(restaurantId, session);
    }

    @Override
    public Order markOrderReady(UUID restaurantId, UUID orderId, HttpSession session) {
        requireOwner(restaurantId, session,
                "Only restaurant staff can mark orders ready",
                "Staff can only mark their own restaurant's orders ready");
        return realService.markOrderReady(restaurantId, orderId, session);
    }

    @Override
    public Order cancelOrder(UUID orderId, HttpSession session) {
        UserAccount user = requireAuthenticated(session);
        if (user.role() != UserRole.CUSTOMER && user.role() != UserRole.PLATFORM_OPERATOR) {
            throw new AuthorizationException("Only customers and operators can cancel orders");
        }
        return realService.cancelOrder(orderId, session);
    }

    @Override
    public List<OrderEvent> viewOrderEvents(UUID orderId, HttpSession session) {
        UserAccount user = requireAuthenticated(session);
        if (user.role() == UserRole.COURIER) {
            throw new AuthorizationException("Couriers cannot view order events");
        }
        return realService.viewOrderEvents(orderId, session);
    }

    private UserAccount requireAuthenticated(HttpSession session) {
        UserAccount user = (UserAccount) session.getAttribute(SESSION_USER);
        if (user == null) {
            throw new AuthenticationRequiredException("Authentication is required");
        }
        return user;
    }

    private Restaurant requireRestaurant(UUID restaurantId) {
        if (restaurantId == null) {
            throw new IllegalArgumentException("Restaurant does not exist");
        }
        return restaurantRepository.findById(restaurantId)
                .orElseThrow(() -> new IllegalArgumentException("Restaurant does not exist"));
    }

    private void requireRestaurantOwner(UUID restaurantId, HttpSession session) {
        requireOwner(restaurantId, session,
                "Only restaurant staff can manage menus",
                "Staff can only manage their own restaurant's menu");
    }

    private void requireOrderOwner(UUID restaurantId, HttpSession session) {
        requireOwner(restaurantId, session,
                "Only restaurant staff can accept or reject orders",
                "Staff can only accept or reject their own restaurant's orders");
    }

    private void requireQueueOwner(UUID restaurantId, HttpSession session) {
        requireOwner(restaurantId, session,
                "Only restaurant staff can manage the preparation queue",
                "Staff can only manage their own restaurant's preparation queue");
    }

    private void requireOwner(UUID restaurantId, HttpSession session, String notStaffMessage, String notOwnerMessage) {
        UserAccount user = requireAuthenticated(session);
        if (user.role() != UserRole.RESTAURANT_STAFF) {
            throw new AuthorizationException(notStaffMessage);
        }
        Restaurant restaurant = requireRestaurant(restaurantId);
        if (!userId(user).equals(restaurant.getOwnerId())) {
            throw new AuthorizationException(notOwnerMessage);
        }
    }

    private UUID userId(UserAccount user) {
        return UUID.fromString(user.id());
    }
}
