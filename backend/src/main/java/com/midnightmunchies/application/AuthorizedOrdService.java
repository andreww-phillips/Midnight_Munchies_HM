package com.midnightmunchies.application;

import com.midnightmunchies.domain.ord.MenuItem;
import com.midnightmunchies.domain.ord.Order;
import com.midnightmunchies.domain.ord.Restaurant;
import com.midnightmunchies.domain.ord.repository.RestaurantRepository;
import com.midnightmunchies.domain.reg.UserAccount;
import com.midnightmunchies.domain.reg.UserRole;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

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
    public List<MenuItem> browseMenu(UUID restaurantId, HttpSession session) {
        requireAuthenticated(session);
        requireRestaurant(restaurantId);
        return realService.browseMenu(restaurantId, session);
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

    private UserAccount requireAuthenticated(HttpSession session) {
        UserAccount user = (UserAccount) session.getAttribute(SESSION_USER);
        if (user == null) {
            throw new AuthenticationRequiredException("Authentication is required");
        }
        return user;
    }

    private void requireRestaurant(UUID restaurantId) {
        if (restaurantId == null || restaurantRepository.findById(restaurantId).isEmpty()) {
            throw new IllegalArgumentException("Restaurant does not exist");
        }
    }

    private UUID userId(UserAccount user) {
        return UUID.fromString(user.id());
    }
}
