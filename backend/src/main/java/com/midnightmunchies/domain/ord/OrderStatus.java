package com.midnightmunchies.domain.ord;

/**
 * Status vocabulary; transition rules are enforced by OrderState implementations.
 * PICKED_UP, DELIVERED, and REFUNDED are reserved for future lifecycle support.
 */
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
