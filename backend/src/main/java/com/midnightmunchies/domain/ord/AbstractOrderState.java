package com.midnightmunchies.domain.ord;

/** Default behaviour: every transition is illegal until a concrete state overrides it. */
abstract class AbstractOrderState implements OrderState {

	@Override
	public OrderState accept() {
		throw illegal("accept");
	}

	@Override
	public OrderState reject() {
		throw illegal("reject");
	}

	@Override
	public OrderState startPreparing() {
		throw illegal("start preparing");
	}

	@Override
	public OrderState markReady() {
		throw illegal("mark ready");
	}

	@Override
	public OrderState cancel() {
		throw illegal("cancel");
	}

	private IllegalOrderTransitionException illegal(String action) {
		return new IllegalOrderTransitionException(status(), action);
	}
}
