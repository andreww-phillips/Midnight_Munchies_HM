package com.midnightmunchies.application;

import com.midnightmunchies.domain.ord.Actor;
import com.midnightmunchies.domain.ord.Order;
import com.midnightmunchies.domain.ord.OrderEvent;
import com.midnightmunchies.domain.ord.OrderLine;
import com.midnightmunchies.domain.ord.OrderStatus;
import com.midnightmunchies.domain.ord.Restaurant;
import com.midnightmunchies.domain.ord.RestaurantStatus;
import com.midnightmunchies.domain.ord.repository.OrderEventLog;
import com.midnightmunchies.domain.ord.repository.OrderRepository;
import com.midnightmunchies.domain.ord.repository.RestaurantRepository;
import com.midnightmunchies.domain.reg.UserAccount;
import com.midnightmunchies.domain.reg.UserRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class OrderEventsIntegrationTests {
    private static final String EVENTS = "/api/ord/orders/{orderId}/events";
    private static final String NEXT = "/api/ord/restaurants/{restaurantId}/preparation/next";
    private static final String READY = "/api/ord/restaurants/{restaurantId}/orders/{orderId}/ready";
    private static final String CANCEL = "/api/ord/orders/{orderId}/cancel";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AuthService authService;

    @Autowired
    private RestaurantRepository restaurants;

    @Autowired
    private OrderRepository orders;

    @Autowired
    private OrderTransitions transitions;

    @Autowired
    private OrderEventLog eventLog;

    @Autowired
    private PreparationQueue queue;

    private UUID restaurantId;
    private UUID customerId;
    private Actor staff;
    private MockHttpSession ownerSession;
    private MockHttpSession customerSession;
    private MockHttpSession operatorSession;

    @BeforeEach
    void setUp() throws Exception {
        UserAccount owner = register(UserRole.RESTAURANT_STAFF);
        UserAccount customer = register(UserRole.CUSTOMER);
        ownerSession = login(owner);
        customerSession = login(customer);
        operatorSession = login(register(UserRole.PLATFORM_OPERATOR));
        staff = new Actor(UUID.fromString(owner.id()), UserRole.RESTAURANT_STAFF);
        customerId = UUID.fromString(customer.id());
        restaurantId = UUID.randomUUID();
        restaurants.save(new Restaurant(restaurantId, staff.userId(), "Diner", RestaurantStatus.OPEN, 0, 0));
    }

    @Test
    void fullLifecycleLeavesAnOrderedTrailWithUniqueIdsAndActors() throws Exception {
        Order order = acceptedOrder();
        assertEquals(List.of(order.getId()), queue.view(restaurantId).stream().map(Order::getId).toList());

        mockMvc.perform(post(NEXT, restaurantId).session(ownerSession)).andExpect(status().isOk());
        mockMvc.perform(post(READY, restaurantId, order.getId()).session(ownerSession)).andExpect(status().isOk());

        List<OrderEvent> events = eventLog.findByOrderId(order.getId());
        assertEquals(List.of(OrderStatus.PLACED, OrderStatus.ACCEPTED, OrderStatus.PREPARING),
                events.stream().map(OrderEvent::fromStatus).toList());
        assertEquals(List.of(OrderStatus.ACCEPTED, OrderStatus.PREPARING, OrderStatus.READY),
                events.stream().map(OrderEvent::toStatus).toList());
        assertEquals(3, new HashSet<>(events.stream().map(OrderEvent::eventId).toList()).size());
        events.forEach(event -> {
            assertEquals(staff.userId(), event.actorId());
            assertEquals(UserRole.RESTAURANT_STAFF, event.actorRole());
            assertEquals(restaurantId, event.restaurantId());
            assertNotNull(event.occurredAt());
        });
        assertFalse(events.get(1).occurredAt().isBefore(events.get(0).occurredAt()));
        assertFalse(events.get(2).occurredAt().isBefore(events.get(1).occurredAt()));
        assertFalse(orders.findById(order.getId()).orElseThrow().hasPendingTransitions());
    }

    @Test
    void cancellationRecordsWhoCancelled() throws Exception {
        Order byCustomer = placedOrder();
        Order byOperator = acceptedOrder();

        mockMvc.perform(post(CANCEL, byCustomer.getId()).session(customerSession)).andExpect(status().isOk());
        mockMvc.perform(post(CANCEL, byOperator.getId()).session(operatorSession)).andExpect(status().isOk());

        OrderEvent customerCancel = eventLog.findByOrderId(byCustomer.getId()).getLast();
        assertEquals(OrderStatus.PLACED, customerCancel.fromStatus());
        assertEquals(OrderStatus.CANCELLED, customerCancel.toStatus());
        assertEquals(customerId, customerCancel.actorId());
        assertEquals(UserRole.CUSTOMER, customerCancel.actorRole());
        OrderEvent operatorCancel = eventLog.findByOrderId(byOperator.getId()).getLast();
        assertEquals(OrderStatus.ACCEPTED, operatorCancel.fromStatus());
        assertEquals(UserRole.PLATFORM_OPERATOR, operatorCancel.actorRole());
        assertTrue(queue.view(restaurantId).isEmpty());
    }

    @Test
    void failedTransitionsCreateNoEvents() throws Exception {
        Order accepted = acceptedOrder();
        int before = eventLog.findByOrderId(accepted.getId()).size();

        mockMvc.perform(post(READY, restaurantId, accepted.getId()).session(ownerSession)).andExpect(status().isConflict());
        Order placed = placedOrder();
        mockMvc.perform(post(CANCEL, accepted.getId()).session(customerSession)).andExpect(status().isNotFound());
        mockMvc.perform(post(READY, restaurantId, placed.getId()).session(ownerSession)).andExpect(status().isConflict());

        assertEquals(before, eventLog.findByOrderId(accepted.getId()).size());
        assertTrue(eventLog.findByOrderId(placed.getId()).isEmpty());
        assertFalse(orders.findById(accepted.getId()).orElseThrow().hasPendingTransitions());
    }

    @Test
    void eventsEndpointReturnsTheTrailToPermittedUsers() throws Exception {
        Order order = acceptedOrder(customerId);
        String eventId = eventLog.findByOrderId(order.getId()).getFirst().eventId().toString();

        for (MockHttpSession session : List.of(ownerSession, customerSession, operatorSession)) {
            mockMvc.perform(get(EVENTS, order.getId()).session(session))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(1))
                    .andExpect(jsonPath("$[0].eventId").value(eventId))
                    .andExpect(jsonPath("$[0].orderId").value(order.getId().toString()))
                    .andExpect(jsonPath("$[0].fromStatus").value("PLACED"))
                    .andExpect(jsonPath("$[0].toStatus").value("ACCEPTED"))
                    .andExpect(jsonPath("$[0].actorRole").value("RESTAURANT_STAFF"))
                    .andExpect(jsonPath("$[0].occurredAt").exists());
        }
    }

    @Test
    void eventsEndpointHidesOrdersFromOtherUsers() throws Exception {
        Order order = acceptedOrder(UUID.randomUUID());
        MockHttpSession otherStaff = login(register(UserRole.RESTAURANT_STAFF));
        MockHttpSession courier = login(register(UserRole.COURIER));

        mockMvc.perform(get(EVENTS, order.getId()).session(customerSession)).andExpect(status().isNotFound());
        mockMvc.perform(get(EVENTS, order.getId()).session(otherStaff)).andExpect(status().isNotFound());
        mockMvc.perform(get(EVENTS, order.getId()).session(courier)).andExpect(status().isForbidden());
        mockMvc.perform(get(EVENTS, order.getId())).andExpect(status().isUnauthorized());
        mockMvc.perform(get(EVENTS, UUID.randomUUID()).session(operatorSession)).andExpect(status().isNotFound());
    }

    private Order placedOrder() {
        return orders.save(new Order(UUID.randomUUID(), customerId, restaurantId,
                List.of(new OrderLine(UUID.randomUUID(), 1, new BigDecimal("8.00"))), Instant.now()));
    }

    private Order acceptedOrder() {
        return acceptedOrder(UUID.randomUUID());
    }

    private Order acceptedOrder(UUID customer) {
        Order order = orders.save(new Order(UUID.randomUUID(), customer, restaurantId,
                List.of(new OrderLine(UUID.randomUUID(), 1, new BigDecimal("8.00"))), Instant.now()));
        order.accept();
        transitions.save(order, staff);
        return orders.findById(order.getId()).orElseThrow();
    }

    private UserAccount register(UserRole role) {
        return authService.register(UUID.randomUUID() + "@example.com", "password", role);
    }

    private MockHttpSession login(UserAccount account) throws Exception {
        MockHttpSession session = new MockHttpSession();
        mockMvc.perform(post("/api/auth/login").session(session).contentType(APPLICATION_JSON)
                        .content("{\"username\":\"%s\",\"password\":\"password\"}".formatted(account.username())))
                .andExpect(status().isOk());
        return session;
    }
}
