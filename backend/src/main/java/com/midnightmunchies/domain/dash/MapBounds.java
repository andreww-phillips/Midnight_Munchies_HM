package com.midnightmunchies.domain.dash;

import java.util.Collection;
import java.util.Objects;

public record MapBounds(double minLat, double maxLat, double minLon, double maxLon) {

    public MapBounds {
        if (minLat > maxLat || minLon > maxLon) {
            throw new IllegalArgumentException("min bounds must not exceed max bounds");
        }
    }

    public static MapBounds enclosing(Collection<? extends MapMarker> markers) {
        Objects.requireNonNull(markers, "markers must not be null");
        if (markers.isEmpty()) {
            return null;
        }
        double minLat = Double.POSITIVE_INFINITY;
        double maxLat = Double.NEGATIVE_INFINITY;
        double minLon = Double.POSITIVE_INFINITY;
        double maxLon = Double.NEGATIVE_INFINITY;
        for (MapMarker marker : markers) {
            minLat = Math.min(minLat, marker.latitude());
            maxLat = Math.max(maxLat, marker.latitude());
            minLon = Math.min(minLon, marker.longitude());
            maxLon = Math.max(maxLon, marker.longitude());
        }
        return new MapBounds(minLat, maxLat, minLon, maxLon);
    }
}
