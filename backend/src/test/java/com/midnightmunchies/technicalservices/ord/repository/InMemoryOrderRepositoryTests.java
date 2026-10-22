package com.midnightmunchies.technicalservices.ord.repository;

import com.midnightmunchies.domain.ord.Order;
import com.midnightmunchies.domain.ord.OrderLine;
import com.midnightmunchies.domain.ord.OrderStatus;
import com.midnightmunchies.domain.ord.repository.OrderRepository;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class InMemoryOrderRepositoryTests {

	private final OrderRepository repository = new InMemoryOrderRepository();

	@Test
	void savesAndRetrievesOrderWithItsRelationshipsAndPriceSnapshot() {
		UUID customerId = UUID.randomUUID();
		UUID restaurantId = UUID.randomUUID();
		Order order = order(UUID.randomUUID(), customerId, restaurantId);

		assertSame(order, repository.save(order));
		Order retrieved = repository.findById(order.getId()).orElseThrow();

		assertEquals(order.getId(), retrieved.getId());
		assertEquals(customerId, retrieved.getCustomerId());
		assertEquals(restaurantId, retrieved.getRestaurantId());
		assertEquals(order.getOrderLines(), retrieved.getOrderLines());
		assertEquals(new BigDecimal("12.35"), retrieved.getOrderLines().getFirst().getUnitPrice());
		assertEquals(OrderStatus.PLACED, retrieved.getStatus());
		assertEquals(order.getCreatedAt(), retrieved.getCreatedAt());
	}

	@Test
	void filtersOrdersIndependentlyByCustomerAndRestaurant() {
		UUID customerId = UUID.randomUUID();
		UUID restaurantId = UUID.randomUUID();
		Order shared = order(UUID.randomUUID(), customerId, restaurantId);
		Order sameCustomer = order(UUID.randomUUID(), customerId, UUID.randomUUID());
		Order sameRestaurant = order(UUID.randomUUID(), UUID.randomUUID(), restaurantId);
		repository.save(shared);
		repository.save(sameCustomer);
		repository.save(sameRestaurant);

		assertEquals(Set.of(shared, sameCustomer), Set.copyOf(repository.findByCustomerId(customerId)));
		assertEquals(Set.of(shared, sameRestaurant), Set.copyOf(repository.findByRestaurantId(restaurantId)));
		assertEquals(Set.of(shared, sameCustomer, sameRestaurant), Set.copyOf(repository.findAll()));
	}

	@Test
	void returnsEmptyForMissingIds() {
		assertTrue(repository.findById(UUID.randomUUID()).isEmpty());
		assertTrue(repository.findAll().isEmpty());
		assertTrue(repository.findByCustomerId(UUID.randomUUID()).isEmpty());
		assertTrue(repository.findByRestaurantId(UUID.randomUUID()).isEmpty());
	}

	@Test
	void saveReplacesByIdAndQueryResultsAreUnmodifiableSnapshots() {
		UUID id = UUID.randomUUID();
		UUID customerId = UUID.randomUUID();
		UUID restaurantId = UUID.randomUUID();
		Order original = order(id, customerId, restaurantId);
		repository.save(original);
		List<Order> allSnapshot = repository.findAll();
		List<Order> customerSnapshot = repository.findByCustomerId(customerId);
		List<Order> restaurantSnapshot = repository.findByRestaurantId(restaurantId);
		Order replacement = order(id, customerId, restaurantId);
		repository.save(replacement);

		assertSame(replacement, repository.findById(id).orElseThrow());
		assertEquals(List.of(replacement), repository.findAll());
		assertEquals(List.of(replacement), repository.findByCustomerId(customerId));
		assertEquals(List.of(replacement), repository.findByRestaurantId(restaurantId));
		assertEquals(List.of(original), allSnapshot);
		assertEquals(List.of(original), customerSnapshot);
		assertEquals(List.of(original), restaurantSnapshot);
		assertThrows(UnsupportedOperationException.class, allSnapshot::clear);
		assertThrows(UnsupportedOperationException.class, customerSnapshot::clear);
		assertThrows(UnsupportedOperationException.class, restaurantSnapshot::clear);
	}

	@Test
	void rejectsNullArguments() {
		assertThrows(NullPointerException.class, () -> repository.save(null));
		assertThrows(NullPointerException.class, () -> repository.findById(null));
		assertThrows(NullPointerException.class, () -> repository.findByRestaurantId(null));
		assertThrows(NullPointerException.class, () -> repository.findByCustomerId(null));
	}

	private Order order(UUID id, UUID customerId, UUID restaurantId) {
		OrderLine line = new OrderLine(UUID.randomUUID(), 2, new BigDecimal("12.35"));
		return new Order(id, customerId, restaurantId, List.of(line), Instant.parse("2026-10-22T22:00:00Z"));
	}
}
