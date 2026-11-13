package com.midnightmunchies.domain.dash;

import com.midnightmunchies.domain.ord.Restaurant;
import com.midnightmunchies.domain.ord.RestaurantStatus;

import java.util.Objects;
import java.util.UUID;

public record RestaurantMarker(
        UUID id,
        UUID ownerId,
        String name,
        RestaurantStatus status,
        double latitude,
        double longitude,
        int pendingCount,
        int queuedCount,
        int preparingCount,
        int readyCount,
        QueueBand band
) implements MapMarker {

    public RestaurantMarker {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(name, "name must not be null");
        Objects.requireNonNull(status, "status must not be null");
        Objects.requireNonNull(band, "band must not be null");
        if (pendingCount < 0 || queuedCount < 0 || preparingCount < 0 || readyCount < 0) {
            throw new IllegalArgumentException("counts must not be negative");
        }
    }

    public static RestaurantMarker from(Restaurant restaurant, int pending, int queued, int preparing, int ready) {
        Objects.requireNonNull(restaurant, "restaurant must not be null");
        return new RestaurantMarker(
                restaurant.getId(),
                restaurant.getOwnerId(),
                restaurant.getName(),
                restaurant.getStatus(),
                restaurant.getLatitude(),
                restaurant.getLongitude(),
                pending,
                queued,
                preparing,
                ready,
                QueueBand.of(restaurant.getStatus(), queued)
        );
    }
}
