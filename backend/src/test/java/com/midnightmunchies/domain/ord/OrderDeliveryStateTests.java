package com.midnightmunchies.domain.ord;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class OrderDeliveryStateTests {

	@Test
	void pickupAndDeliveryAdvanceOrderAndRecordTransitionsExactlyOnce() {
		Order order = order(OrderStatus.READY);

		order.confirmPickup();
		assertEquals(OrderStatus.PICKED_UP, order.getStatus());
		order.confirmDelivery();
		assertEquals(OrderStatus.DELIVERED, order.getStatus());
		assertEquals(List.of(
				new OrderTransition(OrderStatus.READY, OrderStatus.PICKED_UP),
				new OrderTransition(OrderStatus.PICKED_UP, OrderStatus.DELIVERED)), order.pullTransitions());
		assertFalse(order.hasPendingTransitions());
	}

	@ParameterizedTest
	@EnumSource(value = OrderStatus.class, mode = EnumSource.Mode.EXCLUDE, names = {"READY", "REFUNDED"})
	void onlyReadyOrdersCanBePickedUp(OrderStatus status) {
		Order order = order(status);

		IllegalOrderTransitionException error = assertThrows(IllegalOrderTransitionException.class,
				order::confirmPickup);

		assertTrue(error.getMessage().contains("confirm pickup"));
		assertTrue(error.getMessage().contains(status.name()));
		assertEquals(status, order.getStatus());
		assertFalse(order.hasPendingTransitions());
	}

	@ParameterizedTest
	@EnumSource(value = OrderStatus.class, mode = EnumSource.Mode.EXCLUDE, names = {"PICKED_UP", "REFUNDED"})
	void onlyPickedUpOrdersCanBeDelivered(OrderStatus status) {
		Order order = order(status);

		IllegalOrderTransitionException error = assertThrows(IllegalOrderTransitionException.class,
				order::confirmDelivery);

		assertTrue(error.getMessage().contains("confirm delivery"));
		assertTrue(error.getMessage().contains(status.name()));
		assertEquals(status, order.getStatus());
		assertFalse(order.hasPendingTransitions());
	}

	@ParameterizedTest
	@EnumSource(value = OrderStatus.class, names = {"PICKED_UP", "DELIVERED"})
	void deliveryStatesCannotUseRestaurantCommandsOrBeCancelled(OrderStatus status) {
		Order order = order(status);

		assertThrows(IllegalOrderTransitionException.class, order::accept);
		assertThrows(IllegalOrderTransitionException.class, () -> order.reject("Too late"));
		assertThrows(IllegalOrderTransitionException.class, order::startPreparing);
		assertThrows(IllegalOrderTransitionException.class, order::markReady);
		assertThrows(IllegalOrderTransitionException.class, order::cancel);

		assertEquals(status, order.getStatus());
		assertFalse(order.hasPendingTransitions());
	}

	@Test
	void restoredPickedUpOrderCanBeDeliveredAndRestoredDeliveredOrderIsTerminal() {
		Order pickedUp = order(OrderStatus.PICKED_UP);
		assertFalse(pickedUp.hasPendingTransitions());
		pickedUp.confirmDelivery();
		assertEquals(OrderStatus.DELIVERED, pickedUp.getStatus());

		Order delivered = order(OrderStatus.DELIVERED);
		assertFalse(delivered.hasPendingTransitions());
		assertThrows(IllegalOrderTransitionException.class, delivered::confirmPickup);
		assertThrows(IllegalOrderTransitionException.class, delivered::confirmDelivery);
	}

	private Order order(OrderStatus status) {
		OrderLine line = new OrderLine(UUID.randomUUID(), 1, new BigDecimal("12.35"));
		Instant createdAt = Instant.parse("2026-11-12T20:00:00Z");
		return Order.restore(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), List.of(line),
				createdAt, status, status == OrderStatus.PLACED ? null : createdAt.plusSeconds(1));
	}
}
