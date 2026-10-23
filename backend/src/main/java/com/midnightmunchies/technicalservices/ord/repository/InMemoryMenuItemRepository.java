package com.midnightmunchies.technicalservices.ord.repository;

import com.midnightmunchies.domain.ord.MenuItem;
import com.midnightmunchies.domain.ord.CatalogNames;
import com.midnightmunchies.domain.ord.repository.MenuItemRepository;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class InMemoryMenuItemRepository implements MenuItemRepository {

	private final Map<UUID, MenuItem> menuItems = new ConcurrentHashMap<>();

	@Override
	public Optional<MenuItem> findById(UUID id) {
		return Optional.ofNullable(menuItems.get(Objects.requireNonNull(id, "id must not be null")));
	}

	@Override
	public synchronized MenuItem save(MenuItem menuItem) {
		Objects.requireNonNull(menuItem, "menuItem must not be null");
		CatalogNames.requireUniqueMenuItem(menuItem, menuItems.values());
		menuItems.put(menuItem.getId(), menuItem);
		return menuItem;
	}

	@Override
	public boolean deleteById(UUID id) {
		return menuItems.remove(Objects.requireNonNull(id, "id must not be null")) != null;
	}

	@Override
	public List<MenuItem> findAll() {
		return List.copyOf(menuItems.values());
	}

	@Override
	public List<MenuItem> findByRestaurantId(UUID restaurantId) {
		Objects.requireNonNull(restaurantId, "restaurantId must not be null");
		return menuItems.values().stream()
				.filter(menuItem -> menuItem.getRestaurantId().equals(restaurantId))
				.toList();
	}
}
