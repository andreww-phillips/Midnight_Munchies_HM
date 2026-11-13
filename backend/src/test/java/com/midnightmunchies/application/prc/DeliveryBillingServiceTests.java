package com.midnightmunchies.application.prc;

import com.midnightmunchies.application.ord.OrderEventPublisher;
import com.midnightmunchies.domain.ord.Order;
import com.midnightmunchies.domain.ord.OrderEvent;
import com.midnightmunchies.domain.ord.OrderLine;
import com.midnightmunchies.domain.ord.OrderStatus;
import com.midnightmunchies.domain.prc.Charge;
import com.midnightmunchies.domain.prc.LedgerEntry;
import com.midnightmunchies.domain.reg.UserRole;
import com.midnightmunchies.technicalservices.InMemoryOrderRepository;
import com.midnightmunchies.technicalservices.LedgerRepository;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

class DeliveryBillingServiceTests {

    private final Instant createdAt = Instant.parse("2026-11-13T01:00:00Z");
    private final InMemoryOrderRepository orders = new InMemoryOrderRepository();
    private final LedgerRepository ledger = new LedgerRepository();
    private final OrderLedgerService projection = new OrderLedgerService(orders, ledger);
    private final DeliveryPricing pricing = mock(DeliveryPricing.class);
    private final DeliveryBillingService billing = new DeliveryBillingService(projection, ledger, Optional.of(pricing));
    private final Order order = orders.save(Order.restore(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
            List.of(new OrderLine(UUID.randomUUID(), "Original Poutine", 2, new BigDecimal("12.35"))),
            createdAt, OrderStatus.DELIVERED, null));
    private final OrderEvent delivered = new OrderEvent(UUID.randomUUID(), order.getId(), order.getRestaurantId(),
            OrderStatus.PICKED_UP, OrderStatus.DELIVERED, createdAt.plusSeconds(600), UUID.randomUUID(), UserRole.COURIER);
    private final Charge charge = new Charge(new BigDecimal("24.70"), new BigDecimal("3.00"),
            new BigDecimal("2.00"), new BigDecimal("1.00"), new BigDecimal("30.70"));

    @Test
    void deliveryRecordsOneBreakdownRegardlessOfListenerSubscriptionOrder() {
        when(pricing.calculate(anyList())).thenReturn(Optional.of(charge));
        OrderEventPublisher publisher = new OrderEventPublisher();
        publisher.subscribe(billing);
        publisher.subscribe(projection);

        publisher.publish(delivered);

        assertEquals(charge, current().charge());
        assertEquals(charge.total(), current().totalCharge());
        assertEquals(delivered.occurredAt(), current().timestamps().get(OrderStatus.DELIVERED));
        assertEquals(delivered.actorId(), current().courierId());
        verify(pricing).calculate(order.getOrderLines());
        assertEquals(charge, new BillingService(ledger).findSummary(order.getId()).orElseThrow().getCharge());
    }

    @Test
    void absentAdapterLeavesDeliveryAndSummaryExplicitlyUnpriced() {
        DeliveryBillingService unavailable = new DeliveryBillingService(projection, ledger, Optional.empty());

        unavailable.onEvent(delivered);

        assertEquals(OrderStatus.DELIVERED, current().status());
        assertNull(current().totalCharge());
        assertNull(current().charge());
        assertTrue(new BillingService(ledger).findSummary(order.getId()).orElseThrow().isPricingPending());
    }

    @Test
    void anUnavailableResultCanBeRetriedLaterWithoutCreatingAZeroCharge() {
        when(pricing.calculate(anyList())).thenReturn(Optional.empty()).thenReturn(Optional.of(charge));
        billing.onEvent(delivered);
        assertNull(current().totalCharge());

        assertEquals(Optional.of(charge), billing.recordDeliveryCharge(order.getId()));
        assertEquals(charge, current().charge());
        assertEquals(1, ledger.findAll().size());
    }

    @Test
    void duplicateDeliveryAndLaterPricingChangesCannotRepriceTheOriginalPurchase() {
        when(pricing.calculate(anyList())).thenReturn(Optional.of(charge));
        billing.onEvent(delivered);
        LedgerEntry original = current();
        when(pricing.calculate(anyList())).thenThrow(new IllegalStateException("New rules must not be consulted"));

        billing.onEvent(delivered);
        billing.recordDeliveryCharge(order.getId());
        new BillingService(ledger).findSummary(order.getId());

        assertSame(original, current());
        assertEquals(1, ledger.findAll().size());
        verify(pricing, times(1)).calculate(anyList());
    }

