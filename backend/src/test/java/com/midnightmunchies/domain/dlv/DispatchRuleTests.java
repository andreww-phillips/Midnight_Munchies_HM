package com.midnightmunchies.domain.dlv;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class DispatchRuleTests {

    private static final Location RESTAURANT = new Location(45.50, -73.57);
    private final DispatchRule rule = new DispatchRule();

    @Test
    void picksTheIdleCourierWithTheShortestStraightLineDistance() {
        Courier far = idle("00000000-0000-0000-0000-000000000001", 45.60, -73.70);
        Courier near = idle("00000000-0000-0000-0000-000000000002", 45.51, -73.58);

        assertEquals(near, rule.nearest(RESTAURANT, List.of(far, near)).orElseThrow());
    }

    @Test
    void breaksDistanceTiesWithTheLowestCourierId() {
        Courier high = idle("f0000000-0000-0000-0000-000000000000", 45.51, -73.57);
        Courier low = idle("10000000-0000-0000-0000-000000000000", 45.49, -73.57);

        assertEquals(low, rule.nearest(RESTAURANT, List.of(high, low)).orElseThrow());
        assertEquals(low, rule.lowestId(List.of(high, low)).orElseThrow());
    }

    @Test
    void ignoresCouriersThatAreNotIdle() {
        Courier offline = new Courier(UUID.randomUUID(), RESTAURANT);
        Courier idleFarther = idle(UUID.randomUUID().toString(), 45.60, -73.60);

        assertEquals(idleFarther, rule.nearest(RESTAURANT, List.of(offline, idleFarther)).orElseThrow());
        assertTrue(rule.nearest(RESTAURANT, List.of(offline)).isEmpty());
        assertTrue(rule.nearest(RESTAURANT, List.of()).isEmpty());
    }

    @Test
    void distanceIsStraightLineOnTheCoordinateGrid() {
        assertEquals(5.0, DispatchRule.distance(new Location(0, 0), new Location(3, 4)), 1e-9);
    }

    private Courier idle(String id, double latitude, double longitude) {
        Courier courier = new Courier(UUID.fromString(id), new Location(latitude, longitude));
        courier.goOnDuty();
        return courier;
    }
}
