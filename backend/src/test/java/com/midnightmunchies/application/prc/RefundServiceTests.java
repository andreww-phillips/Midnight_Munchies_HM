package com.midnightmunchies.application.prc;

import com.midnightmunchies.application.ord.AuthorizationException;
import com.midnightmunchies.application.ord.EventIdRecorder;
import com.midnightmunchies.application.ord.OrderEventPublisher;
import com.midnightmunchies.application.ord.OrderNotFoundException;
import com.midnightmunchies.application.ord.OrderTransitions;
import com.midnightmunchies.application.reg.AuthenticationRequiredException;
import com.midnightmunchies.domain.ord.Order;
import com.midnightmunchies.domain.ord.OrderEvent;
import com.midnightmunchies.domain.ord.OrderLine;
import com.midnightmunchies.domain.ord.OrderStatus;
import com.midnightmunchies.domain.prc.Charge;
import com.midnightmunchies.domain.prc.LedgerEntry;
import com.midnightmunchies.domain.reg.UserAccount;
import com.midnightmunchies.domain.reg.UserRole;
import com.midnightmunchies.technicalservices.InMemoryOrderEventLog;
import com.midnightmunchies.technicalservices.InMemoryOrderRepository;
import com.midnightmunchies.technicalservices.LedgerRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class RefundServiceTests {

    private static final Instant PLACED = Instant.parse("2026-11-15T01:00:00Z");
    private static final Instant DELIVERED = PLACED.plusSeconds(60);
    private static final Instant NOW = DELIVERED.plusSeconds(60);
    private final InMemoryOrderRepository orders = spy(new InMemoryOrderRepository());
    private final LedgerRepository ledger = spy(new LedgerRepository());
    private final InMemoryOrderEventLog events = spy(new InMemoryOrderEventLog());
    private final OrderLedgerService projection = new OrderLedgerService(orders, ledger);
    private final OrderEventPublisher publisher = new OrderEventPublisher();
    private final EventIdRecorder recorder = new EventIdRecorder(events);
    private final OrderTransitions transitions = new OrderTransitions(orders, publisher);
    private final UserAccount operator = account(UserRole.PLATFORM_OPERATOR);
    private final List<OrderEvent> notifications = new ArrayList<>();
    private final Charge charge = new Charge(new BigDecimal("24.70"), new BigDecimal("3.99"),
            new BigDecimal("1.50"), BigDecimal.ZERO, new BigDecimal("30.19"));
    private final Order original = saveOrder(OrderStatus.DELIVERED, charge.total(), charge);
    private final LedgerEntry originalLedger = ledger.findByOrderId(original.getId()).orElseThrow();
    private final RefundService service = serviceAt(NOW);

    {
        publisher.subscribe(recorder);
        publisher.subscribe(projection);
        publisher.subscribe(notifications::add);
    }

    @Test
    void commitsAFullRefundAndOneAttributedEventBeforeNotifyingObservers() {
        publisher.subscribe(event -> {
            assertEquals(OrderStatus.REFUNDED, orders.findById(event.orderId()).orElseThrow().getStatus());
            assertEquals(OrderStatus.REFUNDED, ledger.findByOrderId(event.orderId()).orElseThrow().status());
            assertTrue(ledger.findRefundByOrderId(event.orderId()).isPresent());
            assertEquals(List.of(event), events.findByOrderId(event.orderId()));
        });
        RefundService.Result result = service.refund(original.getId().toString(), operator);

        assertEquals(charge.total(), result.refund().amount());
        assertEquals(UUID.fromString(operator.id()), result.refund().operatorId());
        assertEquals(NOW, result.refund().refundedAt());
        assertEquals(OrderStatus.REFUNDED, result.status());
        assertEquals(original.getId(), result.orderId());
        LedgerEntry updated = ledger.findByOrderId(original.getId()).orElseThrow();
        assertSame(charge, updated.charge());
        assertEquals(originalLedger.orderLines(), updated.orderLines());
        assertEquals(NOW, updated.timestamps().get(OrderStatus.REFUNDED));
        assertEquals(DELIVERED, updated.timestamps().get(OrderStatus.DELIVERED));
        assertEquals(original.getAcceptedAt(), orders.findById(original.getId()).orElseThrow().getAcceptedAt());
        assertEquals(OrderStatus.DELIVERED, original.getStatus());
        assertFalse(original.hasPendingTransitions());
        OrderEvent event = events.findByOrderId(original.getId()).getFirst();
        assertEquals(result.eventId(), event.eventId());
        assertEquals(OrderStatus.DELIVERED, event.fromStatus());
        assertEquals(OrderStatus.REFUNDED, event.toStatus());
        assertEquals(UserRole.PLATFORM_OPERATOR, event.actorRole());
        assertEquals(result.refund().operatorId(), event.actorId());
        assertEquals(NOW, event.occurredAt());
        assertEquals(List.of(event), notifications);
        verify(events, times(1)).append(event);
    }

    @Test
    void rejectsAnonymousAndNonOperatorsBeforeParsingOrLookingUpAnOrder() {
        clearInvocations(orders, ledger);
        assertThrows(AuthenticationRequiredException.class, () -> service.refund("invalid", null));
        for (UserRole role : List.of(UserRole.CUSTOMER, UserRole.RESTAURANT_STAFF, UserRole.COURIER)) {
            assertThrows(AuthorizationException.class, () -> service.refund("invalid", account(role)));
        }
        verifyNoInteractions(orders, ledger);
    }

    @Test
    void rejectsMalformedAndMissingOrderIds() {
        assertThrows(IllegalArgumentException.class, () -> service.refund("invalid", operator));
        assertThrows(OrderNotFoundException.class, () -> service.refund(UUID.randomUUID().toString(), operator));
        assertTrue(ledger.findAllRefunds().isEmpty());
    }

    @ParameterizedTest
    @EnumSource(value = OrderStatus.class, names = "DELIVERED", mode = EnumSource.Mode.EXCLUDE)
    void rejectsEveryOtherAuthoritativeState(OrderStatus status) {
        Order order = saveOrder(status, null, null);

        assertThrows(RefundConflictException.class, () -> service.refund(order.getId().toString(), operator));
        assertEquals(status, orders.findById(order.getId()).orElseThrow().getStatus());
        assertTrue(ledger.findAllRefunds().isEmpty());
        assertTrue(events.findAll().isEmpty());
    }

    @Test
    void rejectsMissingChargeAndDoesNotPriceTheOrder() {
        Order unpriced = saveOrder(OrderStatus.DELIVERED, null, null);
        assertThrows(RefundConflictException.class, () -> service.refund(unpriced.getId().toString(), operator));
        assertNull(ledger.findByOrderId(unpriced.getId()).orElseThrow().totalCharge());
        assertTrue(ledger.findAllRefunds().isEmpty());
        assertTrue(events.findAll().isEmpty());
    }

    @Test
    void rejectsAnIncompleteOrMismatchedLedgerProjection() {
        ledger.save(new LedgerEntry(originalLedger.orderId(), originalLedger.customerId(),
                originalLedger.restaurantId(), originalLedger.courierId(), originalLedger.orderLines(),
                OrderStatus.PLACED, PLACED, Map.of(OrderStatus.PLACED, PLACED), charge.total(), charge));
        assertThrows(RefundConflictException.class, () -> service.refund(original.getId().toString(), operator));
        assertTrue(ledger.findAllRefunds().isEmpty());
        assertSame(original, orders.findById(original.getId()).orElseThrow());
    }

    @Test
    void refusesToInventAnEarlierRefundTime() {
        assertThrows(RefundConflictException.class,
                () -> serviceAt(DELIVERED.minusSeconds(1)).refund(original.getId().toString(), operator));
        assertTrue(ledger.findAllRefunds().isEmpty());
        assertSame(original, orders.findById(original.getId()).orElseThrow());
    }

    @Test
    void repeatedRefundsLeaveTheFirstTransactionAndEventUnchanged() {
        RefundService.Result first = service.refund(original.getId().toString(), operator);
        LedgerEntry refunded = ledger.findByOrderId(original.getId()).orElseThrow();

        assertThrows(RefundConflictException.class, () -> service.refund(original.getId().toString(), operator));
        assertEquals(List.of(first.refund()), ledger.findAllRefunds());
        assertSame(refunded, ledger.findByOrderId(original.getId()).orElseThrow());
        assertEquals(1, events.findAll().size());
    }

    @ParameterizedTest
    @ValueSource(strings = {"order", "refund", "projection", "event"})
    void failedCommitRestoresOriginalFactsAndCanBeRetried(String failurePoint) {
        switch (failurePoint) {
            case "order" -> doAnswer(invocation -> {
                Order result = (Order) invocation.callRealMethod();
                if (result.getStatus() == OrderStatus.REFUNDED) throw new IllegalStateException("order write failed");
                return result;
            }).when(orders).save(any());
            case "refund" -> doAnswer(invocation -> {
                invocation.callRealMethod();
                throw new IllegalArgumentException("refund write failed");
            }).when(ledger).appendRefund(any());
            case "projection" -> doAnswer(invocation -> {
                LedgerEntry result = (LedgerEntry) invocation.callRealMethod();
                if (result.status() == OrderStatus.REFUNDED) throw new IllegalStateException("projection write failed");
                return result;
            }).when(ledger).save(any());
            case "event" -> doThrow(new IllegalStateException("event write failed")).when(events).append(any());
            default -> throw new AssertionError("Unknown failure point");
        }

        assertThrows(IllegalStateException.class, () -> service.refund(original.getId().toString(), operator));
        assertSame(original, orders.findById(original.getId()).orElseThrow());
        assertEquals(OrderStatus.DELIVERED, original.getStatus());
        assertFalse(original.hasPendingTransitions());
        assertSame(originalLedger, ledger.findByOrderId(original.getId()).orElseThrow());
        assertTrue(ledger.findAllRefunds().isEmpty());
        assertTrue(events.findAll().isEmpty());
        assertTrue(notifications.isEmpty());

        doCallRealMethod().when(orders).save(any());
        doCallRealMethod().when(ledger).appendRefund(any());
        doCallRealMethod().when(ledger).save(any());
        doCallRealMethod().when(events).append(any());
        assertEquals(OrderStatus.REFUNDED, service.refund(original.getId().toString(), operator).status());
        assertEquals(1, ledger.findAllRefunds().size());
        assertEquals(1, events.findAll().size());
    }

    @Test
    void notificationFailureCannotUndoACommittedRefund() {
        publisher.subscribe(event -> { throw new IllegalStateException("observer failed"); });
        assertDoesNotThrow(() -> service.refund(original.getId().toString(), operator));
        assertEquals(OrderStatus.REFUNDED, orders.findById(original.getId()).orElseThrow().getStatus());
        assertEquals(1, ledger.findAllRefunds().size());
        assertEquals(1, events.findAll().size());
    }

    @Test
    void concurrentServiceInstancesProduceOneRefundAndOneEvent() throws Exception {
        CountDownLatch start = new CountDownLatch(1);
        RefundService secondService = serviceAt(NOW);
        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            Future<Boolean> first = executor.submit(() -> refundAfterStart(service, start));
            Future<Boolean> second = executor.submit(() -> refundAfterStart(secondService, start));
            start.countDown();
            assertNotEquals(first.get(5, TimeUnit.SECONDS), second.get(5, TimeUnit.SECONDS));
        }
        assertEquals(1, ledger.findAllRefunds().size());
        assertEquals(1, events.findAll().size());
        assertEquals(OrderStatus.REFUNDED, orders.findById(original.getId()).orElseThrow().getStatus());
        assertEquals(OrderStatus.REFUNDED, ledger.findByOrderId(original.getId()).orElseThrow().status());
    }

    private boolean refundAfterStart(RefundService refunds, CountDownLatch start) throws InterruptedException {
        if (!start.await(5, TimeUnit.SECONDS)) throw new AssertionError("Refund test did not start");
        try {
            refunds.refund(original.getId().toString(), operator);
            return true;
        } catch (RefundConflictException duplicate) {
            assertEquals("Order has already been refunded", duplicate.getMessage());
            return false;
        }
    }

    private RefundService serviceAt(Instant at) {
        return new RefundService(orders, ledger, projection, transitions, recorder, publisher,
                Clock.fixed(at, ZoneOffset.UTC));
    }

    private UserAccount account(UserRole role) {
        String id = UUID.randomUUID().toString();
        return new UserAccount(id, "refund-" + id, "unused-test-hash", role);
    }

    private Order saveOrder(OrderStatus status, BigDecimal total, Charge breakdown) {
        Order order = Order.restore(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                List.of(new OrderLine(UUID.randomUUID(), "Original meal", 2, new BigDecimal("12.35"))),
                PLACED, status, PLACED.plusSeconds(1));
        orders.save(order);
        Map<OrderStatus, Instant> timestamps = status == OrderStatus.PLACED ? Map.of(OrderStatus.PLACED, PLACED)
                : Map.of(OrderStatus.PLACED, PLACED, status, DELIVERED);
        ledger.save(new LedgerEntry(order.getId(), order.getCustomerId(), order.getRestaurantId(), UUID.randomUUID(),
                order.getOrderLines(), status, PLACED, timestamps, total, breakdown));
        return order;
    }
}
