package com.midnightmunchies.domain.ord;

import com.midnightmunchies.domain.reg.UserRole;

import java.util.Objects;
import java.util.UUID;

public record Actor(UUID userId, UserRole role) {

	public Actor {
		Objects.requireNonNull(userId, "userId must not be null");
		Objects.requireNonNull(role, "role must not be null");
	}
}
