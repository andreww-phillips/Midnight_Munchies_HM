package com.midnightmunchies.domain.ord.repository;

import com.midnightmunchies.domain.ord.Order;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OrderRepository {

	Optional<Order> findById(UUID id);

	/** Inserts or replaces the order with this ID and returns the saved entity. */
	Order save(Order order);

	/** Returns an unmodifiable snapshot with no guaranteed ordering. */
	List<Order> findAll();

	/** Returns an unmodifiable snapshot of this restaurant's orders, in no guaranteed order. */
	List<Order> findByRestaurantId(UUID restaurantId);

	/** Returns an unmodifiable snapshot of this customer's orders, in no guaranteed order. */
	List<Order> findByCustomerId(UUID customerId);

	List<Order> findPreparationQueue(UUID restaurantId);
}
