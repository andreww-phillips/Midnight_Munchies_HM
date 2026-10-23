package com.midnightmunchies.application;

import com.midnightmunchies.domain.ord.MenuItem;
import com.midnightmunchies.domain.ord.MenuItemStatus;
import com.midnightmunchies.domain.ord.Order;
import com.midnightmunchies.domain.ord.OrderLine;
import com.midnightmunchies.domain.ord.Restaurant;
import com.midnightmunchies.domain.ord.RestaurantStatus;
import com.midnightmunchies.domain.ord.repository.MenuItemRepository;
import com.midnightmunchies.domain.ord.repository.OrderRepository;
import com.midnightmunchies.domain.ord.repository.RestaurantRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest
class BackendApplicationTests {

	@Autowired
	private RestaurantRepository restaurants;

	@Autowired
	private MenuItemRepository menuItems;

	@Autowired
	private OrderRepository orders;

	@Test
	void contextLoads() {
	}

	@Test
	void wiresOrdRepositoriesAndPreservesRelationships() {
		UUID customerId = UUID.randomUUID();
		Restaurant restaurant = new Restaurant(UUID.randomUUID(), "Diner", RestaurantStatus.OPEN, 45.5, -73.5);
		MenuItem item = new MenuItem(UUID.randomUUID(), restaurant.getId(), "Poutine",
				new BigDecimal("12.35"), MenuItemStatus.AVAILABLE);
		OrderLine line = new OrderLine(item.getId(), 2, item.getPrice());
		Order order = new Order(UUID.randomUUID(), customerId, restaurant.getId(), List.of(line), Instant.now());

		restaurants.save(restaurant);
		menuItems.save(item);
		orders.save(order);

		assertEquals(restaurant, restaurants.findById(restaurant.getId()).orElseThrow());
		assertEquals(List.of(item), menuItems.findByRestaurantId(restaurant.getId()));
		assertEquals(List.of(order), orders.findByRestaurantId(restaurant.getId()));
		assertEquals(List.of(order), orders.findByCustomerId(customerId));
		assertEquals(item.getId(), orders.findById(order.getId()).orElseThrow()
				.getOrderLines().getFirst().getMenuItemId());
	}

}
