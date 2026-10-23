package com.midnightmunchies.application;

import com.midnightmunchies.domain.ord.OrderEvent;
import com.midnightmunchies.domain.ord.OrderEventListener;
import com.midnightmunchies.domain.ord.repository.OrderEventLog;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Objects;

@Component
public class EventIdRecorder implements OrderEventListener {

    private static final Logger log = LoggerFactory.getLogger(EventIdRecorder.class);

    private final OrderEventLog eventLog;

    public EventIdRecorder(OrderEventLog eventLog) {
        this.eventLog = Objects.requireNonNull(eventLog, "eventLog must not be null");
    }

    @Override
    public void onEvent(OrderEvent event) {
        eventLog.append(event);
        log.info("event={} order={} restaurant={} {} -> {} by {} {} at {}",
                event.eventId(), event.orderId(), event.restaurantId(),
                event.fromStatus(), event.toStatus(),
                event.actorRole(), event.actorId(), event.occurredAt());
    }
}
