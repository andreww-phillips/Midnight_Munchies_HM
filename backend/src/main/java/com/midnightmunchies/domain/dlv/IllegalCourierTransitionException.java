package com.midnightmunchies.domain.dlv;

/** A courier command rejected because of the courier's state or the order's readiness. */
public class IllegalCourierTransitionException extends IllegalStateException {

	public IllegalCourierTransitionException(CourierState from, String action) {
		this(from, action, "");
	}

	public IllegalCourierTransitionException(CourierState from, String action, String detail) {
		super("Cannot " + action + " while courier is " + from + detail);
	}
}
