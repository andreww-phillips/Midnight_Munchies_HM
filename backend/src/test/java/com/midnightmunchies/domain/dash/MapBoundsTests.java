package com.midnightmunchies.domain.dash;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MapBoundsTests {

    @Test
    void singlePointGivesZeroSpanBounds() {
        MapBounds bounds = MapBounds.enclosing(List.of(courier("1", 45.5, -73.5)));

        assertEquals(new MapBounds(45.5, 45.5, -73.5, -73.5), bounds);
    }

    @Test
    void severalPointsGiveTheirMinAndMax() {
        MapBounds bounds = MapBounds.enclosing(List.of(
                courier("1", 45.50, -73.60),
                courier("2", 45.53, -73.55),
                courier("3", 45.51, -73.58)));

        assertEquals(new MapBounds(45.50, 45.53, -73.60, -73.55), bounds);
    }

    @Test
    void noPointsGiveNoBounds() {
        assertNull(MapBounds.enclosing(List.of()));
    }

    @Test
    void invertedBoundsAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> new MapBounds(46, 45, -73, -74));
    }

    private static CourierMarker courier(String id, double latitude, double longitude) {
        return new CourierMarker(id, latitude, longitude, "IDLE");
    }
}
