package com.midnightmunchies.domain.ord;

import java.math.BigDecimal;
import java.util.Objects;

final class OrdValidation {

	private OrdValidation() {
	}

	static String requireName(String name) {
		Objects.requireNonNull(name, "name must not be null");
		if (name.isBlank()) {
			throw new IllegalArgumentException("name must not be blank");
		}
		return name;
	}

	static BigDecimal requirePrice(BigDecimal price) {
		Objects.requireNonNull(price, "price must not be null");
		if (price.signum() < 0) {
			throw new IllegalArgumentException("price must not be negative");
		}
		return price;
	}
}
