package com.midnightmunchies.application.dlv;

import com.midnightmunchies.application.dash.DispatchBacklog;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Component
public class DispatchBacklogFeed implements DispatchBacklog {
    private final DispatchQueueService dispatchQueue;

    public DispatchBacklogFeed(DispatchQueueService dispatchQueue) {
        this.dispatchQueue = Objects.requireNonNull(dispatchQueue, "dispatchQueue must not be null");
    }

    @Override
    public List<UUID> waitingOrderIds() {
        return dispatchQueue.view();
    }
}
