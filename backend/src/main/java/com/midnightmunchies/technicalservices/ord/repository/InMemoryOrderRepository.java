package com.midnightmunchies.technicalservices.ord.repository;

import com.midnightmunchies.domain.ord.Order;
import com.midnightmunchies.domain.ord.repository.OrderRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Repository
public class InMemoryOrderRepository implements OrderRepository {

	private final Map<UUID, Order> orders = new ConcurrentHashMap<>();

	@Override
	public Optional<Order> findById(UUID id) {
		return Optional.ofNullable(orders.get(Objects.requireNonNull(id, "id must not be null")));
	}

	@Override
	public Order save(Order order) {
		Objects.requireNonNull(order, "order must not be null");
		orders.put(order.getId(), order);
		return order;
	}

	@Override
	public List<Order> findAll() {
		return List.copyOf(orders.values());
	}

	@Override
	public List<Order> findByRestaurantId(UUID restaurantId) {
		Objects.requireNonNull(restaurantId, "restaurantId must not be null");
		return orders.values().stream()
				.filter(order -> order.getRestaurantId().equals(restaurantId))
				.toList();
	}

	@Override
	public List<Order> findByCustomerId(UUID customerId) {
		Objects.requireNonNull(customerId, "customerId must not be null");
		return orders.values().stream()
				.filter(order -> order.getCustomerId().equals(customerId))
				.toList();
	}
}
