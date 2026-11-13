package com.midnightmunchies.application.dlv;

import com.midnightmunchies.domain.dash.CourierMarker;
import com.midnightmunchies.domain.dlv.Courier;
import com.midnightmunchies.domain.dlv.Location;
import com.midnightmunchies.technicalservices.CourierRepository;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CourierMapFeedTests {

    @Test
    void publishesOnlyOnDutyCouriersWithTheirPositionAndState() {
        CourierRepository couriers = new CourierRepository();
        couriers.save(new Courier(UUID.randomUUID(), new Location(45.50, -73.57)));
        Courier idle = new Courier(UUID.randomUUID(), new Location(45.51, -73.58));
        idle.goOnDuty();
        couriers.save(idle);

        List<CourierMarker> markers = new CourierMapFeed(couriers).onDuty();

        assertEquals(List.of(new CourierMarker(idle.getId().toString(), 45.51, -73.58, "IDLE")), markers);
    }
}
