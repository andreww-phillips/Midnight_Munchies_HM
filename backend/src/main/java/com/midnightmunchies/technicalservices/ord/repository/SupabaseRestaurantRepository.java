package com.midnightmunchies.technicalservices.ord.repository;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.midnightmunchies.domain.ord.Restaurant;
import com.midnightmunchies.domain.ord.RestaurantStatus;
import com.midnightmunchies.domain.ord.repository.RestaurantRepository;
import org.springframework.core.ParameterizedTypeReference;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public class SupabaseRestaurantRepository implements RestaurantRepository {
    private static final ParameterizedTypeReference<List<Row>> ROWS = new ParameterizedTypeReference<>() {};
    private final SupabaseCatalogClient catalog;

    public SupabaseRestaurantRepository(SupabaseCatalogClient catalog) { this.catalog = catalog; }

    @Override
    public Optional<Restaurant> findById(UUID id) {
        return catalog.select("Restaurant", Map.of("id", "eq." + Objects.requireNonNull(id)), ROWS)
                .stream().findFirst().map(Row::restaurant);
    }

    @Override
    public List<Restaurant> findAll() {
        return catalog.select("Restaurant", Map.of(), ROWS).stream().map(Row::restaurant).toList();
    }

    @Override
    public Restaurant save(Restaurant restaurant) {
        // The existing registration schema uses Restaurant.id = the staff account's id.
        if (!restaurant.getId().equals(restaurant.getOwnerId())) {
            throw new IllegalArgumentException("Restaurant ID must match its registered staff account");
        }
        catalog.save("Restaurant", new Row(restaurant.getId(), restaurant.getName(), restaurant.getLatitude(),
                restaurant.getLongitude(), restaurant.getLogo() == null ? "" : restaurant.getLogo(), restaurant.getMenu()));
        return restaurant;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Row(UUID id, @JsonProperty("restaurant_name") String name,
                      @JsonProperty("restaurant_latitude") Double latitude,
                      @JsonProperty("restaurant_longitude") Double longitude,
                      @JsonProperty("restaurant_logo") String logo, String menu) {
        Restaurant restaurant() {
            return new Restaurant(id, id, name, RestaurantStatus.OPEN,
                    latitude == null ? 0 : latitude, longitude == null ? 0 : longitude, logo, menu);
        }
    }
}
