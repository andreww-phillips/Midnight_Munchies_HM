package com.midnightmunchies.domain.ord;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class OrderAcceptedAtTests {

	private static final Instant TIME = Instant.parse("2026-10-23T20:00:00Z");

	@Test
	void newOrderHasNoAcceptedAt() {
		assertNull(order().getAcceptedAt());
	}

	@Test
	void acceptedOrderRecordsAcceptedAtOnce() {
		Order order = order();
		order.accept();
		order.recordAcceptedAt(TIME);

		assertEquals(TIME, order.getAcceptedAt());
		assertThrows(IllegalStateException.class, () -> order.recordAcceptedAt(TIME.plusSeconds(1)));
		assertEquals(TIME, order.getAcceptedAt());
	}

	@Test
	void onlyAcceptedOrdersCanRecordAcceptedAt() {
		Order placed = order();
		assertThrows(IllegalOrderTransitionException.class, () -> placed.recordAcceptedAt(TIME));

		Order cancelled = order();
		cancelled.cancel();
		assertThrows(IllegalOrderTransitionException.class, () -> cancelled.recordAcceptedAt(TIME));

		Order accepted = order();
		accepted.accept();
		assertThrows(NullPointerException.class, () -> accepted.recordAcceptedAt(null));
	}

	@Test
	void restoreRebuildsStatusAndAcceptedAt() {
		UUID id = UUID.randomUUID();
		Order restored = Order.restore(id, UUID.randomUUID(), UUID.randomUUID(), List.of(line()),
				TIME.minusSeconds(60), OrderStatus.ACCEPTED, TIME);

		assertEquals(id, restored.getId());
		assertEquals(OrderStatus.ACCEPTED, restored.getStatus());
		assertEquals(TIME, restored.getAcceptedAt());
		restored.startPreparing();
		assertEquals(OrderStatus.PREPARING, restored.getStatus());
	}

	@Test
	void restoreKeepsTransitionRulesOfStoredStatus() {
		Order ready = Order.restore(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), List.of(line()),
				TIME, OrderStatus.READY, TIME);

		assertEquals(OrderStatus.READY, ready.getStatus());
		assertThrows(IllegalOrderTransitionException.class, ready::cancel);
	}

	@Test
	void restoreRejectsInvalidStoredData() {
		assertThrows(IllegalArgumentException.class, () -> Order.restore(UUID.randomUUID(), UUID.randomUUID(),
				UUID.randomUUID(), List.of(line()), TIME, OrderStatus.ACCEPTED, null));
		assertThrows(IllegalArgumentException.class, () -> Order.restore(UUID.randomUUID(), UUID.randomUUID(),
				UUID.randomUUID(), List.of(line()), TIME, OrderStatus.DELIVERED, null));
		assertThrows(NullPointerException.class, () -> Order.restore(UUID.randomUUID(), UUID.randomUUID(),
				UUID.randomUUID(), List.of(line()), TIME, null, null));
	}

	private Order order() {
		return new Order(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), List.of(line()), TIME);
	}

	private OrderLine line() {
		return new OrderLine(UUID.randomUUID(), 1, new BigDecimal("9.99"));
	}
}