    @Test
    void concurrentDeliveryReplaysStillCalculateAndRecordOnlyOnce() throws Exception {
        when(pricing.calculate(anyList())).thenReturn(Optional.of(charge));
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> billing.onEvent(delivered));
            var second = executor.submit(() -> billing.onEvent(delivered));
            first.get();
            second.get();
        }

        assertEquals(charge, current().charge());
        assertEquals(1, ledger.findAll().size());
        verify(pricing, times(1)).calculate(anyList());
    }

    @Test
    void lateLifecycleFactsArePreservedWhenPricingFinishes() {
        OrderEvent accepted = new OrderEvent(UUID.randomUUID(), order.getId(), order.getRestaurantId(),
                OrderStatus.PLACED, OrderStatus.ACCEPTED, createdAt.plusSeconds(10),
                UUID.randomUUID(), UserRole.RESTAURANT_STAFF);
        when(pricing.calculate(anyList())).thenAnswer(invocation -> {
            projection.onEvent(accepted);
            return Optional.of(charge);
        });

        billing.onEvent(delivered);

        assertEquals(accepted.occurredAt(), current().timestamps().get(OrderStatus.ACCEPTED));
        assertEquals(OrderStatus.DELIVERED, current().status());
        assertEquals(charge, current().charge());
    }

    @Test
    void aTotalRecordedWithoutABreakdownIsNeverReconstructedUsingCurrentRules() {
        projection.onEvent(delivered);
        LedgerEntry unpriced = current();
        LedgerEntry historical = new LedgerEntry(unpriced.orderId(), unpriced.customerId(), unpriced.restaurantId(),
                unpriced.courierId(), unpriced.orderLines(), unpriced.status(), unpriced.createdAt(),
                unpriced.timestamps(), new BigDecimal("25.00"));
        ledger.save(historical);

        assertTrue(billing.recordDeliveryCharge(order.getId()).isEmpty());
        assertSame(historical, current());
        verifyNoInteractions(pricing);
    }

    @Test
    void pricingFailureDoesNotRecordAPartialChargeAndCanBeRetried() {
        when(pricing.calculate(anyList())).thenThrow(new IllegalStateException("Pricing unavailable"));

        assertThrows(IllegalStateException.class, () -> billing.onEvent(delivered));
        assertEquals(OrderStatus.DELIVERED, current().status());
        assertNull(current().totalCharge());
        assertNull(current().charge());
        doReturn(Optional.of(charge)).when(pricing).calculate(anyList());
        assertEquals(Optional.of(charge), billing.recordDeliveryCharge(order.getId()));
    }

    @Test
    void pricingOwnsRoundingWhileOriginalFractionalUnitPricesRemainUnchanged() {
        Order fractional = orders.save(Order.restore(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                List.of(new OrderLine(UUID.randomUUID(), "Fractional item", 1, new BigDecimal("0.333"))),
                createdAt, OrderStatus.DELIVERED, null));
        Charge rounded = new Charge(new BigDecimal("0.33"), BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO, new BigDecimal("0.33"));
        when(pricing.calculate(anyList())).thenReturn(Optional.of(rounded));
        OrderEvent event = new OrderEvent(UUID.randomUUID(), fractional.getId(), fractional.getRestaurantId(),
                OrderStatus.PICKED_UP, OrderStatus.DELIVERED, createdAt.plusSeconds(600),
                delivered.actorId(), UserRole.COURIER);

        billing.onEvent(event);

        LedgerEntry recorded = ledger.findByOrderId(fractional.getId()).orElseThrow();
        assertEquals(new BigDecimal("0.333"), recorded.orderLines().getFirst().getUnitPrice());
        assertEquals(rounded, recorded.charge());
        verify(pricing).calculate(fractional.getOrderLines());
    }

    @Test
    void aRecordedFreeOrderIsNotMistakenForUnavailablePricingOnReplay() {
        Order free = orders.save(Order.restore(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                List.of(new OrderLine(UUID.randomUUID(), "Free item", 1, BigDecimal.ZERO)),
                createdAt, OrderStatus.DELIVERED, null));
        Charge zero = new Charge(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);
        when(pricing.calculate(anyList())).thenReturn(Optional.of(zero));
        OrderEvent event = new OrderEvent(UUID.randomUUID(), free.getId(), free.getRestaurantId(),
                OrderStatus.PICKED_UP, OrderStatus.DELIVERED, createdAt.plusSeconds(600),
                delivered.actorId(), UserRole.COURIER);

        billing.onEvent(event);
        billing.onEvent(event);

        assertEquals(zero, ledger.findByOrderId(free.getId()).orElseThrow().charge());
        assertFalse(new BillingService(ledger).findSummary(free.getId()).orElseThrow().isPricingPending());
        verify(pricing, times(1)).calculate(free.getOrderLines());
    }

    @Test
    void nonDeliveryEventsDoNotRequestPricingAndUndeliveredOrdersCannotBeCharged() {
        projection.recordCreated(order);
        OrderEvent ready = new OrderEvent(UUID.randomUUID(), order.getId(), order.getRestaurantId(),
                OrderStatus.PREPARING, OrderStatus.READY, createdAt.plusSeconds(30),
                UUID.randomUUID(), UserRole.RESTAURANT_STAFF);
        billing.onEvent(ready);

        assertTrue(billing.recordDeliveryCharge(order.getId()).isEmpty());
        assertThrows(IllegalArgumentException.class, () -> projection.recordCharge(order.getId(), charge));
        assertEquals(OrderStatus.PLACED, current().status());
        verifyNoInteractions(pricing);
    }

    @Test
    void originalBreakdownCannotBeRemovedOrChangedEvenWhenTheTotalIsTheSame() {
        when(pricing.calculate(anyList())).thenReturn(Optional.of(charge));
        billing.onEvent(delivered);
        LedgerEntry original = current();
        LedgerEntry removed = new LedgerEntry(original.orderId(), original.customerId(), original.restaurantId(),
                original.courierId(), original.orderLines(), original.status(), original.createdAt(),
                original.timestamps(), original.totalCharge());
        Charge changedFees = new Charge(new BigDecimal("24.70"), new BigDecimal("4.00"),
                new BigDecimal("1.00"), new BigDecimal("1.00"), new BigDecimal("30.70"));

        assertThrows(IllegalArgumentException.class, () -> ledger.save(removed));
        assertThrows(IllegalArgumentException.class, () -> projection.recordCharge(order.getId(), changedFees));
        assertSame(original, current());
    }

    private LedgerEntry current() {
        return ledger.findByOrderId(order.getId()).orElseThrow();
    }
}
