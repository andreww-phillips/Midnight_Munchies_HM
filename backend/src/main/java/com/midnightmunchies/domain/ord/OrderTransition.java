package com.midnightmunchies.domain.ord;

import java.util.Objects;

public record OrderTransition(OrderStatus from, OrderStatus to) {

	public OrderTransition {
		Objects.requireNonNull(from, "from must not be null");
		Objects.requireNonNull(to, "to must not be null");
	}
}
