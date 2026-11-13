package com.midnightmunchies.domain.dlv;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public final class DispatchQueue {
    private final ArrayDeque<UUID> orders = new ArrayDeque<>();
    private final LinkedHashSet<UUID> queued = new LinkedHashSet<>();

    public synchronized void enqueue(UUID orderId) {
        Objects.requireNonNull(orderId, "orderId must not be null");
        if (queued.add(orderId)) {
            orders.addLast(orderId);
        }
    }

    public synchronized Optional<UUID> poll() {
        UUID orderId = orders.pollFirst();
        if (orderId != null) {
            queued.remove(orderId);
        }
        return Optional.ofNullable(orderId);
    }

    public synchronized Optional<UUID> peek() {
        return Optional.ofNullable(orders.peekFirst());
    }

    public synchronized void remove(UUID orderId) {
        if (queued.remove(orderId)) {
            orders.remove(orderId);
        }
    }

    public synchronized List<UUID> snapshot() {
        return List.copyOf(new ArrayList<>(orders));
    }
}
