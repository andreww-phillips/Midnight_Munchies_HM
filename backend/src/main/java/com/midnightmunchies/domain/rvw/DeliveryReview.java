package com.midnightmunchies.domain.rvw;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record DeliveryReview(
        UUID orderId,
        UUID customerId,
        UUID targetId,
        ReviewTarget target,
        int rating,
        String text,
        Instant createdAt
) {
    public DeliveryReview {
        Objects.requireNonNull(orderId, "orderId must not be null");
        Objects.requireNonNull(customerId, "customerId must not be null");
        Objects.requireNonNull(targetId, "targetId must not be null");
        Objects.requireNonNull(target, "target must not be null");
        Objects.requireNonNull(createdAt, "createdAt must not be null");
        if (rating < 1 || rating > 5) {
            throw new IllegalArgumentException("Rating must be between 1 and 5");
        }
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("Review text is required");
        }
        text = text.strip();
        if (text.length() > 1000) {
            throw new IllegalArgumentException("Review text must be 1000 characters or fewer");
        }
    }
}
