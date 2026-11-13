package com.midnightmunchies.technicalservices;

import com.midnightmunchies.domain.dlv.Courier;
import org.springframework.stereotype.Repository;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Repository
public class CourierRepository {
    private final Map<UUID, Courier> couriers = new ConcurrentHashMap<>();

    public Optional<Courier> findById(UUID id) {
        return Optional.ofNullable(couriers.get(id));
    }

    public Courier save(Courier courier) {
        couriers.put(courier.getId(), courier);
        return courier;
    }

    public List<Courier> findAll() {
        return couriers.values().stream()
                .sorted(Comparator.comparing(Courier::getId))
                .toList();
    }
}
