package com.midnightmunchies.application.ord;

import com.midnightmunchies.domain.ord.OrderEventListener;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class OrderEventSubscriptions {

    public OrderEventSubscriptions(OrderEventPublisher publisher, List<OrderEventListener> listeners) {
        listeners.forEach(publisher::subscribe);
    }
}
