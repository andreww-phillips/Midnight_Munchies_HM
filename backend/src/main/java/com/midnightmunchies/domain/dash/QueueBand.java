package com.midnightmunchies.domain.dash;

import com.midnightmunchies.domain.ord.RestaurantStatus;

import java.util.Objects;

public enum QueueBand {
    GREEN,
    AMBER,
    RED;

    public static final int AMBER_THRESHOLD = 3;
    public static final int RED_THRESHOLD = 6;

    public static QueueBand of(RestaurantStatus status, int queued) {
        Objects.requireNonNull(status, "status must not be null");
        if (queued < 0) {
            throw new IllegalArgumentException("queued must not be negative");
        }
        if (status != RestaurantStatus.OPEN || queued >= RED_THRESHOLD) {
            return RED;
        }
        if (queued >= AMBER_THRESHOLD) {
            return AMBER;
        }
        return GREEN;
    }
}
