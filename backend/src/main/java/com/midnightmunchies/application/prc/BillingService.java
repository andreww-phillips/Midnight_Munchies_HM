package com.midnightmunchies.application.prc;

import com.midnightmunchies.domain.ord.OrderStatus;
import com.midnightmunchies.domain.prc.OrderSummary;
import com.midnightmunchies.technicalservices.LedgerRepository;
import org.springframework.stereotype.Service;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Provides post-delivery summaries from the ledger without recomputing or recording charges. */
@Service
public class BillingService {

    private final LedgerRepository ledger;

    public BillingService(LedgerRepository ledger) {
        this.ledger = Objects.requireNonNull(ledger, "ledger must not be null");
    }

    /**
     * Builds a summary on request after delivery; earlier or missing orders have none.
     * A future pricing integration records the charge in the ledger. Subsequent reads
     * then reflect it automatically, while previously returned summaries remain unchanged.
     * The calling controller must enforce authentication and order ownership.
     */
    public Optional<OrderSummary> findSummary(UUID orderId) {
        return ledger.findByOrderId(orderId)
                .filter(entry -> entry.status() == OrderStatus.DELIVERED || entry.status() == OrderStatus.REFUNDED)
                .filter(entry -> entry.timestamps().containsKey(OrderStatus.DELIVERED))
                .map(OrderSummary::new);
    }
}
