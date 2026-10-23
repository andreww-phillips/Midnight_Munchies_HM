package com.midnightmunchies.technicalservices.ord.repository;

import com.midnightmunchies.domain.ord.OrderEvent;
import com.midnightmunchies.domain.ord.repository.OrderEventLog;
import org.springframework.stereotype.Repository;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@Repository
public class InMemoryOrderEventLog implements OrderEventLog {

	private final Map<UUID, OrderEvent> events = new LinkedHashMap<>();

	@Override
	public synchronized void append(OrderEvent event) {
		Objects.requireNonNull(event, "event must not be null");
		if (events.containsKey(event.eventId())) {
			throw new IllegalArgumentException("An event with id " + event.eventId() + " is already recorded");
		}
		events.put(event.eventId(), event);
	}

	@Override
	public synchronized List<OrderEvent> findAll() {
		return List.copyOf(events.values());
	}

	@Override
	public synchronized List<OrderEvent> findByOrderId(UUID orderId) {
		Objects.requireNonNull(orderId, "orderId must not be null");
		return events.values().stream()
				.filter(event -> event.orderId().equals(orderId))
				.toList();
	}
}
