package com.midnightmunchies.domain.ord.repository;

import com.midnightmunchies.domain.ord.OrderEvent;

import java.util.List;
import java.util.UUID;

public interface OrderEventLog {

	void append(OrderEvent event);

	List<OrderEvent> findAll();

	List<OrderEvent> findByOrderId(UUID orderId);
}
