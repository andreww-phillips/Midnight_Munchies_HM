package com.midnightmunchies.application.dlv;

import com.midnightmunchies.application.dash.OnDutyCouriers;
import com.midnightmunchies.domain.dash.CourierMarker;
import com.midnightmunchies.domain.dlv.Courier;
import com.midnightmunchies.technicalservices.CourierRepository;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;

@Component
public class CourierMapFeed implements OnDutyCouriers {
    private final CourierRepository couriers;

    public CourierMapFeed(CourierRepository couriers) {
        this.couriers = Objects.requireNonNull(couriers, "couriers must not be null");
    }

    @Override
    public List<CourierMarker> onDuty() {
        return couriers.findAll().stream()
                .filter(Courier::isOnDuty)
                .map(courier -> new CourierMarker(courier.getId().toString(), courier.getLocation().latitude(),
                        courier.getLocation().longitude(), courier.getState().name()))
                .toList();
    }
}
