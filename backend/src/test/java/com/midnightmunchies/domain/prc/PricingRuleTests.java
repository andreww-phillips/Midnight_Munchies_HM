package com.midnightmunchies.domain.prc;

import com.midnightmunchies.domain.ord.OrderLine;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PricingRuleTests {

	private final PricingRule rule = new PricingRule(
			new BigDecimal("3.99"), new BigDecimal("1.50"), new BigDecimal("15.00"), new BigDecimal("2.00"));

	@Test
	void totalIsSubtotalPlusDeliveryAndServiceFee() {
		// 2 x 12.99 + 1 x 5.49 = 31.47; + 3.99 + 1.50 = 36.96; above the threshold so no surcharge
		Fare fare = rule.calculate(List.of(line("12.99", 2), line("5.49", 1)));

		assertEquals(new BigDecimal("31.47"), fare.subtotal());
		assertEquals(new BigDecimal("3.99"), fare.deliveryFee());
		assertEquals(new BigDecimal("1.50"), fare.serviceFee());
		assertEquals(new BigDecimal("0.00"), fare.smallOrderSurcharge());
		assertEquals(new BigDecimal("36.96"), fare.total());
	}

	@Test
	void smallOrderBelowTheThresholdPaysTheSurcharge() {
		// 5.49 + 3.99 + 1.50 + 2.00 = 12.98
		Fare fare = rule.calculate(List.of(line("5.49", 1)));

		assertEquals(new BigDecimal("2.00"), fare.smallOrderSurcharge());
		assertEquals(new BigDecimal("12.98"), fare.total());
	}

	@Test
	void subtotalExactlyAtTheThresholdPaysNoSurcharge() {
		Fare fare = rule.calculate(List.of(line("7.50", 2)));

		assertEquals(new BigDecimal("15.00"), fare.subtotal());
		assertEquals(new BigDecimal("0.00"), fare.smallOrderSurcharge());
		assertEquals(new BigDecimal("20.49"), fare.total());
	}

	@Test
	void sameLinesAndValuesAlwaysGiveTheSameFare() {
		List<OrderLine> lines = List.of(line("12.99", 2), line("5.49", 1));

		assertEquals(rule.calculate(lines), rule.calculate(lines));
		assertEquals(rule.calculate(lines), new PricingRule(
				new BigDecimal("3.99"), new BigDecimal("1.50"), new BigDecimal("15.00"), new BigDecimal("2.00"))
				.calculate(lines));
	}

	@Test
	void lineOrderDoesNotChangeTheFare() {
		OrderLine burger = line("12.99", 2);
		OrderLine fries = line("5.49", 1);

		assertEquals(rule.calculate(List.of(burger, fries)), rule.calculate(List.of(fries, burger)));
	}

	@Test
	void zeroFeesAndZeroSurchargeAreAllowed() {
		PricingRule free = new PricingRule(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);

		assertEquals(new BigDecimal("5.49"), free.calculate(List.of(line("5.49", 1))).total());
	}

	@Test
	void ruleRejectsMissingOrNegativeValues() {
		BigDecimal ok = BigDecimal.ONE;
		assertThrows(NullPointerException.class, () -> new PricingRule(null, ok, ok, ok));
		assertThrows(NullPointerException.class, () -> new PricingRule(ok, null, ok, ok));
		assertThrows(NullPointerException.class, () -> new PricingRule(ok, ok, null, ok));
		assertThrows(NullPointerException.class, () -> new PricingRule(ok, ok, ok, null));
		assertThrows(IllegalArgumentException.class, () -> new PricingRule(new BigDecimal("-0.01"), ok, ok, ok));
		assertThrows(IllegalArgumentException.class, () -> new PricingRule(ok, ok, ok, new BigDecimal("-1")));
	}

	@Test
	void calculateRejectsNullOrEmptyLines() {
		assertThrows(NullPointerException.class, () -> rule.calculate(null));
		assertThrows(IllegalArgumentException.class, () -> rule.calculate(List.of()));
	}

	private OrderLine line(String price, int quantity) {
		return new OrderLine(UUID.randomUUID(), quantity, new BigDecimal(price));
	}
}
