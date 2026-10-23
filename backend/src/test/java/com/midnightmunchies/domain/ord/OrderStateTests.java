package com.midnightmunchies.domain.ord;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;

class OrderStateTests {

	private static final List<Consumer<Order>> ALL_TRANSITIONS = List.of(
			Order::accept, order -> order.reject("reason"), Order::startPreparing, Order::markReady, Order::cancel);

	@Test
	void happyPathMovesPlacedAcceptedPreparingReady() {
		Order order = order();
		assertEquals(OrderStatus.PLACED, order.getStatus());
		order.accept();
		assertEquals(OrderStatus.ACCEPTED, order.getStatus());
		order.startPreparing();
		assertEquals(OrderStatus.PREPARING, order.getStatus());
		order.markReady();
		assertEquals(OrderStatus.READY, order.getStatus());
	}

	@Test
	void placedOrderCanBeRejectedOrCancelled() {
		Order rejected = order();
		rejected.reject("Out of stock");
		assertEquals(OrderStatus.REJECTED, rejected.getStatus());

		Order cancelled = order();
		cancelled.cancel();
		assertEquals(OrderStatus.CANCELLED, cancelled.getStatus());
	}

	@Test
	void acceptedAndPreparingOrdersCanBeCancelled() {
		Order accepted = order();
		accepted.accept();
		accepted.cancel();
		assertEquals(OrderStatus.CANCELLED, accepted.getStatus());

		Order preparing = order();
		preparing.accept();
		preparing.startPreparing();
		preparing.cancel();
		assertEquals(OrderStatus.CANCELLED, preparing.getStatus());
	}

	@Test
	void skippedTransitionsAreRejectedAndLeaveStatusUnchanged() {
		Order placed = order();
		assertThrows(IllegalOrderTransitionException.class, placed::startPreparing);
		assertThrows(IllegalOrderTransitionException.class, placed::markReady);
		assertEquals(OrderStatus.PLACED, placed.getStatus());

		Order accepted = order();
		accepted.accept();
		assertThrows(IllegalOrderTransitionException.class, accepted::markReady);
		assertThrows(IllegalOrderTransitionException.class, accepted::accept);
		assertThrows(IllegalOrderTransitionException.class, () -> accepted.reject("Too late"));
		assertEquals(OrderStatus.ACCEPTED, accepted.getStatus());
	}

	@Test
	void rejectedAndCancelledOrdersCannotBeAdvanced() {
		Order rejected = order();
		rejected.reject("Out of stock");
		Order cancelled = order();
		cancelled.cancel();
		for (Consumer<Order> transition : ALL_TRANSITIONS) {
			assertThrows(IllegalOrderTransitionException.class, () -> transition.accept(rejected));
			assertThrows(IllegalOrderTransitionException.class, () -> transition.accept(cancelled));
		}
		assertEquals(OrderStatus.REJECTED, rejected.getStatus());
		assertEquals(OrderStatus.CANCELLED, cancelled.getStatus());
	}

	@Test
	void readyOrderCannotBeAdvancedOrCancelledHere() {
		Order ready = order();
		ready.accept();
		ready.startPreparing();
		ready.markReady();
		for (Consumer<Order> transition : ALL_TRANSITIONS) {
			assertThrows(IllegalOrderTransitionException.class, () -> transition.accept(ready));
		}
		assertEquals(OrderStatus.READY, ready.getStatus());
	}

	@Test
	void rejectionRecordsTheReasonAndRequiresOne() {
		Order order = order();
		assertNull(order.getRejectionReason());
		assertThrows(IllegalArgumentException.class, () -> order.reject(null));
		assertThrows(IllegalArgumentException.class, () -> order.reject("  "));
		assertEquals(OrderStatus.PLACED, order.getStatus());

		order.reject("  Kitchen closed ");

		assertEquals(OrderStatus.REJECTED, order.getStatus());
		assertEquals("Kitchen closed", order.getRejectionReason());
	}

	@Test
	void failedRejectionDoesNotRecordAReason() {
		Order order = order();
		order.accept();

		assertThrows(IllegalOrderTransitionException.class, () -> order.reject("Too late"));

		assertNull(order.getRejectionReason());
		assertEquals(OrderStatus.ACCEPTED, order.getStatus());
	}

	private Order order() {
		OrderLine line = new OrderLine(UUID.randomUUID(), 1, new BigDecimal("12.35"));
		return new Order(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), List.of(line), Instant.now());
	}
}
