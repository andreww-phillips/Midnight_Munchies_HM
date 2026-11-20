package com.midnightmunchies.application.prc;

import com.midnightmunchies.domain.ord.OrderLine;
import com.midnightmunchies.domain.prc.Charge;
import com.midnightmunchies.domain.prc.Fare;
import com.midnightmunchies.domain.prc.PricingRule;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Connects delivery billing to the same configured rule used by PricingService. */
@Service
public class ConfiguredDeliveryPricing implements DeliveryPricing {

    private final PricingRule pricingRule;

    public ConfiguredDeliveryPricing(PricingRule pricingRule) {
        this.pricingRule = Objects.requireNonNull(pricingRule, "pricingRule must not be null");
    }

    @Override
    public Optional<Charge> calculate(List<OrderLine> orderLines) {
        Fare fare = pricingRule.calculate(orderLines);
        return Optional.of(new Charge(fare.subtotal(), fare.deliveryFee(), fare.serviceFee(),
                fare.smallOrderSurcharge(), fare.total()));
    }
}
