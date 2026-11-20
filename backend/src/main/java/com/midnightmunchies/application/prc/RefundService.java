package com.midnightmunchies.application.prc;

import com.midnightmunchies.application.ord.AuthorizationException;
import com.midnightmunchies.application.ord.EventIdRecorder;
import com.midnightmunchies.application.ord.OrderEventPublisher;
import com.midnightmunchies.application.ord.OrderNotFoundException;
import com.midnightmunchies.application.ord.OrderTransitions;
import com.midnightmunchies.application.reg.AuthenticationRequiredException;
import com.midnightmunchies.domain.ord.Actor;
import com.midnightmunchies.domain.ord.Order;
import com.midnightmunchies.domain.ord.OrderEvent;
import com.midnightmunchies.domain.ord.OrderRepository;
import com.midnightmunchies.domain.ord.OrderStatus;
import com.midnightmunchies.domain.prc.LedgerEntry;
import com.midnightmunchies.domain.prc.Refund;
import com.midnightmunchies.domain.reg.UserAccount;
import com.midnightmunchies.domain.reg.UserRole;
import com.midnightmunchies.technicalservices.LedgerRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;

/** Simulator full refunds using frozen charge facts, with coordinated in-memory commits. */
@Service
public class RefundService {

    private final OrderRepository orders;
    private final LedgerRepository ledger;
    private final OrderLedgerService orderLedger;
    private final OrderTransitions transitions;
    private final EventIdRecorder eventRecorder;
    private final OrderEventPublisher publisher;
    private final Clock clock;

    @Autowired
    public RefundService(OrderRepository orders, LedgerRepository ledger, OrderLedgerService orderLedger,
                         OrderTransitions transitions, EventIdRecorder eventRecorder, OrderEventPublisher publisher) {
        this(orders, ledger, orderLedger, transitions, eventRecorder, publisher, Clock.systemUTC());
    }

    RefundService(OrderRepository orders, LedgerRepository ledger, OrderLedgerService orderLedger,
                  OrderTransitions transitions, EventIdRecorder eventRecorder, OrderEventPublisher publisher, Clock clock) {
        this.orders = orders;
        this.ledger = ledger;
        this.orderLedger = orderLedger;
        this.transitions = transitions;
        this.eventRecorder = eventRecorder;
        this.publisher = publisher;
        this.clock = clock;
    }

    public Result refund(String orderId, UserAccount account) {
        requireOperator(account);
        UUID id;
        try {
            id = UUID.fromString(orderId);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Order ID must be a valid UUID");
        }
        Refund refund;
        OrderEvent event;
        // Same lock order as lifecycle projection writers. Notifications run after release.
        synchronized (orderLedger) {
            synchronized (ledger) {
                Order order = orders.findById(id)
                        .orElseThrow(() -> new OrderNotFoundException("Order does not exist"));
                if (order.getStatus() == OrderStatus.REFUNDED || ledger.findRefundByOrderId(id).isPresent()) {
                    throw new RefundConflictException("Order has already been refunded");
                }
                if (order.getStatus() != OrderStatus.DELIVERED) {
                    throw new RefundConflictException("Only delivered orders can be refunded");
                }
                LedgerEntry previous = ledger.findByOrderId(id).orElse(null);
                if (previous == null || previous.totalCharge() == null) {
                    throw new RefundConflictException("A recorded charge is required for a refund");
                }
                if (previous.status() != OrderStatus.DELIVERED
                        || !previous.timestamps().containsKey(OrderStatus.DELIVERED)
                        || !order.getCustomerId().equals(previous.customerId())
                        || !order.getRestaurantId().equals(previous.restaurantId())) {
                    throw new RefundConflictException("Order ledger is not ready for a refund");
                }
                Instant at = clock.instant();
                if (previous.timestamps().values().stream().anyMatch(time -> time.isAfter(at))) {
                    throw new RefundConflictException("Refund time precedes recorded order timestamps");
                }
                refund = new Refund(UUID.randomUUID(), id, previous.totalCharge(), at, UUID.fromString(account.id()));
                Map<OrderStatus, Instant> times = new EnumMap<>(OrderStatus.class);
                times.putAll(previous.timestamps());
                times.put(OrderStatus.REFUNDED, at);
                LedgerEntry refundedOrder = new LedgerEntry(previous.orderId(), previous.customerId(),
                        previous.restaurantId(), previous.courierId(), previous.orderLines(), OrderStatus.REFUNDED,
                        previous.createdAt(), times, previous.totalCharge(), previous.charge());
                try {
                    event = transitions.refund(id, new Actor(UUID.fromString(account.id()), account.role()), at,
                            recorded -> ledger.commitRefund(refund, refundedOrder, () -> eventRecorder.onEvent(recorded)));
                } catch (RuntimeException failure) {
                    throw new IllegalStateException("Refund commit failed", failure);
                }
            }
        }
        publisher.publishExcept(event, eventRecorder);
        return new Result(id, OrderStatus.REFUNDED, refund, event.eventId());
    }

    private void requireOperator(UserAccount account) {
        if (account == null) {
            throw new AuthenticationRequiredException("Authentication is required");
        }
        if (account.role() != UserRole.PLATFORM_OPERATOR) {
            throw new AuthorizationException("Only platform operators can refund orders");
        }
    }

    public record Result(UUID orderId, OrderStatus status, Refund refund, UUID eventId) {
    }
}
