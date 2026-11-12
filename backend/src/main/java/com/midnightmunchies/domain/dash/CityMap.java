package com.midnightmunchies.domain.dash;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public record CityMap(MapBounds bounds, List<RestaurantMarker> restaurants, List<CourierMarker> couriers) {

    public CityMap {
        restaurants = List.copyOf(Objects.requireNonNull(restaurants, "restaurants must not be null"));
        couriers = List.copyOf(Objects.requireNonNull(couriers, "couriers must not be null"));
    }

    public static CityMap of(List<RestaurantMarker> restaurants, List<CourierMarker> couriers) {
        List<CourierMarker> onDuty = couriers.stream().filter(CourierMarker::isOnDuty).toList();
        List<MapMarker> all = new ArrayList<>(restaurants);
        all.addAll(onDuty);
        return new CityMap(MapBounds.enclosing(all), restaurants, onDuty);
    }
}
