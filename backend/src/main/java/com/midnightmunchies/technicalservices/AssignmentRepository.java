package com.midnightmunchies.technicalservices;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Repository;

import com.midnightmunchies.domain.dlv.Assignment;

@Repository
public class AssignmentRepository {
    private final Map<UUID, Assignment> assignments = new ConcurrentHashMap<>();

    public Assignment save(Assignment assignment) {
        assignments.put(assignment.id(), assignment);
        return assignment;
    }

    public Optional<Assignment> findById(UUID id) {
        return Optional.ofNullable(assignments.get(id));
    }

    public List<Assignment> findActive() {
        return assignments.values().stream()
                .filter(Assignment::isActive)
                .sorted(Comparator.comparing(Assignment::offeredAt))
                .toList();
    }

    public Optional<Assignment> findActiveByOrderId(UUID orderId) {
        return assignments.values().stream()
                .filter(Assignment::isActive)
                .filter(assignment -> assignment.orderId().equals(orderId))
                .findFirst();
    }

    public Optional<Assignment> findActiveByCourierId(UUID courierId) {
        return assignments.values().stream()
                .filter(Assignment::isActive)
                .filter(assignment -> assignment.courierId().equals(courierId))
                .findFirst();
    }

    public List<Assignment> findByCourierId(UUID courierId) {
        return assignments.values().stream()
                .filter(assignment -> assignment.courierId().equals(courierId))
                .sorted(Comparator.comparing(Assignment::offeredAt))
                .toList();
    }
}
