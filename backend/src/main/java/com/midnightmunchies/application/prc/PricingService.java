package com.midnightmunchies.application.prc;

import com.midnightmunchies.domain.ord.Order;
import com.midnightmunchies.domain.prc.Fare;
import com.midnightmunchies.domain.prc.PricingRule;
import org.springframework.stereotype.Service;

import java.util.Objects;

/** Calculates an order's fare from its lines and the configured pricing rule (UC-PRC-01). */
@Service
public class PricingService {

    private final PricingRule pricingRule;

    public PricingService(PricingRule pricingRule) {
        this.pricingRule = Objects.requireNonNull(pricingRule, "pricingRule must not be null");
    }

    public Fare calculateFare(Order order) {
        Objects.requireNonNull(order, "order must not be null");
        return pricingRule.calculate(order.getOrderLines());
    }
}
