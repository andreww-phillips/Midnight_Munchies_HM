package com.midnightmunchies.application;

import com.midnightmunchies.domain.ord.Actor;
import com.midnightmunchies.domain.ord.OrderEvent;
import com.midnightmunchies.domain.ord.OrderStatus;
import com.midnightmunchies.domain.reg.UserRole;
import com.midnightmunchies.technicalservices.ord.repository.InMemoryOrderEventLog;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(OutputCaptureExtension.class)
class EventIdRecorderTests {

    private final InMemoryOrderEventLog log = new InMemoryOrderEventLog();
    private final EventIdRecorder recorder = new EventIdRecorder(log);

    @Test
    void storesEventsInArrivalOrderAndByOrder() {
        UUID orderId = UUID.randomUUID();
        OrderEvent first = event(orderId, OrderStatus.PLACED, OrderStatus.ACCEPTED);
        OrderEvent other = event(UUID.randomUUID(), OrderStatus.PLACED, OrderStatus.CANCELLED);
        OrderEvent second = event(orderId, OrderStatus.ACCEPTED, OrderStatus.PREPARING);

        recorder.onEvent(first);
        recorder.onEvent(other);
        recorder.onEvent(second);

        assertEquals(List.of(first, other, second), log.findAll());
        assertEquals(List.of(first, second), log.findByOrderId(orderId));
        assertTrue(log.findByOrderId(UUID.randomUUID()).isEmpty());
    }

    @Test
    void writesOneApplicationLogLinePerEvent(CapturedOutput output) {
        OrderEvent event = event(UUID.randomUUID(), OrderStatus.PREPARING, OrderStatus.READY);

        recorder.onEvent(event);

        assertTrue(output.getOut().contains("event=" + event.eventId()));
        assertTrue(output.getOut().contains("order=" + event.orderId()));
        assertTrue(output.getOut().contains("PREPARING -> READY by RESTAURANT_STAFF " + event.actorId()));
    }

    @Test
    void duplicateEventIdsAreRejectedAndHistoryIsReadOnly() {
        OrderEvent event = event(UUID.randomUUID(), OrderStatus.PLACED, OrderStatus.ACCEPTED);
        recorder.onEvent(event);

        assertThrows(IllegalArgumentException.class, () -> recorder.onEvent(event));
        assertEquals(1, log.findAll().size());
        assertThrows(UnsupportedOperationException.class, () -> log.findAll().clear());
    }

    private OrderEvent event(UUID orderId, OrderStatus from, OrderStatus to) {
        return OrderEvent.of(UUID.randomUUID(), orderId, UUID.randomUUID(), from, to, Instant.now(),
                new Actor(UUID.randomUUID(), UserRole.RESTAURANT_STAFF));
    }
}
