package com.midnightmunchies.domain.ord;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class OrderTransitionRecordingTests {

	@Test
	void eachSuccessfulTransitionIsRecordedOnceWithPreviousAndNewStatus() {
		Order order = order();
		order.accept();
		order.startPreparing();
		order.markReady();

		assertEquals(List.of(
				new OrderTransition(OrderStatus.PLACED, OrderStatus.ACCEPTED),
				new OrderTransition(OrderStatus.ACCEPTED, OrderStatus.PREPARING),
				new OrderTransition(OrderStatus.PREPARING, OrderStatus.READY)), order.pullTransitions());
	}

	@Test
	void rejectAndCancelAreRecorded() {
		Order rejected = order();
		rejected.reject();
		Order cancelled = order();
		cancelled.cancel();

		assertEquals(List.of(new OrderTransition(OrderStatus.PLACED, OrderStatus.REJECTED)), rejected.pullTransitions());
		assertEquals(List.of(new OrderTransition(OrderStatus.PLACED, OrderStatus.CANCELLED)), cancelled.pullTransitions());
	}

	@Test
	void failedTransitionsRecordNothingAndKeepTheState() {
		Order order = order();

		assertThrows(IllegalOrderTransitionException.class, order::markReady);
		assertThrows(IllegalOrderTransitionException.class, order::startPreparing);

		assertFalse(order.hasPendingTransitions());
		assertTrue(order.pullTransitions().isEmpty());
		assertEquals(OrderStatus.PLACED, order.getStatus());
	}

	@Test
	void failedTransitionAfterSuccessfulOneKeepsOnlyTheSuccessfulRecord() {
		Order order = order();
		order.accept();

		assertThrows(IllegalOrderTransitionException.class, order::accept);

		assertEquals(List.of(new OrderTransition(OrderStatus.PLACED, OrderStatus.ACCEPTED)), order.pullTransitions());
	}

	@Test
	void creatingOrRestoringAnOrderRecordsNothing() {
		Order restored = Order.restore(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), List.of(line()),
				Instant.now(), OrderStatus.ACCEPTED, Instant.now());

		assertFalse(order().hasPendingTransitions());
		assertFalse(restored.hasPendingTransitions());
	}

	@Test
	void pullingClearsPendingTransitions() {
		Order order = order();
		order.accept();

		assertTrue(order.hasPendingTransitions());
		assertEquals(1, order.pullTransitions().size());
		assertFalse(order.hasPendingTransitions());
		assertTrue(order.pullTransitions().isEmpty());
	}

	@Test
	void pulledListCannotBeModified() {
		Order order = order();
		order.accept();

		List<OrderTransition> pulled = order.pullTransitions();

		assertThrows(UnsupportedOperationException.class, pulled::clear);
	}

	private Order order() {
		return new Order(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), List.of(line()), Instant.now());
	}

	private OrderLine line() {
		return new OrderLine(UUID.randomUUID(), 1, new BigDecimal("4.00"));
	}
}
