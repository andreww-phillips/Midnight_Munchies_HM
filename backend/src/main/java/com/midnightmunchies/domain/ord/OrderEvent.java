package com.midnightmunchies.domain.ord;

import com.midnightmunchies.domain.reg.UserRole;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record OrderEvent(
		UUID eventId,
		UUID orderId,
		UUID restaurantId,
		OrderStatus fromStatus,
		OrderStatus toStatus,
		Instant occurredAt,
		UUID actorId,
		UserRole actorRole
) {

	public OrderEvent {
		Objects.requireNonNull(eventId, "eventId must not be null");
		Objects.requireNonNull(orderId, "orderId must not be null");
		Objects.requireNonNull(restaurantId, "restaurantId must not be null");
		Objects.requireNonNull(fromStatus, "fromStatus must not be null");
		Objects.requireNonNull(toStatus, "toStatus must not be null");
		Objects.requireNonNull(occurredAt, "occurredAt must not be null");
		Objects.requireNonNull(actorId, "actorId must not be null");
		Objects.requireNonNull(actorRole, "actorRole must not be null");
		if (fromStatus == toStatus) {
			throw new IllegalArgumentException("fromStatus and toStatus must differ");
		}
	}

	public static OrderEvent of(UUID eventId, UUID orderId, UUID restaurantId, OrderStatus fromStatus,
			OrderStatus toStatus, Instant occurredAt, Actor actor) {
		Objects.requireNonNull(actor, "actor must not be null");
		return new OrderEvent(eventId, orderId, restaurantId, fromStatus, toStatus, occurredAt,
				actor.userId(), actor.role());
	}
}
