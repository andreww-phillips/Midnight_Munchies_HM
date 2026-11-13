package com.midnightmunchies.technicalservices;

import com.midnightmunchies.domain.prc.PricingRule;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;

import java.math.BigDecimal;

/** Reads the pricing values from the configuration file; none of them are written into the code. */
@Configuration
@PropertySource("classpath:pricing.properties")
public class PricingConfiguration {

    @Bean
    public PricingRule pricingRule(
            @Value("${pricing.delivery-fee}") BigDecimal deliveryFee,
            @Value("${pricing.service-fee}") BigDecimal serviceFee,
            @Value("${pricing.small-order-threshold}") BigDecimal smallOrderThreshold,
            @Value("${pricing.small-order-surcharge}") BigDecimal smallOrderSurcharge
    ) {
        return new PricingRule(deliveryFee, serviceFee, smallOrderThreshold, smallOrderSurcharge);
    }
}
