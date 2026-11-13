package com.midnightmunchies.domain.dash;

import com.midnightmunchies.domain.ord.Restaurant;
import com.midnightmunchies.domain.ord.RestaurantStatus;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CityMapTests {

    @Test
    void boundsIncludeRestaurantsAndOnDutyCouriers() {
        RestaurantMarker restaurant = marker(RestaurantStatus.OPEN, 45.50, -73.57, 0);
        CourierMarker courier = new CourierMarker("c1", 45.53, -73.60, "IDLE");

        CityMap map = CityMap.of(List.of(restaurant), List.of(courier));

        assertEquals(new MapBounds(45.50, 45.53, -73.60, -73.57), map.bounds());
    }

    @Test
    void offlineCouriersAreDroppedAndDoNotStretchBounds() {
        RestaurantMarker restaurant = marker(RestaurantStatus.OPEN, 45.50, -73.57, 0);
        CourierMarker offline = new CourierMarker("c1", 10.0, 10.0, "OFFLINE");
        CourierMarker carrying = new CourierMarker("c2", 45.51, -73.58, "CARRYING");

        CityMap map = CityMap.of(List.of(restaurant), List.of(offline, carrying));

        assertEquals(List.of(carrying), map.couriers());
        assertEquals(new MapBounds(45.50, 45.51, -73.58, -73.57), map.bounds());
    }

    @Test
    void emptyCityHasNoBounds() {
        CityMap map = CityMap.of(List.of(), List.of());

        assertNull(map.bounds());
        assertEquals(List.of(), map.restaurants());
        assertEquals(List.of(), map.couriers());
    }

    @Test
    void markerListsCannotBeModified() {
        CityMap map = CityMap.of(List.of(), List.of());

        assertThrows(UnsupportedOperationException.class,
                () -> map.couriers().add(new CourierMarker("c1", 0, 0, "IDLE")));
    }

    @Test
    void restaurantMarkerTakesItsBandFromQueuedCount() {
        assertEquals(QueueBand.GREEN, marker(RestaurantStatus.OPEN, 45.5, -73.5, 2).band());
        assertEquals(QueueBand.AMBER, marker(RestaurantStatus.OPEN, 45.5, -73.5, 4).band());
        assertEquals(QueueBand.RED, marker(RestaurantStatus.PAUSED, 45.5, -73.5, 0).band());
    }

    private static RestaurantMarker marker(RestaurantStatus status, double latitude, double longitude, int queued) {
        Restaurant restaurant = new Restaurant(UUID.randomUUID(), "Diner", status, latitude, longitude);
        return RestaurantMarker.from(restaurant, 0, queued, 0, 0);
    }
}
