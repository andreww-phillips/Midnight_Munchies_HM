package com.midnightmunchies.domain.ord;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;

/** Immutable item quantity and price snapshot, independent of later menu price changes. */
public final class OrderLine {

	private final UUID menuItemId;
	private final int quantity;
	private final BigDecimal unitPrice;

	public OrderLine(UUID menuItemId, int quantity, BigDecimal unitPrice) {
		this.menuItemId = Objects.requireNonNull(menuItemId, "menuItemId must not be null");
		if (quantity < 1) {
			throw new IllegalArgumentException("quantity must be at least 1");
		}
		this.quantity = quantity;
		this.unitPrice = OrdValidation.requirePrice(unitPrice);
	}

	public UUID getMenuItemId() {
		return menuItemId;
	}

	public int getQuantity() {
		return quantity;
	}

	public BigDecimal getUnitPrice() {
		return unitPrice;
	}
}
