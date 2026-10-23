package com.midnightmunchies.domain.ord;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * An order for a single customer and restaurant. The application service will check
 * that each referenced menu item belongs to that restaurant before creating it.
 */
public final class Order {

	private final UUID id;
	private final UUID customerId;
	private final UUID restaurantId;
	private final List<OrderLine> orderLines;
	private OrderState state;
	private final Instant createdAt;
	private Instant acceptedAt;
	private String rejectionReason;
	private final List<OrderTransition> pendingTransitions = new ArrayList<>();

	public Order(UUID id, UUID customerId, UUID restaurantId, List<OrderLine> orderLines, Instant createdAt) {
		this(id, customerId, restaurantId, orderLines, createdAt, OrderStates.PLACED, null);
	}

	private Order(UUID id, UUID customerId, UUID restaurantId, List<OrderLine> orderLines, Instant createdAt,
			OrderState state, Instant acceptedAt) {
		this.id = Objects.requireNonNull(id, "id must not be null");
		this.customerId = Objects.requireNonNull(customerId, "customerId must not be null");
		this.restaurantId = Objects.requireNonNull(restaurantId, "restaurantId must not be null");
		Objects.requireNonNull(orderLines, "orderLines must not be null");
		if (orderLines.isEmpty()) {
			throw new IllegalArgumentException("orderLines must contain at least one line");
		}
		this.orderLines = List.copyOf(orderLines);
		this.createdAt = Objects.requireNonNull(createdAt, "createdAt must not be null");
		this.state = Objects.requireNonNull(state, "state must not be null");
		this.acceptedAt = acceptedAt;
	}

	public static Order restore(UUID id, UUID customerId, UUID restaurantId, List<OrderLine> orderLines,
			Instant createdAt, OrderStatus status, Instant acceptedAt) {
		Objects.requireNonNull(status, "status must not be null");
		if (status == OrderStatus.ACCEPTED && acceptedAt == null) {
			throw new IllegalArgumentException("ACCEPTED orders must have an acceptedAt time");
		}
		return new Order(id, customerId, restaurantId, orderLines, createdAt, OrderStates.of(status), acceptedAt);
	}

	public UUID getId() {
		return id;
	}

	public UUID getCustomerId() {
		return customerId;
	}

	public UUID getRestaurantId() {
		return restaurantId;
	}

	public List<OrderLine> getOrderLines() {
		return orderLines;
	}

	public OrderStatus getStatus() {
		return state.status();
	}

	public void accept() {
		transitionTo(state.accept());
	}

	/** Rejects the order and records why; the order is terminal afterwards. */
	public void reject(String reason) {
		if (reason == null || reason.isBlank()) {
			throw new IllegalArgumentException("A rejection reason is required");
		}
		transitionTo(state.reject());
		rejectionReason = reason.strip();
	}

	public void startPreparing() {
		transitionTo(state.startPreparing());
	}

	public void markReady() {
		transitionTo(state.markReady());
	}

	public void cancel() {
		transitionTo(state.cancel());
	}

	public List<OrderTransition> pullTransitions() {
		List<OrderTransition> pulled = List.copyOf(pendingTransitions);
		pendingTransitions.clear();
		return pulled;
	}

	public boolean hasPendingTransitions() {
		return !pendingTransitions.isEmpty();
	}

	private void transitionTo(OrderState next) {
		OrderStatus from = state.status();
		state = next;
		pendingTransitions.add(new OrderTransition(from, next.status()));
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public String getRejectionReason() {
		return rejectionReason;
	}

	public Instant getAcceptedAt() {
		return acceptedAt;
	}

	public void recordAcceptedAt(Instant acceptedAt) {
		Objects.requireNonNull(acceptedAt, "acceptedAt must not be null");
		if (getStatus() != OrderStatus.ACCEPTED) {
			throw new IllegalOrderTransitionException(getStatus(), "queue");
		}
		if (this.acceptedAt != null) {
			throw new IllegalStateException("Order is already in the preparation queue");
		}
		this.acceptedAt = acceptedAt;
	}
}
