package com.midnightmunchies.domain.ord;

/** Thrown when an order is asked to make a transition its current state does not allow. */
public class IllegalOrderTransitionException extends IllegalStateException {

	public IllegalOrderTransitionException(OrderStatus from, String action) {
		super("Cannot " + action + " an order that is " + from);
	}
}
