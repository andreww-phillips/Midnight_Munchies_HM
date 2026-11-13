package com.midnightmunchies.application.dash;

import com.midnightmunchies.application.reg.AuthenticationRequiredException;
import com.midnightmunchies.domain.dash.CityMap;
import com.midnightmunchies.domain.dash.RestaurantMarker;
import com.midnightmunchies.domain.ord.Order;
import com.midnightmunchies.domain.ord.OrderStatus;
import com.midnightmunchies.domain.ord.Restaurant;
import com.midnightmunchies.domain.ord.OrderRepository;
import com.midnightmunchies.domain.ord.RestaurantRepository;
import com.midnightmunchies.domain.reg.UserAccount;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;

@Service
public class MapService {

    private static final String SESSION_USER = "authenticatedUser";

    private final RestaurantRepository restaurantRepository;
    private final OrderRepository orderRepository;
    private final OnDutyCouriers couriers;

    public MapService(RestaurantRepository restaurantRepository, OrderRepository orderRepository,
                      OnDutyCouriers couriers) {
        this.restaurantRepository = Objects.requireNonNull(restaurantRepository, "restaurantRepository must not be null");
        this.orderRepository = Objects.requireNonNull(orderRepository, "orderRepository must not be null");
        this.couriers = Objects.requireNonNull(couriers, "couriers must not be null");
    }

    public CityMap cityMap(HttpSession session) {
        requireAuthenticated(session);
        List<RestaurantMarker> restaurants = restaurantRepository.findAll().stream()
                .sorted(Comparator.comparing(Restaurant::getName))
                .map(this::toMarker)
                .toList();
        return CityMap.of(restaurants, couriers.onDuty());
    }

    private RestaurantMarker toMarker(Restaurant restaurant) {
        List<Order> orders = orderRepository.findByRestaurantId(restaurant.getId());
        int queued = orderRepository.findPreparationQueue(restaurant.getId()).size();
        return RestaurantMarker.from(restaurant,
                count(orders, OrderStatus.PLACED),
                queued,
                count(orders, OrderStatus.PREPARING),
                count(orders, OrderStatus.READY));
    }

    private static int count(List<Order> orders, OrderStatus status) {
        return (int) orders.stream().filter(order -> order.getStatus() == status).count();
    }

    private static void requireAuthenticated(HttpSession session) {
        if (session == null || !(session.getAttribute(SESSION_USER) instanceof UserAccount)) {
            throw new AuthenticationRequiredException("Authentication is required");
        }
    }
}
