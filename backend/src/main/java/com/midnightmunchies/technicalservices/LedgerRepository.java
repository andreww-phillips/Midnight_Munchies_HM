package com.midnightmunchies.technicalservices;

import com.midnightmunchies.domain.prc.LedgerEntry;
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
}
