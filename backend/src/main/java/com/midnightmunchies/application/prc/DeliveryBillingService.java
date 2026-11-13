package com.midnightmunchies.application.prc;

import com.midnightmunchies.domain.ord.OrderEvent;
import com.midnightmunchies.domain.ord.OrderEventListener;
import com.midnightmunchies.domain.ord.OrderStatus;
import com.midnightmunchies.domain.prc.Charge;
import com.midnightmunchies.domain.prc.LedgerEntry;
import com.midnightmunchies.technicalservices.LedgerRepository;
import org.springframework.stereotype.Service;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Coordinates delivery-time billing while preserving the original recorded amounts. */
@Service
public class DeliveryBillingService implements OrderEventListener {

    private final OrderLedgerService orderLedger;
    private final LedgerRepository ledger;
    private final Optional<DeliveryPricing> pricing;

    public DeliveryBillingService(OrderLedgerService orderLedger, LedgerRepository ledger,
                                  Optional<DeliveryPricing> pricing) {
        this.orderLedger = Objects.requireNonNull(orderLedger, "orderLedger must not be null");
        this.ledger = Objects.requireNonNull(ledger, "ledger must not be null");
        this.pricing = Objects.requireNonNull(pricing, "pricing must not be null");
    }

    @Override
    public void onEvent(OrderEvent event) {
        Objects.requireNonNull(event, "event must not be null");
        if (event.toStatus() != OrderStatus.DELIVERED) {
            return;
        }
        // Listener ordering is unspecified. Ensure the delivery facts are present
        // before billing; replaying this projection is safe and preserves charges.
        orderLedger.onEvent(event);
        recordDeliveryCharge(event.orderId());
    }

    /** Can also retry a previously unpriced delivery after a temporary pricing failure. */
    public synchronized Optional<Charge> recordDeliveryCharge(UUID orderId) {
        LedgerEntry entry = ledger.findByOrderId(orderId)
                .orElseThrow(() -> new IllegalArgumentException("Order does not exist"));
        if (entry.charge() != null) {
            return Optional.of(entry.charge());
        }
        if (entry.status() != OrderStatus.DELIVERED || entry.totalCharge() != null || pricing.isEmpty()) {
            // A historical total without a breakdown must not be repriced using newer rules.
            return Optional.empty();
        }
        return Objects.requireNonNull(pricing.get().calculate(entry.orderLines()), "pricing result must not be null")
                .map(charge -> orderLedger.recordCharge(orderId, charge).charge());
    }
}
