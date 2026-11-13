package com.midnightmunchies.domain.prc;

import com.midnightmunchies.domain.reg.UserRole;

import java.util.Objects;
import java.util.UUID;

/** Who is opening a history: their ID, role and, for restaurant staff, the restaurant they work for. */
public record HistorySession(UUID userId, UserRole role, UUID restaurantId) {

	public HistorySession {
		Objects.requireNonNull(userId, "userId must not be null");
		Objects.requireNonNull(role, "role must not be null");
	}
}
