package com.midnightmunchies.application;

import com.midnightmunchies.domain.ord.OrderEvent;
import com.midnightmunchies.domain.ord.OrderEventListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;

@Component
public class OrderEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(OrderEventPublisher.class);

    private final List<OrderEventListener> listeners = new CopyOnWriteArrayList<>();

    public void subscribe(OrderEventListener listener) {
        Objects.requireNonNull(listener, "listener must not be null");
        if (!listeners.contains(listener)) {
            listeners.add(listener);
        }
    }

    public void publish(OrderEvent event) {
        Objects.requireNonNull(event, "event must not be null");
        for (OrderEventListener listener : listeners) {
            try {
                listener.onEvent(event);
            } catch (RuntimeException exception) {
                log.error("Listener {} failed to handle event {}", listener.getClass().getSimpleName(),
                        event.eventId(), exception);
            }
        }
    }

    public List<OrderEventListener> listeners() {
        return List.copyOf(listeners);
    }
}
