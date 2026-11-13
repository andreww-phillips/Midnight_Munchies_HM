package com.midnightmunchies.domain.dlv;

import com.midnightmunchies.domain.ord.Order;
import com.midnightmunchies.domain.ord.OrderStatus;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** A courier starts offline and may handle one ready order at a time. */
public final class Courier {

	private final UUID id;
	private Location location;
	private CourierState state = CourierState.OFFLINE;
	private Order assignedOrder;

	public Courier(UUID id, Location location) {
		this.id = Objects.requireNonNull(id, "id must not be null");
		this.location = Objects.requireNonNull(location, "location must not be null");
	}

	public UUID getId() {
		return id;
	}

	public Location getLocation() {
		return location;
	}

	public void updateLocation(Location location) {
		this.location = Objects.requireNonNull(location, "location must not be null");
	}

	public CourierState getState() {
		return state;
	}

	public Optional<Order> getAssignedOrder() {
		return Optional.ofNullable(assignedOrder);
	}

	public void goOnDuty() {
		requireState(CourierState.OFFLINE, "go on duty");
		state = CourierState.IDLE;
	}

	public void goOffDuty() {
		requireState(CourierState.IDLE, "go off duty");
		state = CourierState.OFFLINE;
	}

	/** Accepts an offered order only after the restaurant has marked it ready. */
	public void acceptOrder(Order order) {
		requireState(CourierState.IDLE, "accept an order");
		Objects.requireNonNull(order, "order must not be null");
		requireReady(order, "accept an order");
		assignedOrder = order;
		state = CourierState.ASSIGNED;
	}

	public void confirmPickup() {
		requireState(CourierState.ASSIGNED, "confirm pickup");
		requireReady(assignedOrder, "confirm pickup");
		assignedOrder.confirmPickup();
		state = CourierState.CARRYING;
	}

	public void confirmDelivery() {
		requireState(CourierState.CARRYING, "confirm delivery");
		assignedOrder.confirmDelivery();
		assignedOrder = null;
		state = CourierState.IDLE;
	}

	private void requireState(CourierState expected, String action) {
		if (state != expected) {
			throw new IllegalCourierTransitionException(state, action);
		}
	}

	private void requireReady(Order order, String action) {
		OrderStatus status = order.getStatus();
		if (status != OrderStatus.READY) {
			throw new IllegalCourierTransitionException(state, action,
					": order " + order.getId() + " must be READY but is " + status);
		}
	}
}
