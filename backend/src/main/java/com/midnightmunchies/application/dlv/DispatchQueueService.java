package com.midnightmunchies.application.dlv;

import com.midnightmunchies.domain.dlv.DispatchQueue;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class DispatchQueueService {
    private final DispatchQueue queue = new DispatchQueue();

    public void enqueue(UUID orderId) {
        queue.enqueue(orderId);
    }

    public Optional<UUID> poll() {
        return queue.poll();
    }

    public void remove(UUID orderId) {
        queue.remove(orderId);
    }

    public List<UUID> view() {
        return queue.snapshot();
    }
}
