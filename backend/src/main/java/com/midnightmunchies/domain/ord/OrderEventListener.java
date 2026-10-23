package com.midnightmunchies.domain.ord;

public interface OrderEventListener {

	void onEvent(OrderEvent event);
}
