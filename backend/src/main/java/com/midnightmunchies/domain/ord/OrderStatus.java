package com.midnightmunchies.domain.ord;

/**
 * Status vocabulary; transition rules are enforced by OrderState implementations.
 * Only delivered orders can transition to REFUNDED.
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
