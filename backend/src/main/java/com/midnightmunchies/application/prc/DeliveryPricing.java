package com.midnightmunchies.application.prc;

import com.midnightmunchies.domain.ord.OrderLine;
import com.midnightmunchies.domain.prc.Charge;

import java.util.List;
import java.util.Optional;

/**
 * Adapter boundary for issue #50: calculate from immutable purchase facts and configured rules.
 * ConfiguredDeliveryPricing supplies the production bean using issue #50's configured rule.
 * Pricing owns subtotal calculation and all rounding; billing stores the supplied amounts.
 * An empty result means pricing is unavailable; it must never stand in for a zero charge.
 * Implementations calculate amounts only; they must not charge customers or change orders.
 */
@FunctionalInterface
public interface DeliveryPricing {
    Optional<Charge> calculate(List<OrderLine> orderLines);
}
