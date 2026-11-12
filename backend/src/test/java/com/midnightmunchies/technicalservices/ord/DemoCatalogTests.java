package com.midnightmunchies.technicalservices.ord;

import com.midnightmunchies.domain.ord.Restaurant;
import com.midnightmunchies.technicalservices.ord.repository.InMemoryMenuItemRepository;
import com.midnightmunchies.technicalservices.ord.repository.InMemoryRestaurantRepository;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DemoCatalogTests {

    @Test
    void seededRestaurantsHaveDistinctCoordinatesInsideMontreal() {
        InMemoryRestaurantRepository restaurants = new InMemoryRestaurantRepository();
        new DemoCatalog(restaurants, new InMemoryMenuItemRepository());

        List<Restaurant> seeded = restaurants.findAll();
        Set<String> positions = seeded.stream()
                .map(restaurant -> restaurant.getLatitude() + "," + restaurant.getLongitude())
                .collect(Collectors.toSet());

        assertEquals(seeded.size(), positions.size());
        for (Restaurant restaurant : seeded) {
            assertTrue(restaurant.getLatitude() > 45.4 && restaurant.getLatitude() < 45.7);
            assertTrue(restaurant.getLongitude() > -73.9 && restaurant.getLongitude() < -73.4);
        }
    }
}
