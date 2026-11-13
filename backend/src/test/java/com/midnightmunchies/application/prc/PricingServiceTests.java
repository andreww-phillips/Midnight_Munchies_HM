package com.midnightmunchies.application.prc;

import com.midnightmunchies.domain.ord.Order;
import com.midnightmunchies.domain.ord.OrderLine;
import com.midnightmunchies.domain.prc.Fare;
import com.midnightmunchies.domain.prc.PricingRule;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest
class PricingServiceTests {

    @Autowired
    private PricingService service;

    @Autowired
    private PricingRule configuredRule;

    @Test
    void fareUsesTheValuesFromTheConfigurationFile() {
        Order order = order(new OrderLine(UUID.randomUUID(), 2, new BigDecimal("12.99")));

        Fare fare = service.calculateFare(order);

        BigDecimal expected = new BigDecimal("25.98")
                .add(configuredRule.deliveryFee())
                .add(configuredRule.serviceFee());
        assertEquals(configuredRule.deliveryFee(), fare.deliveryFee());
        assertEquals(configuredRule.serviceFee(), fare.serviceFee());
        assertEquals(expected, fare.total());
    }

    @Test
    void smallOrderSurchargeComesFromTheConfiguredAmount() {
        BigDecimal justUnderThreshold = configuredRule.smallOrderThreshold().subtract(new BigDecimal("0.01"));
        Order order = order(new OrderLine(UUID.randomUUID(), 1, justUnderThreshold));

        Fare fare = service.calculateFare(order);

        assertEquals(configuredRule.smallOrderSurcharge(), fare.smallOrderSurcharge());
    }

    @Test
    void sameOrderGivesTheSameFareEveryTime() {
        Order order = order(new OrderLine(UUID.randomUUID(), 3, new BigDecimal("4.25")));

        assertEquals(service.calculateFare(order), service.calculateFare(order));
    }

    @Test
    void missingOrderIsRejected() {
        assertThrows(NullPointerException.class, () -> service.calculateFare(null));
    }

    private Order order(OrderLine... lines) {
        return new Order(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), List.of(lines), Instant.now());
    }
}
