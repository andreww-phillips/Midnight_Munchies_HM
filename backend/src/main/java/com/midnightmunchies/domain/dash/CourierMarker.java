package com.midnightmunchies.domain.dash;

import java.util.Objects;

public record CourierMarker(String id, double latitude, double longitude, String status) implements MapMarker {

    public static final String OFFLINE = "OFFLINE";

    public CourierMarker {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(status, "status must not be null");
    }

    public boolean isOnDuty() {
        return !OFFLINE.equals(status);
    }
}
