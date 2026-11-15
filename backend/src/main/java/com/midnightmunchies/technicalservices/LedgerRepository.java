package com.midnightmunchies.technicalservices;

import com.midnightmunchies.domain.prc.LedgerEntry;
import com.midnightmunchies.domain.prc.Refund;
import com.midnightmunchies.domain.ord.OrderStatus;
import org.springframework.stereotype.Repository;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** In-memory order ledger. Records live for the lifetime of the backend process. */
@Repository
public class LedgerRepository {

    private final Map<UUID, LedgerEntry> entries = new HashMap<>();
    private final Map<UUID, Refund> refundsByOrderId = new HashMap<>();

    /**
     * Inserts or replaces one order's snapshot, returning the saved record.
     * Once a charge is recorded it cannot be changed or removed by a later save.
     * Synchronization makes that check and the replacement a single operation.
     */
    public synchronized LedgerEntry save(LedgerEntry entry) {
        Objects.requireNonNull(entry, "entry must not be null");
        LedgerEntry previous = entries.get(entry.orderId());
        if (previous != null && previous.totalCharge() != null
                && (entry.totalCharge() == null
                || previous.totalCharge().compareTo(entry.totalCharge()) != 0)) {
            throw new IllegalArgumentException("A recorded charge cannot be changed or removed");
        }
        if (previous != null && previous.charge() != null && !previous.charge().equals(entry.charge())) {
            throw new IllegalArgumentException("A recorded charge breakdown cannot be changed or removed");
        }
        entries.put(entry.orderId(), entry);
        return entry;
    }

    public synchronized Optional<LedgerEntry> findByOrderId(UUID orderId) {
        Objects.requireNonNull(orderId, "orderId must not be null");
        return Optional.ofNullable(entries.get(orderId));
    }

    /** Returns an unmodifiable snapshot, oldest first, with order ID breaking time ties. */
    public synchronized List<LedgerEntry> findAll() {
        return entries.values().stream()
                .sorted(Comparator.comparing(LedgerEntry::createdAt).thenComparing(LedgerEntry::orderId))
                .toList();
    }

    /**
     * Appends one full refund without replacing the order snapshot or its charge.
     * Validation and insertion share the repository lock, including duplicate checks.
     * Operator authorization and the authoritative order transition belong to the refund service.
     */
    public synchronized Refund appendRefund(Refund refund) {
        Objects.requireNonNull(refund, "refund must not be null");
        LedgerEntry order = entries.get(refund.orderId());
        if (order == null) {
            throw new IllegalArgumentException("Order does not exist in the ledger");
        }
        if (refundsByOrderId.containsKey(refund.orderId())) {
            throw new IllegalStateException("Order has already been refunded");
        }
        if (refundsByOrderId.values().stream().anyMatch(existing -> existing.refundId().equals(refund.refundId()))) {
            throw new IllegalArgumentException("Refund ID already exists");
        }
        if (order.totalCharge() == null) {
            throw new IllegalStateException("A recorded charge is required for a refund");
        }
        if (refund.amount().compareTo(order.totalCharge()) != 0) {
            throw new IllegalArgumentException("Refund amount must equal the original recorded charge");
        }
        if (refund.refundedAt().isBefore(order.createdAt())
                || order.timestamps().values().stream().anyMatch(time -> time.isAfter(refund.refundedAt()))) {
            throw new IllegalArgumentException("Refund timestamp must not precede recorded order timestamps");
        }
        refundsByOrderId.put(refund.orderId(), refund);
        return refund;
    }

    public synchronized Optional<Refund> findRefundByOrderId(UUID orderId) {
        Objects.requireNonNull(orderId, "orderId must not be null");
        return Optional.ofNullable(refundsByOrderId.get(orderId));
    }

    /**
     * Commits refund facts and the matching projection together. Required event recording
     * runs last; any failure restores both maps before releasing the repository lock.
     * The caller coordinates this with the authoritative ORD save.
     */
    public synchronized void commitRefund(Refund refund, LedgerEntry refundedOrder, Runnable recordEvent) {
        Objects.requireNonNull(refund, "refund must not be null");
        Objects.requireNonNull(refundedOrder, "refundedOrder must not be null");
        Objects.requireNonNull(recordEvent, "recordEvent must not be null");
        if (!refund.orderId().equals(refundedOrder.orderId())
                || refundedOrder.status() != OrderStatus.REFUNDED
                || !refund.refundedAt().equals(refundedOrder.timestamps().get(OrderStatus.REFUNDED))) {
            throw new IllegalArgumentException("Refund must match the refunded order snapshot");
        }
        LedgerEntry previous = entries.get(refund.orderId());
        Refund previousRefund = refundsByOrderId.get(refund.orderId());
        try {
            appendRefund(refund);
            save(refundedOrder);
            recordEvent.run();
        } catch (RuntimeException failure) {
            if (previous == null) entries.remove(refund.orderId());
            else entries.put(refund.orderId(), previous);
            if (previousRefund == null) refundsByOrderId.remove(refund.orderId());
            else refundsByOrderId.put(refund.orderId(), previousRefund);
            throw failure;
        }
    }

    /** Returns an unmodifiable snapshot, oldest refund first, with refund ID breaking time ties. */
    public synchronized List<Refund> findAllRefunds() {
        return refundsByOrderId.values().stream()
                .sorted(Comparator.comparing(Refund::refundedAt).thenComparing(Refund::refundId))
                .toList();
    }
}
