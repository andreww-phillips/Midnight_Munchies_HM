package com.midnightmunchies.domain.dlv;

import java.util.Objects;
import java.util.UUID;

public final class Courier {
    private final UUID id;
    private boolean onDuty;
    private CourierState state = CourierState.OFFLINE;
    private UUID orderId;

    public Courier(UUID id) {
        this.id = Objects.requireNonNull(id, "id must not be null");
    }

    public UUID getId() {
        return id;
    }

    public boolean isOnDuty() {
        return onDuty;
    }

    public CourierState getState() {
        return state;
    }

    public UUID getOrderId() {
        return orderId;
    }

    public boolean isIdleOnDuty() {
        return onDuty && state == CourierState.IDLE;
    }

    public void goOnDuty() {
        if (onDuty) {
            return;
        }
        onDuty = true;
        state = CourierState.IDLE;
    }

    public void goOffline() {
        if (orderId != null) {
            throw new IllegalStateException("You cannot go offline while assigned to an order");
        }
        onDuty = false;
        state = CourierState.OFFLINE;
    }

    public void offer(UUID nextOrderId) {
        Objects.requireNonNull(nextOrderId, "orderId must not be null");
        if (!isIdleOnDuty()) {
            throw new IllegalStateException("Only idle, on-duty couriers can receive an offer");
        }
        orderId = nextOrderId;
        state = CourierState.OFFERED;
    }

    public void accept(UUID expectedOrderId) {
        requireOrder(expectedOrderId, CourierState.OFFERED);
        state = CourierState.ASSIGNED;
    }

    public void pickUp(UUID expectedOrderId) {
        requireOrder(expectedOrderId, CourierState.ASSIGNED);
        state = CourierState.CARRYING;
    }

    public void deliver(UUID expectedOrderId) {
        requireOrder(expectedOrderId, CourierState.CARRYING);
        orderId = null;
        state = CourierState.IDLE;
    }

    public void release(UUID expectedOrderId) {
        if (!Objects.equals(orderId, expectedOrderId)) {
            throw new IllegalStateException("Courier is not assigned to this order");
        }
        orderId = null;
        state = onDuty ? CourierState.IDLE : CourierState.OFFLINE;
    }

    private void requireOrder(UUID expectedOrderId, CourierState expectedState) {
        if (!Objects.equals(orderId, expectedOrderId) || state != expectedState) {
            throw new IllegalStateException("Courier order is not in the required state");
        }
    }
}
