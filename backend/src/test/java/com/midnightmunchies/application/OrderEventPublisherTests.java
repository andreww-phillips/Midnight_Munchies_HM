package com.midnightmunchies.application;

import com.midnightmunchies.domain.ord.Actor;
import com.midnightmunchies.domain.ord.OrderEvent;
import com.midnightmunchies.domain.ord.OrderEventListener;
import com.midnightmunchies.domain.ord.OrderStatus;
import com.midnightmunchies.domain.reg.UserRole;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class OrderEventPublisherTests {

    private final OrderEventPublisher publisher = new OrderEventPublisher();

    @Test
    void deliversEachEventToEveryListenerInSubscriptionOrder() {
        List<String> calls = new ArrayList<>();
        publisher.subscribe(event -> calls.add("first:" + event.eventId()));
        publisher.subscribe(event -> calls.add("second:" + event.eventId()));
        OrderEvent event = event();

        publisher.publish(event);

        assertEquals(List.of("first:" + event.eventId(), "second:" + event.eventId()), calls);
    }

    @Test
    void failingListenerDoesNotStopTheOthers() {
        List<OrderEvent> received = new ArrayList<>();
        publisher.subscribe(event -> {
            throw new IllegalStateException("boom");
        });
        publisher.subscribe(received::add);
        OrderEvent event = event();

        assertDoesNotThrow(() -> publisher.publish(event));
        assertEquals(List.of(event), received);
    }

    @Test
    void subscribingTheSameListenerTwiceDeliversOnce() {
        List<OrderEvent> received = new ArrayList<>();
        OrderEventListener listener = received::add;
        publisher.subscribe(listener);
        publisher.subscribe(listener);

        publisher.publish(event());

        assertEquals(1, received.size());
        assertEquals(1, publisher.listeners().size());
    }

    @Test
    void publishingWithNoListenersIsFineAndNullsAreRejected() {
        assertDoesNotThrow(() -> publisher.publish(event()));
        assertThrows(NullPointerException.class, () -> publisher.publish(null));
        assertThrows(NullPointerException.class, () -> publisher.subscribe(null));
    }

    private OrderEvent event() {
        return OrderEvent.of(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), OrderStatus.PLACED,
                OrderStatus.ACCEPTED, Instant.now(), new Actor(UUID.randomUUID(), UserRole.RESTAURANT_STAFF));
    }
}
