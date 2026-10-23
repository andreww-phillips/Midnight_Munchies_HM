package com.midnightmunchies.domain.ord;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class OrderLineTests {

	@Test
	void preservesItemQuantityAndPriceSnapshot() {
		UUID itemId = UUID.randomUUID();
		UUID restaurantId = UUID.randomUUID();
		MenuItem original = new MenuItem(itemId, restaurantId, "Poutine", new BigDecimal("12.35"), MenuItemStatus.AVAILABLE);
		OrderLine line = new OrderLine(original.getId(), 2, original.getPrice());
		MenuItem repriced = new MenuItem(itemId, restaurantId, "Poutine", new BigDecimal("15.00"), MenuItemStatus.SOLD_OUT);

		assertEquals(itemId, line.getMenuItemId());
		assertEquals(2, line.getQuantity());
		assertEquals(new BigDecimal("12.35"), line.getUnitPrice());
		assertNotEquals(repriced.getPrice(), line.getUnitPrice());
	}

	@Test
	void acceptsQuantityOneAndZeroPrice() {
		OrderLine line = new OrderLine(UUID.randomUUID(), 1, BigDecimal.ZERO);
		assertEquals(1, line.getQuantity());
		assertEquals(BigDecimal.ZERO, line.getUnitPrice());
	}

	@ParameterizedTest
	@ValueSource(ints = {0, -1, Integer.MIN_VALUE})
	void rejectsNonPositiveQuantity(int quantity) {
		assertThrows(IllegalArgumentException.class,
				() -> new OrderLine(UUID.randomUUID(), quantity, BigDecimal.ONE));
	}

	@Test
	void rejectsNegativeUnitPrice() {
		assertThrows(IllegalArgumentException.class,
				() -> new OrderLine(UUID.randomUUID(), 1, new BigDecimal("-0.01")));
	}

	@Test
	void rejectsMissingRequiredFields() {
		assertThrows(NullPointerException.class, () -> new OrderLine(null, 1, BigDecimal.ONE));
		assertThrows(NullPointerException.class, () -> new OrderLine(UUID.randomUUID(), 1, null));
	}
}
