package com.midnightmunchies.domain.ord;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class MenuItemTests {

	@ParameterizedTest
	@EnumSource(MenuItemStatus.class)
	void preservesMenuItemDetailsAndStatus(MenuItemStatus status) {
		UUID id = UUID.randomUUID();
		UUID restaurantId = UUID.randomUUID();
		BigDecimal price = new BigDecimal("12.35");
		MenuItem item = new MenuItem(id, restaurantId, "Poutine", price, status);

		assertEquals(id, item.getId());
		assertEquals(restaurantId, item.getRestaurantId());
		assertEquals("Poutine", item.getName());
		assertEquals(price, item.getPrice());
		assertEquals(status, item.getStatus());
	}

	@Test
	void acceptsZeroPriceAndPreservesDecimalPrecision() {
		assertEquals(BigDecimal.ZERO, item(BigDecimal.ZERO).getPrice());
		BigDecimal precisePrice = new BigDecimal("0.12345678901234567890");
		assertEquals(precisePrice, item(precisePrice).getPrice());
	}

	@ParameterizedTest
	@ValueSource(strings = {"-0.01", "-10.00"})
	void rejectsNegativePrice(String price) {
		assertThrows(IllegalArgumentException.class, () -> item(new BigDecimal(price)));
	}

	@Test
	void rejectsMissingRequiredFieldsAndBlankName() {
		UUID id = UUID.randomUUID();
		UUID restaurantId = UUID.randomUUID();
		assertThrows(NullPointerException.class,
				() -> new MenuItem(null, restaurantId, "Poutine", BigDecimal.ONE, MenuItemStatus.AVAILABLE));
		assertThrows(NullPointerException.class,
				() -> new MenuItem(id, null, "Poutine", BigDecimal.ONE, MenuItemStatus.AVAILABLE));
		assertThrows(NullPointerException.class,
				() -> new MenuItem(id, restaurantId, null, BigDecimal.ONE, MenuItemStatus.AVAILABLE));
		assertThrows(IllegalArgumentException.class,
				() -> new MenuItem(id, restaurantId, " ", BigDecimal.ONE, MenuItemStatus.AVAILABLE));
		assertThrows(NullPointerException.class, () -> item(null));
		assertThrows(NullPointerException.class,
				() -> new MenuItem(id, restaurantId, "Poutine", BigDecimal.ONE, null));
	}

	private MenuItem item(BigDecimal price) {
		return new MenuItem(UUID.randomUUID(), UUID.randomUUID(), "Poutine", price, MenuItemStatus.AVAILABLE);
	}
}
