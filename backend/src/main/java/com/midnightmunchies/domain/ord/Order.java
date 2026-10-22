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
	private final OrderStatus status;
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
		this.status = OrderStatus.PLACED;
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

	/**
	 * Read-only status view. Issue #9 can delegate this to an internal OrderState
	 * and add validated domain operations without changing repository contracts.
	 */
	public OrderStatus getStatus() {
		return status;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}
}
