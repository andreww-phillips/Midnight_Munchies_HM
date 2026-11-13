package com.midnightmunchies.domain.dash;

import java.util.Objects;
import java.util.UUID;

public record WaitingOrder(UUID orderId, UUID restaurantId, String restaurantName) {

    public WaitingOrder {
        Objects.requireNonNull(orderId, "orderId must not be null");
        Objects.requireNonNull(restaurantId, "restaurantId must not be null");
    }
}
