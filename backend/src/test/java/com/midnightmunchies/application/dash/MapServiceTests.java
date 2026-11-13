package com.midnightmunchies.application.dash;

import com.midnightmunchies.application.reg.AuthenticationRequiredException;
import com.midnightmunchies.domain.dash.CityMap;
import com.midnightmunchies.domain.dash.CourierMarker;
import com.midnightmunchies.domain.dash.MapBounds;
import com.midnightmunchies.domain.dash.QueueBand;
import com.midnightmunchies.domain.dash.RestaurantMarker;
import com.midnightmunchies.domain.ord.Order;
import com.midnightmunchies.domain.ord.OrderLine;
import com.midnightmunchies.domain.ord.Restaurant;
import com.midnightmunchies.domain.ord.RestaurantStatus;
import com.midnightmunchies.domain.reg.UserAccount;
import com.midnightmunchies.domain.reg.UserRole;
import com.midnightmunchies.technicalservices.InMemoryOrderRepository;
import com.midnightmunchies.technicalservices.InMemoryRestaurantRepository;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpSession;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MapServiceTests {

    private static final Instant START = Instant.parse("2026-11-12T23:00:00Z");

    private final InMemoryRestaurantRepository restaurants = new InMemoryRestaurantRepository();
    private final InMemoryOrderRepository orders = new InMemoryOrderRepository();
    private final List<CourierMarker> couriers = new ArrayList<>();
    private final MapService service = new MapService(restaurants, orders, () -> couriers);

    @Test
    void countsOrdersByStageForEachRestaurant() {
        Restaurant diner = restaurant("Diner", RestaurantStatus.OPEN, 45.50, -73.57);
        placed(diner);
        queued(diner, 0);
        queued(diner, 1);
        preparing(diner);
        ready(diner);
        ready(diner);

        RestaurantMarker marker = service.cityMap(loggedIn()).restaurants().get(0);

        assertEquals(1, marker.pendingCount());
        assertEquals(2, marker.queuedCount());
        assertEquals(1, marker.preparingCount());
        assertEquals(2, marker.readyCount());
        assertEquals(QueueBand.GREEN, marker.band());
    }

    @Test
    void bandFollowsQueuedCountAndPausedStatus() {
        Restaurant busy = restaurant("Busy", RestaurantStatus.OPEN, 45.50, -73.57);
        for (int i = 0; i < 6; i++) {
            queued(busy, i);
        }
        Restaurant paused = restaurant("Paused", RestaurantStatus.PAUSED, 45.51, -73.58);

        List<RestaurantMarker> markers = service.cityMap(loggedIn()).restaurants();

        assertEquals(QueueBand.RED, markers.get(0).band());
        assertEquals(6, markers.get(0).queuedCount());
        assertEquals(paused.getId(), markers.get(1).id());
        assertEquals(QueueBand.RED, markers.get(1).band());
    }

    @Test
    void ordersOfOtherRestaurantsAreNotCounted() {
        Restaurant first = restaurant("First", RestaurantStatus.OPEN, 45.50, -73.57);
        Restaurant second = restaurant("Second", RestaurantStatus.OPEN, 45.51, -73.58);
        queued(second, 0);

        List<RestaurantMarker> markers = service.cityMap(loggedIn()).restaurants();

        assertEquals(first.getId(), markers.get(0).id());
        assertEquals(0, markers.get(0).queuedCount());
        assertEquals(1, markers.get(1).queuedCount());
    }

    @Test
    void offlineCouriersAreExcludedAndBoundsIncludeOnDutyCouriers() {
        restaurant("Diner", RestaurantStatus.OPEN, 45.50, -73.57);
        CourierMarker idle = new CourierMarker("c1", 45.53, -73.60, "IDLE");
        couriers.add(idle);
        couriers.add(new CourierMarker("c2", 10.0, 10.0, "OFFLINE"));

        CityMap map = service.cityMap(loggedIn());

        assertEquals(List.of(idle), map.couriers());
        assertEquals(new MapBounds(45.50, 45.53, -73.60, -73.57), map.bounds());
    }

    @Test
    void emptyCityGivesNoBoundsAndNoMarkers() {
        CityMap map = service.cityMap(loggedIn());

        assertNull(map.bounds());
        assertEquals(List.of(), map.restaurants());
        assertEquals(List.of(), map.couriers());
    }

    @Test
    void customersCanViewTheMap() {
        restaurant("Diner", RestaurantStatus.OPEN, 45.50, -73.57);
        MockHttpSession customer = new MockHttpSession();
        customer.setAttribute("authenticatedUser",
                new UserAccount(UUID.randomUUID().toString(), "customer@example.com", "hash", UserRole.CUSTOMER));

        assertEquals(1, service.cityMap(customer).restaurants().size());
    }

    @Test
    void missingLoginIsRejected() {
        assertThrows(AuthenticationRequiredException.class, () -> service.cityMap(new MockHttpSession()));
        assertThrows(AuthenticationRequiredException.class, () -> service.cityMap(null));
    }

    private MockHttpSession loggedIn() {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("authenticatedUser",
                new UserAccount(UUID.randomUUID().toString(), "operator@example.com", "hash", UserRole.PLATFORM_OPERATOR));
        return session;
    }

    private Restaurant restaurant(String name, RestaurantStatus status, double latitude, double longitude) {
        return restaurants.save(new Restaurant(UUID.randomUUID(), name, status, latitude, longitude));
    }

    private Order placed(Restaurant restaurant) {
        Order order = new Order(UUID.randomUUID(), UUID.randomUUID(), restaurant.getId(),
                List.of(new OrderLine(UUID.randomUUID(), 1, new BigDecimal("5.00"))), START);
        return orders.save(order);
    }

    private Order queued(Restaurant restaurant, int position) {
        Order order = placed(restaurant);
        order.accept();
        order.recordAcceptedAt(START.plusSeconds(position));
        return orders.save(order);
    }

    private Order preparing(Restaurant restaurant) {
        Order order = queued(restaurant, 100);
        order.startPreparing();
        return orders.save(order);
    }

    private Order ready(Restaurant restaurant) {
        Order order = preparing(restaurant);
        order.markReady();
        return orders.save(order);
    }
}
