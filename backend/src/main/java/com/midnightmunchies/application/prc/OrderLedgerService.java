package com.midnightmunchies.application.prc;

import com.midnightmunchies.domain.ord.Order;
import com.midnightmunchies.domain.ord.OrderEvent;
import com.midnightmunchies.domain.ord.OrderEventListener;
import com.midnightmunchies.domain.ord.OrderRepository;
import com.midnightmunchies.domain.ord.OrderStatus;
import com.midnightmunchies.domain.prc.LedgerEntry;
import com.midnightmunchies.domain.prc.Charge;
import com.midnightmunchies.domain.reg.UserRole;
import com.midnightmunchies.technicalservices.LedgerRepository;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** Observes order facts for PRC without issuing commands or calculating charges. */
@Service
public class OrderLedgerService implements OrderEventListener {

    private final OrderRepository orders;
    private final LedgerRepository ledger;

    public OrderLedgerService(OrderRepository orders, LedgerRepository ledger) {
        this.orders = Objects.requireNonNull(orders, "orders must not be null");
        this.ledger = Objects.requireNonNull(ledger, "ledger must not be null");
    }

    /** Records placement separately because it is not a state-transition event. */
    public synchronized LedgerEntry recordCreated(Order order) {
        Objects.requireNonNull(order, "order must not be null");
        return ledger.findByOrderId(order.getId()).orElseGet(() -> ledger.save(placement(order)));
    }

    @Override
    public synchronized void onEvent(OrderEvent event) {
        Objects.requireNonNull(event, "event must not be null");
        Order order = orders.findById(event.orderId())
                .orElseThrow(() -> new IllegalArgumentException("Cannot record an event for a missing order"));
        if (!order.getRestaurantId().equals(event.restaurantId())) {
            throw new IllegalArgumentException("Event restaurant does not match the order");
        }
        LedgerEntry previous = ledger.findByOrderId(order.getId()).orElseGet(() -> placement(order));
        // The current lifecycle never revisits a state. Replayed events must not
        // duplicate records, change original timestamps, or undo later states.
        if (previous.timestamps().containsKey(event.toStatus())) {
            return;
        }

        Map<OrderStatus, Instant> times = new EnumMap<>(OrderStatus.class);
        times.putAll(previous.timestamps());
        times.put(event.toStatus(), event.occurredAt());
        Instant latest = previous.timestamps().get(previous.status());
        boolean advances = event.occurredAt().isAfter(latest)
                || (event.occurredAt().equals(latest)
                && (event.fromStatus() == previous.status() || event.toStatus() == order.getStatus()));
        UUID carriedBy = previous.courierId();
        if (carriedBy == null && event.actorRole() == UserRole.COURIER
                && (event.toStatus() == OrderStatus.PICKED_UP || event.toStatus() == OrderStatus.DELIVERED)) {
            carriedBy = event.actorId();
        }
        ledger.save(new LedgerEntry(previous.orderId(), previous.customerId(), previous.restaurantId(),
                carriedBy, previous.orderLines(), advances ? event.toStatus() : previous.status(),
                previous.createdAt(), times, previous.totalCharge(), previous.charge()));
    }

    /** Records pricing facts against the latest snapshot under the same lock as event updates. */
    public synchronized LedgerEntry recordCharge(UUID orderId, Charge charge) {
        Objects.requireNonNull(charge, "charge must not be null");
        LedgerEntry previous = ledger.findByOrderId(orderId)
                .orElseThrow(() -> new IllegalArgumentException("Order does not exist"));
        if (previous.status() != OrderStatus.DELIVERED) {
            throw new IllegalArgumentException("Only a delivered order can receive a charge");
        }
        return ledger.save(new LedgerEntry(previous.orderId(), previous.customerId(), previous.restaurantId(),
                previous.courierId(), previous.orderLines(), previous.status(), previous.createdAt(),
                previous.timestamps(), charge.total(), charge));
    }

    private LedgerEntry placement(Order order) {
        return new LedgerEntry(order.getId(), order.getCustomerId(), order.getRestaurantId(), null,
                order.getOrderLines(), OrderStatus.PLACED, order.getCreatedAt(),
                Map.of(OrderStatus.PLACED, order.getCreatedAt()), null);
    }
}
