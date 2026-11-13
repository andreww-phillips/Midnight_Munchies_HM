package com.midnightmunchies.domain.dash;

import com.midnightmunchies.domain.geo.MontrealServiceArea;

import java.util.Objects;

public record CourierMarker(String id, double latitude, double longitude, String status, String area) implements MapMarker {

    public static final String OFFLINE = "OFFLINE";

    public CourierMarker {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(status, "status must not be null");
    }

    public CourierMarker(String id, double latitude, double longitude, String status) {
        this(id, latitude, longitude, status, MontrealServiceArea.areaOf(latitude, longitude));
    }

    public boolean isOnDuty() {
        return !OFFLINE.equals(status);
    }
}
