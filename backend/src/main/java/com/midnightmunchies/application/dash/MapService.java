package com.midnightmunchies.application.dash;

import com.midnightmunchies.application.reg.AuthenticationRequiredException;
import com.midnightmunchies.domain.dash.CityMap;
import com.midnightmunchies.domain.dash.RestaurantMarker;
import com.midnightmunchies.domain.dash.WaitingOrder;
import com.midnightmunchies.domain.ord.Order;
import com.midnightmunchies.domain.ord.OrderStatus;
import com.midnightmunchies.domain.ord.Restaurant;
import com.midnightmunchies.domain.ord.OrderRepository;
import com.midnightmunchies.domain.ord.RestaurantRepository;
import com.midnightmunchies.domain.reg.UserAccount;
import com.midnightmunchies.domain.reg.UserRole;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class MapService {

    private static final String SESSION_USER = "authenticatedUser";

    private final RestaurantRepository restaurantRepository;
    private final OrderRepository orderRepository;
    private final OnDutyCouriers couriers;
    private final DispatchBacklog backlog;

    public MapService(RestaurantRepository restaurantRepository, OrderRepository orderRepository,
                      OnDutyCouriers couriers, DispatchBacklog backlog) {
        this.restaurantRepository = Objects.requireNonNull(restaurantRepository, "restaurantRepository must not be null");
        this.orderRepository = Objects.requireNonNull(orderRepository, "orderRepository must not be null");
        this.couriers = Objects.requireNonNull(couriers, "couriers must not be null");
        this.backlog = Objects.requireNonNull(backlog, "backlog must not be null");
    }

    public CityMap cityMap(HttpSession session) {
        UserAccount viewer = requireAuthenticated(session);
        List<Restaurant> all = restaurantRepository.findAll().stream()
                .sorted(Comparator.comparing(Restaurant::getName))
                .toList();
        List<RestaurantMarker> restaurants = all.stream()
                .map(restaurant -> toMarker(restaurant, ownedBy(viewer, restaurant)))
                .toList();
        List<WaitingOrder> waiting = viewer.role() == UserRole.PLATFORM_OPERATOR ? awaitingCourier(all) : List.of();
        return CityMap.of(restaurants, couriers.onDuty(), waiting);
    }

    private List<WaitingOrder> awaitingCourier(List<Restaurant> restaurants) {
        Map<UUID, String> names = restaurants.stream()
                .collect(Collectors.toMap(Restaurant::getId, Restaurant::getName));
        return backlog.waitingOrderIds().stream()
                .map(orderRepository::findById)
                .flatMap(Optional::stream)
                .map(order -> new WaitingOrder(order.getId(), order.getRestaurantId(), names.get(order.getRestaurantId())))
                .toList();
    }

    private static boolean ownedBy(UserAccount viewer, Restaurant restaurant) {
        return viewer.role() == UserRole.RESTAURANT_STAFF
                && restaurant.getOwnerId() != null
                && restaurant.getOwnerId().toString().equals(viewer.id());
    }

    private RestaurantMarker toMarker(Restaurant restaurant, boolean ownedByViewer) {
        List<Order> orders = orderRepository.findByRestaurantId(restaurant.getId());
        int queued = orderRepository.findPreparationQueue(restaurant.getId()).size();
        return RestaurantMarker.from(restaurant, ownedByViewer,
                count(orders, OrderStatus.PLACED),
                queued,
                count(orders, OrderStatus.PREPARING),
                count(orders, OrderStatus.READY));
    }

    private static int count(List<Order> orders, OrderStatus status) {
        return (int) orders.stream().filter(order -> order.getStatus() == status).count();
    }

    private static UserAccount requireAuthenticated(HttpSession session) {
        if (session == null || !(session.getAttribute(SESSION_USER) instanceof UserAccount user)) {
            throw new AuthenticationRequiredException("Authentication is required");
        }
        return user;
    }
}
