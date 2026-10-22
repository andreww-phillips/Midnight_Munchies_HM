package com.midnightmunchies.domain.ord;

import java.time.Instant;
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

	public Order(UUID id, UUID customerId, UUID restaurantId, List<OrderLine> orderLines, Instant createdAt) {
		this.id = Objects.requireNonNull(id, "id must not be null");
		this.customerId = Objects.requireNonNull(customerId, "customerId must not be null");
		this.restaurantId = Objects.requireNonNull(restaurantId, "restaurantId must not be null");
		Objects.requireNonNull(orderLines, "orderLines must not be null");
		if (orderLines.isEmpty()) {
			throw new IllegalArgumentException("orderLines must contain at least one line");
		}
		this.orderLines = List.copyOf(orderLines);
		this.createdAt = Objects.requireNonNull(createdAt, "createdAt must not be null");
		this.state = OrderStates.PLACED;
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
		state = state.accept();
	}

	public void reject() {
		state = state.reject();
	}

	public void startPreparing() {
		state = state.startPreparing();
	}

	public void markReady() {
		state = state.markReady();
	}

	public void cancel() {
		state = state.cancel();
	}

	public Instant getCreatedAt() {
		return createdAt;
	}
}
