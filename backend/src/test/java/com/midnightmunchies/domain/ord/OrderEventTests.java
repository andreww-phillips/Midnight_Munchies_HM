package com.midnightmunchies.domain.ord;

import com.midnightmunchies.domain.reg.UserRole;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class OrderEventTests {

	private static final Instant TIME = Instant.parse("2026-10-23T20:00:00Z");

	@Test
	void ofCopiesTheActorIntoTheEvent() {
		Actor actor = new Actor(UUID.randomUUID(), UserRole.PLATFORM_OPERATOR);
		UUID eventId = UUID.randomUUID();
		UUID orderId = UUID.randomUUID();
		UUID restaurantId = UUID.randomUUID();

		OrderEvent event = OrderEvent.of(eventId, orderId, restaurantId, OrderStatus.ACCEPTED,
				OrderStatus.CANCELLED, TIME, actor);

		assertEquals(eventId, event.eventId());
		assertEquals(orderId, event.orderId());
		assertEquals(restaurantId, event.restaurantId());
		assertEquals(OrderStatus.ACCEPTED, event.fromStatus());
		assertEquals(OrderStatus.CANCELLED, event.toStatus());
		assertEquals(TIME, event.occurredAt());
		assertEquals(actor.userId(), event.actorId());
		assertEquals(UserRole.PLATFORM_OPERATOR, event.actorRole());
	}

	@Test
	void rejectsMissingFieldsAndNonTransitions() {
		UUID id = UUID.randomUUID();
		Actor actor = new Actor(id, UserRole.CUSTOMER);

		assertThrows(NullPointerException.class, () -> OrderEvent.of(null, id, id, OrderStatus.PLACED,
				OrderStatus.CANCELLED, TIME, actor));
		assertThrows(NullPointerException.class, () -> OrderEvent.of(id, id, id, OrderStatus.PLACED,
				OrderStatus.CANCELLED, null, actor));
		assertThrows(NullPointerException.class, () -> OrderEvent.of(id, id, id, OrderStatus.PLACED,
				OrderStatus.CANCELLED, TIME, null));
		assertThrows(IllegalArgumentException.class, () -> OrderEvent.of(id, id, id, OrderStatus.PLACED,
				OrderStatus.PLACED, TIME, actor));
	}

	@Test
	void actorRequiresIdAndRole() {
		assertThrows(NullPointerException.class, () -> new Actor(null, UserRole.CUSTOMER));
		assertThrows(NullPointerException.class, () -> new Actor(UUID.randomUUID(), null));
	}
}
