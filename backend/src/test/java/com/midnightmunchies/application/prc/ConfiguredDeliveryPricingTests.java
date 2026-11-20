package com.midnightmunchies.application.prc;

import com.midnightmunchies.domain.ord.OrderLine;
import com.midnightmunchies.domain.prc.Charge;
import com.midnightmunchies.domain.prc.PricingRule;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ConfiguredDeliveryPricingTests {

    private final ConfiguredDeliveryPricing adapter = new ConfiguredDeliveryPricing(new PricingRule(
            new BigDecimal("3.99"), new BigDecimal("1.50"), new BigDecimal("15.00"), new BigDecimal("2.00")));

    @ParameterizedTest
    @CsvSource({
            "8.50, 1, 8.50, 2.00, 15.99",
            "7.50, 2, 15.00, 0.00, 20.49",
            "12.35, 2, 24.70, 0.00, 30.19",
            "14.995, 1, 15.00, 0.00, 20.49",
            "14.994, 1, 14.99, 2.00, 22.48"
    })
    void mapsEveryFareComponentIncludingThresholdAndRoundingCases(String price, int quantity,
            String subtotal, String surcharge, String total) {
        OrderLine line = line(price, quantity);

        Charge charge = adapter.calculate(List.of(line)).orElseThrow();

        assertEquals(new BigDecimal(subtotal), charge.itemSubtotal());
        assertEquals(new BigDecimal("3.99"), charge.deliveryFee());
        assertEquals(new BigDecimal("1.50"), charge.serviceFee());
        assertEquals(new BigDecimal(surcharge), charge.smallOrderSurcharge());
        assertEquals(new BigDecimal(total), charge.total());
        assertEquals(new BigDecimal(price), line.getUnitPrice());
    }

    @Test
    void usesTheInjectedRuleIncludingItsFeeRoundingRatherThanFixedAdapterValues() {
        ConfiguredDeliveryPricing customized = new ConfiguredDeliveryPricing(new PricingRule(
                new BigDecimal("1.125"), new BigDecimal("0.755"), new BigDecimal("10.00"), new BigDecimal("0.125")));

        Charge charge = customized.calculate(List.of(line("1.005", 1))).orElseThrow();

        assertEquals(new BigDecimal("1.01"), charge.itemSubtotal());
        assertEquals(new BigDecimal("1.13"), charge.deliveryFee());
        assertEquals(new BigDecimal("0.76"), charge.serviceFee());
        assertEquals(new BigDecimal("0.13"), charge.smallOrderSurcharge());
        assertEquals(new BigDecimal("3.03"), charge.total());
    }

    @Test
    void aConfiguredFreeOrderIsARecordedChargeRatherThanUnavailablePricing() {
        ConfiguredDeliveryPricing free = new ConfiguredDeliveryPricing(new PricingRule(
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO));

        Charge charge = free.calculate(List.of(line("0.00", 1))).orElseThrow();

        assertEquals(new BigDecimal("0.00"), charge.total());
    }

    @Test
    void invalidInputsAreRejectedInsteadOfSilentlyBecomingPendingPricing() {
        assertThrows(NullPointerException.class, () -> adapter.calculate(null));
        assertThrows(IllegalArgumentException.class, () -> adapter.calculate(List.of()));
        assertThrows(NullPointerException.class, () -> new ConfiguredDeliveryPricing(null));
    }

    private OrderLine line(String price, int quantity) {
        return new OrderLine(UUID.randomUUID(), "Original meal", quantity, new BigDecimal(price));
    }
}
