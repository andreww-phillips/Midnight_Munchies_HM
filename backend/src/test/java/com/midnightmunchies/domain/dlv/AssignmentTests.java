package com.midnightmunchies.domain.dlv;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class AssignmentTests {

    private static final Instant T0 = Instant.parse("2026-11-12T22:00:00Z");
    private static final Instant T1 = Instant.parse("2026-11-12T22:05:00Z");
    private static final Instant T2 = Instant.parse("2026-11-12T22:10:00Z");

    @Test
    void operatorCanReassignAnOfferedAssignmentAndIsRecorded() {
        Assignment offered = offered();

        Assignment reassigned = offered.reassign("operator-1", T1);

        assertEquals(AssignmentStatus.REASSIGNED, reassigned.status());
        assertEquals("operator-1", reassigned.releasedBy());
        assertEquals(T1, reassigned.releasedAt());
        assertEquals(offered.id(), reassigned.id());
        assertFalse(reassigned.isActive());
    }

    @Test
    void operatorCanReassignAnAcceptedAssignmentAndKeepsAcceptedAt() {
        Assignment accepted = offered().accept(T1);

        Assignment reassigned = accepted.reassign("operator-1", T2);

        assertEquals(AssignmentStatus.REASSIGNED, reassigned.status());
        assertEquals(T1, reassigned.acceptedAt());
        assertEquals(T2, reassigned.releasedAt());
    }

    @Test
    void pickedUpOrDeliveredAssignmentCannotBeReassigned() {
        Assignment pickedUp = offered().accept(T1).pickUp(T2);
        Assignment delivered = pickedUp.deliver(T2);

        assertThrows(IllegalStateException.class, () -> pickedUp.reassign("operator-1", T2));
        assertThrows(IllegalStateException.class, () -> delivered.reassign("operator-1", T2));
    }

    @Test
    void reassignRequiresOperatorAndTime() {
        Assignment offered = offered();

        assertThrows(IllegalArgumentException.class, () -> offered.reassign(" ", T1));
        assertThrows(IllegalArgumentException.class, () -> offered.reassign(null, T1));
        assertThrows(NullPointerException.class, () -> offered.reassign("operator-1", null));
    }

    @Test
    void courierDeclineIsRecordedSeparatelyFromReassignment() {
        Assignment offered = offered();

        Assignment declined = offered.decline(T1);

        assertEquals(AssignmentStatus.DECLINED, declined.status());
        assertEquals(offered.courierId().toString(), declined.releasedBy());
        assertEquals(T1, declined.releasedAt());
        assertFalse(declined.isActive());
        assertThrows(IllegalStateException.class, () -> offered.accept(T1).decline(T2));
    }

    private Assignment offered() {
        return Assignment.offer(UUID.randomUUID(), UUID.randomUUID(), T0);
    }
}
