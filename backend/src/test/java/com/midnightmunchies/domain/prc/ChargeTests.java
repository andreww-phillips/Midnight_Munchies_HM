package com.midnightmunchies.domain.prc;

import com.midnightmunchies.domain.ord.OrderLine;
import com.midnightmunchies.domain.ord.OrderStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ChargeTests {

    @Test
    void retainsSuppliedComponentsWithoutSelectingFeesOrRounding() {
        Charge charge = charge("24.700", "3.005", "2.00", "1.00", "30.705");

        assertEquals(new BigDecimal("24.700"), charge.itemSubtotal());
        assertEquals(new BigDecimal("3.005"), charge.deliveryFee());
        assertEquals(new BigDecimal("30.705"), charge.total());
        assertEquals(BigDecimal.ZERO, charge("0", "0", "0", "0", "0").total());
    }

    @Test
    void rejectsAnInconsistentTotal() {
        assertThrows(IllegalArgumentException.class, () -> charge("24.70", "3", "2", "1", "31.70"));
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 2, 3, 4})
    void rejectsNegativeAndMissingAmounts(int component) {
        BigDecimal[] amounts = {BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO};
        amounts[component] = new BigDecimal("-0.01");
        assertThrows(IllegalArgumentException.class,
                () -> new Charge(amounts[0], amounts[1], amounts[2], amounts[3], amounts[4]));
        amounts[component] = null;
        assertThrows(NullPointerException.class,
                () -> new Charge(amounts[0], amounts[1], amounts[2], amounts[3], amounts[4]));
    }

    @Test
    void ledgerAcceptsOnlyABreakdownMatchingItsRecordedTotal() {
        Charge valid = charge("24.70", "3", "2", "1", "30.70");
        assertEquals(valid, entry(new BigDecimal("30.70"), valid).charge());
        assertThrows(IllegalArgumentException.class, () -> entry(null, valid));
        assertThrows(IllegalArgumentException.class, () -> entry(new BigDecimal("31.70"), valid));
    }

    private LedgerEntry entry(BigDecimal total, Charge charge) {
        Instant created = Instant.parse("2026-11-13T01:00:00Z");
        return new LedgerEntry(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                List.of(new OrderLine(UUID.randomUUID(), "Poutine", 2, new BigDecimal("12.35"))),
                OrderStatus.DELIVERED, created,
                Map.of(OrderStatus.PLACED, created, OrderStatus.DELIVERED, created.plusSeconds(600)), total, charge);
    }

    private Charge charge(String subtotal, String delivery, String service, String surcharge, String total) {
        return new Charge(new BigDecimal(subtotal), new BigDecimal(delivery), new BigDecimal(service),
                new BigDecimal(surcharge), new BigDecimal(total));
    }
}
