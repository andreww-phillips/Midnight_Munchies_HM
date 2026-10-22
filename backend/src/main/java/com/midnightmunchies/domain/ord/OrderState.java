package com.midnightmunchies.domain.ord;

/**
 * State pattern for the order lifecycle. Each state returns the next state for the
 * transitions it allows and rejects every other one.
 */
interface OrderState {

	OrderStatus status();

	OrderState accept();

	OrderState reject();

	OrderState startPreparing();

	OrderState markReady();

	OrderState cancel();
}
