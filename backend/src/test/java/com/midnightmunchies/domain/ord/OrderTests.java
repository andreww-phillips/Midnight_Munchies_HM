package com.midnightmunchies.domain.ord;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class OrderTests {

	@Test
	void createsPlacedOrderWithDistinctRelationshipsAndMultipleLines() {
		UUID id = UUID.randomUUID();
		UUID customerId = UUID.randomUUID();
		UUID restaurantId = UUID.randomUUID();
		OrderLine first = line();
		OrderLine second = line();
		Instant createdAt = Instant.parse("2026-10-22T22:00:00Z");
		Order order = new Order(id, customerId, restaurantId, List.of(first, second), createdAt);

		assertEquals(id, order.getId());
		assertEquals(customerId, order.getCustomerId());
		assertEquals(restaurantId, order.getRestaurantId());
		assertEquals(List.of(first, second), order.getOrderLines());
		assertNotEquals(first.getMenuItemId(), second.getMenuItemId());
		assertEquals(OrderStatus.PLACED, order.getStatus());
		assertEquals(createdAt, order.getCreatedAt());
	}

	@Test
	void orderLinesCannotBeChangedThroughInputOrGetter() {
		List<OrderLine> input = new ArrayList<>(List.of(line()));
		Order order = order(input);
		input.clear();

		assertEquals(1, order.getOrderLines().size());
		assertThrows(UnsupportedOperationException.class, () -> order.getOrderLines().clear());
		assertThrows(UnsupportedOperationException.class, () -> order.getOrderLines().add(line()));
	}

	@Test
	void rejectsEmptyOrNullOrderLines() {
		assertThrows(IllegalArgumentException.class, () -> order(List.of()));
		assertThrows(NullPointerException.class, () -> order(null));
		assertThrows(NullPointerException.class, () -> order(Arrays.asList(line(), null)));
	}

	@Test
	void rejectsMissingIdentifiersAndTimestamp() {
		UUID id = UUID.randomUUID();
		List<OrderLine> lines = List.of(line());
		Instant createdAt = Instant.now();
		assertThrows(NullPointerException.class, () -> new Order(null, id, id, lines, createdAt));
		assertThrows(NullPointerException.class, () -> new Order(id, null, id, lines, createdAt));
		assertThrows(NullPointerException.class, () -> new Order(id, id, null, lines, createdAt));
		assertThrows(NullPointerException.class, () -> new Order(id, id, id, lines, null));
	}

	@Test
	void statusTypesRepresentTheSpecifiedVocabularyIncludingRefunded() {
		assertArrayEquals(new RestaurantStatus[]{RestaurantStatus.OPEN, RestaurantStatus.PAUSED, RestaurantStatus.CLOSED},
				RestaurantStatus.values());
		assertArrayEquals(new MenuItemStatus[]{MenuItemStatus.AVAILABLE, MenuItemStatus.SOLD_OUT}, MenuItemStatus.values());
		assertArrayEquals(new OrderStatus[]{OrderStatus.PLACED, OrderStatus.ACCEPTED, OrderStatus.PREPARING,
				OrderStatus.READY, OrderStatus.PICKED_UP, OrderStatus.DELIVERED, OrderStatus.REJECTED,
				OrderStatus.CANCELLED, OrderStatus.REFUNDED}, OrderStatus.values());
	}

	private OrderLine line() {
		return new OrderLine(UUID.randomUUID(), 1, new BigDecimal("12.35"));
	}

	private Order order(List<OrderLine> lines) {
		return new Order(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), lines, Instant.now());
	}
}
