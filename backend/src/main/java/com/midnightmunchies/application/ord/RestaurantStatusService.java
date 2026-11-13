package com.midnightmunchies.application.ord;

import com.midnightmunchies.application.reg.AuthenticationRequiredException;
import com.midnightmunchies.domain.ord.Restaurant;
import com.midnightmunchies.domain.ord.RestaurantRepository;
import com.midnightmunchies.domain.ord.RestaurantStatus;
import com.midnightmunchies.domain.reg.UserAccount;
import com.midnightmunchies.domain.reg.UserRole;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Service;

import java.util.Objects;
import java.util.UUID;

@Service
public class RestaurantStatusService {

    private static final String SESSION_USER = "authenticatedUser";

    private final RestaurantRepository restaurants;

    public RestaurantStatusService(RestaurantRepository restaurants) {
        this.restaurants = Objects.requireNonNull(restaurants, "restaurants must not be null");
    }

    public synchronized Restaurant pause(UUID restaurantId, HttpSession session) {
        Restaurant restaurant = authorize(restaurantId, session);
        if (restaurant.getStatus() != RestaurantStatus.OPEN) {
            throw new IllegalStateException("Only an open restaurant can be paused");
        }
        return restaurants.save(restaurant.withStatus(RestaurantStatus.PAUSED));
    }

    public synchronized Restaurant reopen(UUID restaurantId, HttpSession session) {
        Restaurant restaurant = authorize(restaurantId, session);
        if (restaurant.getStatus() == RestaurantStatus.OPEN) {
            throw new IllegalStateException("The restaurant is already open");
        }
        return restaurants.save(restaurant.withStatus(RestaurantStatus.OPEN));
    }

    private Restaurant authorize(UUID restaurantId, HttpSession session) {
        if (session == null || !(session.getAttribute(SESSION_USER) instanceof UserAccount user)) {
            throw new AuthenticationRequiredException("Authentication is required");
        }
        Restaurant restaurant = restaurants.findById(restaurantId)
                .orElseThrow(() -> new RestaurantNotFoundException("Restaurant does not exist"));
        if (user.role() == UserRole.PLATFORM_OPERATOR) {
            return restaurant;
        }
        if (user.role() != UserRole.RESTAURANT_STAFF) {
            throw new AuthorizationException("Only restaurant staff or the operator can pause or reopen a restaurant");
        }
        if (!UUID.fromString(user.id()).equals(restaurant.getOwnerId())) {
            throw new AuthorizationException("Staff can only pause or reopen their own restaurant");
        }
        return restaurant;
    }
}
