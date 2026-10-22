package com.midnightmunchies.technicalservices.ord.repository;

import com.midnightmunchies.domain.ord.Restaurant;
import com.midnightmunchies.domain.ord.repository.RestaurantRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Repository
public class InMemoryRestaurantRepository implements RestaurantRepository {

	private final Map<UUID, Restaurant> restaurants = new ConcurrentHashMap<>();

	@Override
	public Optional<Restaurant> findById(UUID id) {
		return Optional.ofNullable(restaurants.get(Objects.requireNonNull(id, "id must not be null")));
	}

	@Override
	public Restaurant save(Restaurant restaurant) {
		Objects.requireNonNull(restaurant, "restaurant must not be null");
		restaurants.put(restaurant.getId(), restaurant);
		return restaurant;
	}

	@Override
	public List<Restaurant> findAll() {
		return List.copyOf(restaurants.values());
	}
}
