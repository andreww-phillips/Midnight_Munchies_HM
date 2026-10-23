package com.midnightmunchies.domain.ord;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;

/** Immutable item details captured at placement, independent of later menu changes or removal. */
public final class OrderLine {

	private final UUID menuItemId;
	private final String menuItemName;
	private final int quantity;
	private final BigDecimal unitPrice;

	/** Retained for callers without a name snapshot; getMenuItemName() returns null for these lines. */
	public OrderLine(UUID menuItemId, int quantity, BigDecimal unitPrice) {
		this.menuItemId = Objects.requireNonNull(menuItemId, "menuItemId must not be null");
		this.menuItemName = null;
		this.quantity = requireQuantity(quantity);
		this.unitPrice = OrdValidation.requirePrice(unitPrice);
	}

	public OrderLine(UUID menuItemId, String menuItemName, int quantity, BigDecimal unitPrice) {
		this.menuItemId = Objects.requireNonNull(menuItemId, "menuItemId must not be null");
		this.menuItemName = OrdValidation.requireName(menuItemName);
		this.quantity = requireQuantity(quantity);
		this.unitPrice = OrdValidation.requirePrice(unitPrice);
	}

	public UUID getMenuItemId() {
		return menuItemId;
	}

	/** Returns the name captured at placement, or null for lines made with the legacy constructor. */
	public String getMenuItemName() {
		return menuItemName;
	}

	public int getQuantity() {
		return quantity;
	}

	public BigDecimal getUnitPrice() {
		return unitPrice;
	}

	private static int requireQuantity(int quantity) {
		if (quantity < 1) {
			throw new IllegalArgumentException("quantity must be at least 1");
		}
		return quantity;
	}
}
