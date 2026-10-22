package com.midnightmunchies.technicalservices.ord.repository;

import com.midnightmunchies.domain.ord.MenuItem;
import com.midnightmunchies.domain.ord.MenuItemStatus;
import com.midnightmunchies.domain.ord.OrderLine;
import com.midnightmunchies.domain.ord.repository.MenuItemRepository;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class InMemoryMenuItemRepositoryTests {

	private final MenuItemRepository repository = new InMemoryMenuItemRepository();

	@Test
	void savesItemsAndFiltersByRestaurantWithoutMixingMenus() {
		UUID restaurantId = UUID.randomUUID();
		MenuItem first = item(UUID.randomUUID(), restaurantId, "12.35", MenuItemStatus.AVAILABLE);
		MenuItem second = item(UUID.randomUUID(), restaurantId, "5.00", MenuItemStatus.SOLD_OUT);
		MenuItem other = item(UUID.randomUUID(), UUID.randomUUID(), "8.00", MenuItemStatus.AVAILABLE);

		assertSame(first, repository.save(first));
		repository.save(second);
		repository.save(other);

		assertSame(first, repository.findById(first.getId()).orElseThrow());
		assertSame(second, repository.findById(second.getId()).orElseThrow());
		assertEquals(Set.of(first, second), Set.copyOf(repository.findByRestaurantId(restaurantId)));
		assertEquals(List.of(other), repository.findByRestaurantId(other.getRestaurantId()));
		assertEquals(Set.of(first, second, other), Set.copyOf(repository.findAll()));
	}

	@Test
	void returnsEmptyForMissingIds() {
		assertTrue(repository.findById(UUID.randomUUID()).isEmpty());
		assertTrue(repository.findAll().isEmpty());
		assertTrue(repository.findByRestaurantId(UUID.randomUUID()).isEmpty());
	}

	@Test
	void replacingMenuItemKeepsCapturedOrderPriceAndQuerySnapshots() {
		UUID itemId = UUID.randomUUID();
		UUID restaurantId = UUID.randomUUID();
		MenuItem original = item(itemId, restaurantId, "12.35", MenuItemStatus.AVAILABLE);
		repository.save(original);
		OrderLine line = new OrderLine(itemId, 2, original.getPrice());
		List<MenuItem> allSnapshot = repository.findAll();
		List<MenuItem> menuSnapshot = repository.findByRestaurantId(restaurantId);
		MenuItem replacement = item(itemId, restaurantId, "15.00", MenuItemStatus.SOLD_OUT);
		repository.save(replacement);

		assertSame(replacement, repository.findById(itemId).orElseThrow());
		assertEquals(List.of(replacement), repository.findByRestaurantId(restaurantId));
		assertEquals(1, repository.findAll().size());
		assertEquals(new BigDecimal("12.35"), line.getUnitPrice());
		assertEquals(List.of(original), allSnapshot);
		assertEquals(List.of(original), menuSnapshot);
		assertThrows(UnsupportedOperationException.class, allSnapshot::clear);
		assertThrows(UnsupportedOperationException.class, menuSnapshot::clear);
	}

	@Test
	void rejectsNullArguments() {
		assertThrows(NullPointerException.class, () -> repository.save(null));
		assertThrows(NullPointerException.class, () -> repository.findById(null));
		assertThrows(NullPointerException.class, () -> repository.findByRestaurantId(null));
	}

	private MenuItem item(UUID id, UUID restaurantId, String price, MenuItemStatus status) {
		return new MenuItem(id, restaurantId, "Poutine", new BigDecimal(price), status);
	}
}
