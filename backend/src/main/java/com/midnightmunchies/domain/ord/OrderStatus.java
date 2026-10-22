package com.midnightmunchies.domain.ord;

/** Status vocabulary only; transition rules belong to the future order State implementation. */
public enum OrderStatus {
	PLACED,
	ACCEPTED,
	PREPARING,
	READY,
	PICKED_UP,
	DELIVERED,
	REJECTED,
	CANCELLED,
	REFUNDED
}
