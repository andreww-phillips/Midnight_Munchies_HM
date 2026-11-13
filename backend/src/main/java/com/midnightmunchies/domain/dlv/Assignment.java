package com.midnightmunchies.domain.dlv;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record Assignment(
        UUID id,
        UUID orderId,
        UUID courierId,
        AssignmentStatus status,
        Instant offeredAt,
        Instant acceptedAt,
        Instant pickedUpAt,
        Instant deliveredAt,
        Instant releasedAt,
        String releasedBy
) {
    public Assignment {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(orderId, "orderId must not be null");
        Objects.requireNonNull(courierId, "courierId must not be null");
        Objects.requireNonNull(status, "status must not be null");
        Objects.requireNonNull(offeredAt, "offeredAt must not be null");
    }

    public static Assignment offer(UUID orderId, UUID courierId, Instant offeredAt) {
        return new Assignment(UUID.randomUUID(), orderId, courierId, AssignmentStatus.OFFERED,
                offeredAt, null, null, null, null, null);
    }

    public Assignment accept(Instant at) {
        requireStatus(AssignmentStatus.OFFERED);
        return new Assignment(id, orderId, courierId, AssignmentStatus.ACCEPTED, offeredAt,
                Objects.requireNonNull(at, "at must not be null"), null, null, null, null);
    }

    public Assignment decline(Instant at) {
        requireStatus(AssignmentStatus.OFFERED);
        return new Assignment(id, orderId, courierId, AssignmentStatus.DECLINED, offeredAt,
                null, null, null);
    }

    public Assignment pickUp(Instant at) {
        requireStatus(AssignmentStatus.ACCEPTED);
        return new Assignment(id, orderId, courierId, AssignmentStatus.PICKED_UP, offeredAt,
                acceptedAt, Objects.requireNonNull(at, "at must not be null"), null, null, null);
    }

    public Assignment deliver(Instant at) {
        requireStatus(AssignmentStatus.PICKED_UP);
        return new Assignment(id, orderId, courierId, AssignmentStatus.DELIVERED, offeredAt,
                acceptedAt, pickedUpAt, Objects.requireNonNull(at, "at must not be null"), null, null);
    }

    public Assignment decline(Instant at) {
        requireStatus(AssignmentStatus.OFFERED);
        return new Assignment(id, orderId, courierId, AssignmentStatus.DECLINED, offeredAt,
                null, null, null, Objects.requireNonNull(at, "at must not be null"), courierId.toString());
    }

    public Assignment reassign(String operatorId, Instant at) {
        if (status != AssignmentStatus.OFFERED && status != AssignmentStatus.ACCEPTED) {
            throw new IllegalStateException("Only an offered or accepted order can be reassigned");
        }
        if (operatorId == null || operatorId.isBlank()) {
            throw new IllegalArgumentException("operatorId must not be blank");
        }
        return new Assignment(id, orderId, courierId, AssignmentStatus.REASSIGNED, offeredAt,
                acceptedAt, null, null, Objects.requireNonNull(at, "at must not be null"), operatorId);
    }

    public boolean isActive() {
        return status == AssignmentStatus.OFFERED
                || status == AssignmentStatus.ACCEPTED
                || status == AssignmentStatus.PICKED_UP;
    }

    private void requireStatus(AssignmentStatus expected) {
        if (status != expected) {
            throw new IllegalStateException("Assignment is " + status + ", not " + expected);
        }
    }
}
