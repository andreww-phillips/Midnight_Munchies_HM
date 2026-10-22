package com.midnightmunchies.domain.ord.repository;

import com.midnightmunchies.domain.ord.MenuItem;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MenuItemRepository {

	Optional<MenuItem> findById(UUID id);

	/** Inserts or replaces the menu item with this ID and returns the saved entity. */
	MenuItem save(MenuItem menuItem);

	/** Returns an unmodifiable snapshot with no guaranteed ordering. */
	List<MenuItem> findAll();

	/** Returns an unmodifiable snapshot of this restaurant's items, in no guaranteed order. */
	List<MenuItem> findByRestaurantId(UUID restaurantId);
}
