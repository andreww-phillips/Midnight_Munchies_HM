package com.midnightmunchies.domain.dlv;

import com.midnightmunchies.domain.ord.IllegalOrderTransitionException;
import com.midnightmunchies.domain.ord.Order;
import com.midnightmunchies.domain.ord.OrderLine;
import com.midnightmunchies.domain.ord.OrderStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CourierTests {

	private static final Location LOCATION = new Location(45.497, -73.579);

	@Test
	void newCourierHasIdentityLocationAndStartsOfflineWithoutAnOrder() {
		UUID id = UUID.randomUUID();
		Courier courier = new Courier(id, LOCATION);

		assertEquals(id, courier.getId());
		assertEquals(LOCATION, courier.getLocation());
		assertEquals(CourierState.OFFLINE, courier.getState());
		assertTrue(courier.getAssignedOrder().isEmpty());
		assertArrayEquals(new CourierState[]{CourierState.OFFLINE, CourierState.IDLE,
				CourierState.ASSIGNED, CourierState.CARRYING}, CourierState.values());
	}

	@Test
	void offlineCourierCanGoOnDutyAndIdleCourierCanGoOffDuty() {
		Courier courier = courier(CourierState.OFFLINE);

		courier.goOnDuty();
		assertEquals(CourierState.IDLE, courier.getState());
		assertTrue(courier.getAssignedOrder().isEmpty());

		courier.goOffDuty();
		assertEquals(CourierState.OFFLINE, courier.getState());
		assertTrue(courier.getAssignedOrder().isEmpty());

		courier.goOnDuty();
		assertEquals(CourierState.IDLE, courier.getState());
	}

	@Test
	void acceptsReadyOrderPicksUpAndDeliversThenReturnsToIdle() {
		Courier courier = courier(CourierState.IDLE);
		Order order = readyOrder();

		courier.acceptOrder(order);
		assertEquals(CourierState.ASSIGNED, courier.getState());
		assertSame(order, courier.getAssignedOrder().orElseThrow());
		assertEquals(OrderStatus.READY, order.getStatus());

		courier.confirmPickup();
		assertEquals(CourierState.CARRYING, courier.getState());
		assertSame(order, courier.getAssignedOrder().orElseThrow());
		assertEquals(OrderStatus.PICKED_UP, order.getStatus());

		courier.confirmDelivery();
		assertEquals(CourierState.IDLE, courier.getState());
		assertTrue(courier.getAssignedOrder().isEmpty());
		assertEquals(OrderStatus.DELIVERED, order.getStatus());
		assertEquals(LOCATION, courier.getLocation());
	}

	@Test
	void deliveredCourierCanAcceptAnotherOrderAndLaterGoOffDuty() {
		Courier courier = courier(CourierState.CARRYING);
		courier.confirmDelivery();
		Order next = readyOrder();

		courier.acceptOrder(next);
		assertSame(next, courier.getAssignedOrder().orElseThrow());
		courier.confirmPickup();
		courier.confirmDelivery();
		courier.goOffDuty();

		assertEquals(CourierState.OFFLINE, courier.getState());
		assertTrue(courier.getAssignedOrder().isEmpty());
		assertEquals(OrderStatus.DELIVERED, next.getStatus());
	}

	@ParameterizedTest
	@CsvSource({
			"OFFLINE, go off duty", "OFFLINE, accept an order", "OFFLINE, confirm pickup",
			"OFFLINE, confirm delivery", "IDLE, go on duty", "IDLE, confirm pickup",
			"IDLE, confirm delivery", "ASSIGNED, go on duty", "ASSIGNED, go off duty",
			"ASSIGNED, accept an order", "ASSIGNED, confirm delivery", "CARRYING, go on duty",
			"CARRYING, go off duty", "CARRYING, accept an order", "CARRYING, confirm pickup"
	})
	void invalidCommandsExplainFailureAndPreserveStateAndAssignment(CourierState state, String action) {
		Courier courier = courier(state);
		Optional<Order> assignment = courier.getAssignedOrder();
		assignment.ifPresent(Order::pullTransitions);
		Optional<OrderStatus> orderStatus = assignment.map(Order::getStatus);
		Order offered = readyOrder();
		offered.pullTransitions();

		IllegalCourierTransitionException error = assertThrows(IllegalCourierTransitionException.class, () -> {
			switch (action) {
				case "go on duty" -> courier.goOnDuty();
				case "go off duty" -> courier.goOffDuty();
				case "accept an order" -> courier.acceptOrder(offered);
				case "confirm pickup" -> courier.confirmPickup();
				case "confirm delivery" -> courier.confirmDelivery();
				default -> throw new AssertionError("Unknown command " + action);
			}
		});

		assertTrue(error.getMessage().contains(action));
		assertTrue(error.getMessage().contains(state.name()));
		assertEquals(state, courier.getState());
		assertEquals(assignment, courier.getAssignedOrder());
		assertEquals(orderStatus, courier.getAssignedOrder().map(Order::getStatus));
		assignment.ifPresent(order -> assertFalse(order.hasPendingTransitions()));
		assertEquals(OrderStatus.READY, offered.getStatus());
		assertFalse(offered.hasPendingTransitions());
	}

	@ParameterizedTest
	@EnumSource(value = OrderStatus.class, mode = EnumSource.Mode.EXCLUDE, names = "READY")
	void cannotAcceptAnOrderUnlessItIsReady(OrderStatus status) {
		Courier courier = courier(CourierState.IDLE);
		Order order = mock(Order.class);
		UUID orderId = UUID.randomUUID();
		when(order.getId()).thenReturn(orderId);
		when(order.getStatus()).thenReturn(status);

		IllegalCourierTransitionException error = assertThrows(IllegalCourierTransitionException.class,
				() -> courier.acceptOrder(order));

		assertTrue(error.getMessage().contains(orderId.toString()));
		assertTrue(error.getMessage().contains("must be READY but is " + status));
		assertEquals(CourierState.IDLE, courier.getState());
		assertTrue(courier.getAssignedOrder().isEmpty());
	}

	@ParameterizedTest
	@EnumSource(value = OrderStatus.class, mode = EnumSource.Mode.EXCLUDE, names = "READY")
	void pickupRechecksReadinessAndPreservesAssignmentWhenOrderIsNoLongerReady(OrderStatus status) {
		Courier courier = courier(CourierState.IDLE);
		Order order = mock(Order.class);
		when(order.getStatus()).thenReturn(OrderStatus.READY);
		courier.acceptOrder(order);
		// Model a changed order snapshot after the offer was accepted.
		when(order.getStatus()).thenReturn(status);

		IllegalCourierTransitionException error = assertThrows(IllegalCourierTransitionException.class,
				courier::confirmPickup);

		assertTrue(error.getMessage().contains("must be READY but is " + status));
		assertEquals(CourierState.ASSIGNED, courier.getState());
		assertSame(order, courier.getAssignedOrder().orElseThrow());
		verify(order, never()).confirmPickup();
	}

	@Test
	void failedOrderDeliveryDoesNotClearAssignmentOrMakeCourierIdle() {
		Courier courier = courier(CourierState.CARRYING);
		Order order = courier.getAssignedOrder().orElseThrow();
		order.confirmDelivery();
		order.pullTransitions();

		assertThrows(IllegalOrderTransitionException.class, courier::confirmDelivery);

		assertEquals(CourierState.CARRYING, courier.getState());
		assertSame(order, courier.getAssignedOrder().orElseThrow());
		assertFalse(order.hasPendingTransitions());
	}

	@ParameterizedTest
	@EnumSource(CourierState.class)
	void locationCanBeUpdatedWithoutChangingStateOrAssignment(CourierState state) {
		Courier courier = courier(state);
		Optional<Order> assignment = courier.getAssignedOrder();
		Location updated = new Location(45.5, -73.6);

		courier.updateLocation(updated);

		assertEquals(updated, courier.getLocation());
		assertEquals(state, courier.getState());
		assertEquals(assignment, courier.getAssignedOrder());
		assertThrows(NullPointerException.class, () -> courier.updateLocation(null));
		assertEquals(updated, courier.getLocation());
	}

	@Test
	void rejectsMissingIdLocationAndOrder() {
		assertThrows(NullPointerException.class, () -> new Courier(null, LOCATION));
		assertThrows(NullPointerException.class, () -> new Courier(UUID.randomUUID(), null));
		Courier courier = courier(CourierState.IDLE);

		assertThrows(NullPointerException.class, () -> courier.acceptOrder(null));

		assertEquals(CourierState.IDLE, courier.getState());
		assertTrue(courier.getAssignedOrder().isEmpty());
	}

	private Courier courier(CourierState state) {
		Courier courier = new Courier(UUID.randomUUID(), LOCATION);
		if (state != CourierState.OFFLINE) {
			courier.goOnDuty();
		}
		if (state == CourierState.ASSIGNED || state == CourierState.CARRYING) {
			courier.acceptOrder(readyOrder());
		}
		if (state == CourierState.CARRYING) {
			courier.confirmPickup();
		}
		return courier;
	}

	private Order readyOrder() {
		OrderLine line = new OrderLine(UUID.randomUUID(), 1, new BigDecimal("12.35"));
		Order order = new Order(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), List.of(line), Instant.now());
		order.accept();
		order.startPreparing();
		order.markReady();
		return order;
	}
}
