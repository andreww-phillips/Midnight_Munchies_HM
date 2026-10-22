package com.midnightmunchies.technicalservices.ord.repository;

import com.midnightmunchies.domain.ord.Restaurant;
import com.midnightmunchies.domain.ord.RestaurantStatus;
import com.midnightmunchies.domain.ord.repository.RestaurantRepository;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class InMemoryRestaurantRepositoryTests {

	private final RestaurantRepository repository = new InMemoryRestaurantRepository();

	@Test
	void savesAndRetrievesDistinctRestaurants() {
		Restaurant first = restaurant(UUID.randomUUID(), RestaurantStatus.OPEN);
		Restaurant second = restaurant(UUID.randomUUID(), RestaurantStatus.CLOSED);

		assertSame(first, repository.save(first));
		repository.save(second);

		assertSame(first, repository.findById(first.getId()).orElseThrow());
		assertSame(second, repository.findById(second.getId()).orElseThrow());
		assertEquals(Set.of(first, second), Set.copyOf(repository.findAll()));
	}

	@Test
	void returnsEmptyForMissingIdAndEmptyStorage() {
		assertTrue(repository.findById(UUID.randomUUID()).isEmpty());
		assertTrue(repository.findAll().isEmpty());
	}

	@Test
	void saveReplacesExistingIdAndFindAllReturnsUnmodifiableSnapshot() {
		UUID id = UUID.randomUUID();
		Restaurant original = restaurant(id, RestaurantStatus.OPEN);
		repository.save(original);
		List<Restaurant> snapshot = repository.findAll();
		Restaurant replacement = restaurant(id, RestaurantStatus.PAUSED);
		repository.save(replacement);

		assertSame(replacement, repository.findById(id).orElseThrow());
		assertEquals(List.of(replacement), repository.findAll());
		assertEquals(List.of(original), snapshot);
		assertThrows(UnsupportedOperationException.class, snapshot::clear);
	}

	@Test
	void rejectsNullArguments() {
		assertThrows(NullPointerException.class, () -> repository.save(null));
		assertThrows(NullPointerException.class, () -> repository.findById(null));
	}

	private Restaurant restaurant(UUID id, RestaurantStatus status) {
		return new Restaurant(id, "Diner", status, 45.5, -73.5);
	}
}
