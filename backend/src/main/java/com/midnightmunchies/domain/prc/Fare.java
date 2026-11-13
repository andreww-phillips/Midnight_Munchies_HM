package com.midnightmunchies.domain.prc;

import java.math.BigDecimal;
import java.util.Objects;

/** The deterministic charge for one order, with every part that makes up the total. */
public record Fare(
		BigDecimal subtotal,
		BigDecimal deliveryFee,
		BigDecimal serviceFee,
		BigDecimal smallOrderSurcharge,
		BigDecimal total
) {

	public Fare {
		Objects.requireNonNull(subtotal, "subtotal must not be null");
		Objects.requireNonNull(deliveryFee, "deliveryFee must not be null");
		Objects.requireNonNull(serviceFee, "serviceFee must not be null");
		Objects.requireNonNull(smallOrderSurcharge, "smallOrderSurcharge must not be null");
		Objects.requireNonNull(total, "total must not be null");
	}
}
