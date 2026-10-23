package com.midnightmunchies.technicalservices.ord.repository;

import com.midnightmunchies.domain.ord.Order;
import com.midnightmunchies.domain.ord.OrderLine;
import com.midnightmunchies.domain.ord.repository.OrderRepository;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class InMemoryOrderRepositoryPreparationQueueTests {

	private static final Instant START = Instant.parse("2026-10-23T20:00:00Z");

	private final OrderRepository repository = new InMemoryOrderRepository();
	private final UUID restaurantId = UUID.randomUUID();

	@Test
	void returnsAcceptedQueuedOrdersOldestAcceptedFirst() {
		Order third = queued(restaurantId, START.plusSeconds(20));
		Order first = queued(restaurantId, START);
		Order second = queued(restaurantId, START.plusSeconds(10));

		assertEquals(List.of(first, second, third), repository.findPreparationQueue(restaurantId));
	}

	@Test
	void sortsByAcceptedAtNotCreatedAt() {
		Order placedLaterAcceptedFirst = queued(restaurantId, START, START.plusSeconds(100));
		Order placedFirstAcceptedLater = queued(restaurantId, START.plusSeconds(5), START.minusSeconds(100));

		assertEquals(List.of(placedLaterAcceptedFirst, placedFirstAcceptedLater),
				repository.findPreparationQueue(restaurantId));
	}

	@Test
	void excludesOtherRestaurantsAndNonWaitingOrders() {
		Order waiting = queued(restaurantId, START);
		queued(UUID.randomUUID(), START.minusSeconds(1));
		repository.save(order(restaurantId, START));
		Order accepted = order(restaurantId, START);
		accepted.accept();
		repository.save(accepted);
		Order preparing = queued(restaurantId, START.minusSeconds(5));
		preparing.startPreparing();
		Order cancelled = queued(restaurantId, START.minusSeconds(6));
		cancelled.cancel();

		assertEquals(List.of(waiting), repository.findPreparationQueue(restaurantId));
	}

	@Test
	void emptyWhenNothingIsWaiting() {
		assertTrue(repository.findPreparationQueue(restaurantId).isEmpty());
		assertThrows(NullPointerException.class, () -> repository.findPreparationQueue(null));
	}

	private Order queued(UUID restaurant, Instant acceptedAt) {
		return queued(restaurant, acceptedAt, START.minusSeconds(3600));
	}

	private Order queued(UUID restaurant, Instant acceptedAt, Instant createdAt) {
		Order order = order(restaurant, createdAt);
		order.accept();
		order.recordAcceptedAt(acceptedAt);
		return repository.save(order);
	}

	private Order order(UUID restaurant, Instant createdAt) {
		return new Order(UUID.randomUUID(), UUID.randomUUID(), restaurant,
				List.of(new OrderLine(UUID.randomUUID(), 1, new BigDecimal("5.00"))), createdAt);
	}
}
