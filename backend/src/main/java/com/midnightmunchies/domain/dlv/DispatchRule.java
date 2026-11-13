package com.midnightmunchies.domain.dlv;

import java.util.Collection;
import java.util.Comparator;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;

public final class DispatchRule {

    private static final Comparator<Courier> LOWEST_ID =
            Comparator.comparing(courier -> courier.getId().toString());

    public Optional<Courier> nearest(Location pickup, Collection<Courier> candidates) {
        Objects.requireNonNull(pickup, "pickup must not be null");
        Comparator<Courier> byDistance = Comparator.comparingDouble(courier -> distance(pickup, courier.getLocation()));
        return idle(candidates).min(byDistance.thenComparing(LOWEST_ID));
    }

    public Optional<Courier> lowestId(Collection<Courier> candidates) {
        return idle(candidates).min(LOWEST_ID);
    }

    public static double distance(Location from, Location to) {
        return Math.hypot(from.latitude() - to.latitude(), from.longitude() - to.longitude());
    }

    private Stream<Courier> idle(Collection<Courier> candidates) {
        Objects.requireNonNull(candidates, "candidates must not be null");
        return candidates.stream().filter(courier -> courier.getState() == CourierState.IDLE);
    }
}
