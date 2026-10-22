package com.midnightmunchies.domain.ord.repository;

import com.midnightmunchies.domain.ord.Restaurant;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RestaurantRepository {

	Optional<Restaurant> findById(UUID id);

	/** Inserts or replaces the restaurant with this ID and returns the saved entity. */
	Restaurant save(Restaurant restaurant);

	/** Returns an unmodifiable snapshot with no guaranteed ordering. */
	List<Restaurant> findAll();
}
