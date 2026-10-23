package com.midnightmunchies.domain.ord;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class OrderLineTests {

	@Test
	void preservesItemNameQuantityAndPriceSnapshot() {
		UUID itemId = UUID.randomUUID();
		UUID restaurantId = UUID.randomUUID();
		MenuItem original = new MenuItem(itemId, restaurantId, "Poutine", new BigDecimal("12.35"), MenuItemStatus.AVAILABLE);
		OrderLine line = new OrderLine(original.getId(), original.getName(), 2, original.getPrice());
		MenuItem repriced = new MenuItem(itemId, restaurantId, "Loaded Poutine", new BigDecimal("15.00"), MenuItemStatus.SOLD_OUT);

		assertEquals(itemId, line.getMenuItemId());
		assertEquals("Poutine", line.getMenuItemName());
		assertEquals(2, line.getQuantity());
		assertEquals(new BigDecimal("12.35"), line.getUnitPrice());
		assertNotEquals(repriced.getPrice(), line.getUnitPrice());
		assertNotEquals(repriced.getName(), line.getMenuItemName());
	}

	@Test
	void acceptsQuantityOneAndZeroPrice() {
		OrderLine line = new OrderLine(UUID.randomUUID(), "Poutine", 1, BigDecimal.ZERO);
		assertEquals(1, line.getQuantity());
		assertEquals(BigDecimal.ZERO, line.getUnitPrice());
	}

	@Test
	void legacyConstructorPreservesItemQuantityAndPriceWithoutInventingAName() {
		UUID itemId = UUID.randomUUID();
		OrderLine line = new OrderLine(itemId, 2, new BigDecimal("12.35"));

		assertEquals(itemId, line.getMenuItemId());
		assertNull(line.getMenuItemName());
		assertEquals(2, line.getQuantity());
		assertEquals(new BigDecimal("12.35"), line.getUnitPrice());
	}

	@ParameterizedTest
	@ValueSource(ints = {0, -1, Integer.MIN_VALUE})
	void rejectsNonPositiveQuantity(int quantity) {
		assertThrows(IllegalArgumentException.class,
				() -> new OrderLine(UUID.randomUUID(), quantity, BigDecimal.ONE));
		assertThrows(IllegalArgumentException.class,
				() -> new OrderLine(UUID.randomUUID(), "Poutine", quantity, BigDecimal.ONE));
	}

	@Test
	void rejectsNegativeUnitPrice() {
		assertThrows(IllegalArgumentException.class,
				() -> new OrderLine(UUID.randomUUID(), 1, new BigDecimal("-0.01")));
		assertThrows(IllegalArgumentException.class,
				() -> new OrderLine(UUID.randomUUID(), "Poutine", 1, new BigDecimal("-0.01")));
	}

	@ParameterizedTest
	@ValueSource(strings = {"", " ", "\t", "\n"})
	void rejectsBlankSnapshotName(String name) {
		assertThrows(IllegalArgumentException.class,
				() -> new OrderLine(UUID.randomUUID(), name, 1, BigDecimal.ONE));
	}

	@Test
	void rejectsMissingRequiredFields() {
		assertThrows(NullPointerException.class, () -> new OrderLine(null, 1, BigDecimal.ONE));
		assertThrows(NullPointerException.class, () -> new OrderLine(UUID.randomUUID(), 1, null));
		assertThrows(NullPointerException.class, () -> new OrderLine(null, "Poutine", 1, BigDecimal.ONE));
		assertThrows(NullPointerException.class, () -> new OrderLine(UUID.randomUUID(), null, 1, BigDecimal.ONE));
		assertThrows(NullPointerException.class, () -> new OrderLine(UUID.randomUUID(), "Poutine", 1, null));
	}
}
