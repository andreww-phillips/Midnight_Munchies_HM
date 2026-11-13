package com.midnightmunchies.domain.prc;

import java.math.BigDecimal;
import java.util.Objects;

/** Monetary facts supplied by pricing and frozen when the order is billed. */
public record Charge(
        BigDecimal itemSubtotal,
        BigDecimal deliveryFee,
        BigDecimal serviceFee,
        BigDecimal smallOrderSurcharge,
        BigDecimal total
) {
    public Charge {
        requireAmount(itemSubtotal, "itemSubtotal");
        requireAmount(deliveryFee, "deliveryFee");
        requireAmount(serviceFee, "serviceFee");
        requireAmount(smallOrderSurcharge, "smallOrderSurcharge");
        requireAmount(total, "total");
        if (itemSubtotal.add(deliveryFee).add(serviceFee).add(smallOrderSurcharge).compareTo(total) != 0) {
            throw new IllegalArgumentException("Charge total must match its recorded components");
        }
    }

    private static void requireAmount(BigDecimal amount, String name) {
        Objects.requireNonNull(amount, name + " must not be null");
        if (amount.signum() < 0) {
            throw new IllegalArgumentException(name + " must not be negative");
        }
    }
}
