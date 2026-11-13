package com.midnightmunchies.domain.prc;

import com.midnightmunchies.domain.ord.OrderLine;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Objects;

/**
 * The configured pricing values (R-PRC-01) and the rule that turns order lines into a fare.
 * The same lines and values always produce the same fare.
 */
public record PricingRule(
		BigDecimal deliveryFee,
		BigDecimal serviceFee,
		BigDecimal smallOrderThreshold,
		BigDecimal smallOrderSurcharge
) {

	private static final int CENTS = 2;

	public PricingRule {
		deliveryFee = requireAmount(deliveryFee, "deliveryFee");
		serviceFee = requireAmount(serviceFee, "serviceFee");
		smallOrderThreshold = requireAmount(smallOrderThreshold, "smallOrderThreshold");
		smallOrderSurcharge = requireAmount(smallOrderSurcharge, "smallOrderSurcharge");
	}

	/** The surcharge applies when the item subtotal is below the threshold. */
	public Fare calculate(List<OrderLine> orderLines) {
		Objects.requireNonNull(orderLines, "orderLines must not be null");
		if (orderLines.isEmpty()) {
			throw new IllegalArgumentException("orderLines must contain at least one line");
		}
		BigDecimal subtotal = orderLines.stream()
				.map(line -> line.getUnitPrice().multiply(BigDecimal.valueOf(line.getQuantity())))
				.reduce(BigDecimal.ZERO, BigDecimal::add)
				.setScale(CENTS, RoundingMode.HALF_UP);
		BigDecimal surcharge = subtotal.compareTo(smallOrderThreshold) < 0 ? smallOrderSurcharge : BigDecimal.ZERO.setScale(CENTS);
		BigDecimal total = subtotal.add(deliveryFee).add(serviceFee).add(surcharge);
		return new Fare(subtotal, deliveryFee, serviceFee, surcharge, total);
	}

	private static BigDecimal requireAmount(BigDecimal amount, String name) {
		Objects.requireNonNull(amount, name + " must not be null");
		if (amount.signum() < 0) {
			throw new IllegalArgumentException(name + " must not be negative");
		}
		return amount.setScale(CENTS, RoundingMode.HALF_UP);
	}
}
