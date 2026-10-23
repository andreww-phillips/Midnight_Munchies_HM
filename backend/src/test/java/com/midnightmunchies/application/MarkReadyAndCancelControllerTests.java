package com.midnightmunchies.application;

import com.midnightmunchies.domain.ord.Actor;
import com.midnightmunchies.domain.ord.Order;
import com.midnightmunchies.domain.ord.OrderLine;
import com.midnightmunchies.domain.ord.OrderStatus;
import com.midnightmunchies.domain.ord.Restaurant;
import com.midnightmunchies.domain.ord.RestaurantStatus;
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
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class MarkReadyAndCancelControllerTests {
    private static final String READY = "/api/ord/restaurants/{restaurantId}/orders/{orderId}/ready";
    private static final String CANCEL = "/api/ord/orders/{orderId}/cancel";
    private static final String QUEUE = "/api/ord/restaurants/{restaurantId}/preparation";

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

    private Actor staff;
    private UUID restaurantId;
    private UUID customerId;
    private MockHttpSession ownerSession;
    private MockHttpSession customerSession;
    private MockHttpSession operatorSession;

    @BeforeEach
    void setUp() throws Exception {
        UserAccount owner = register(UserRole.RESTAURANT_STAFF);
        UserAccount customer = register(UserRole.CUSTOMER);
        UserAccount operator = register(UserRole.PLATFORM_OPERATOR);
        ownerSession = login(owner);
        customerSession = login(customer);
        operatorSession = login(operator);
        customerId = UUID.fromString(customer.id());
        restaurantId = UUID.randomUUID();
        staff = new Actor(UUID.fromString(owner.id()), UserRole.RESTAURANT_STAFF);
        restaurants.save(new Restaurant(restaurantId, UUID.fromString(owner.id()), "Diner", RestaurantStatus.OPEN, 0, 0));
    }

    @Test
    void ownerMarksPreparingOrderReady() throws Exception {
        Order order = preparing();

        mockMvc.perform(post(READY, restaurantId, order.getId()).session(ownerSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(order.getId().toString()))
                .andExpect(jsonPath("$.status").value("READY"));
    }

    @Test
    void markingANonPreparingOrderReadyIsAConflict() throws Exception {
        Order accepted = accepted(UUID.randomUUID());

        mockMvc.perform(post(READY, restaurantId, accepted.getId()).session(ownerSession))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Cannot mark ready an order that is ACCEPTED"));
    }

    @Test
    void markReadyPermissionsAndLookupErrors() throws Exception {
        Order order = preparing();
        MockHttpSession otherStaff = login(register(UserRole.RESTAURANT_STAFF));

        mockMvc.perform(post(READY, restaurantId, order.getId()).session(otherStaff)).andExpect(status().isForbidden());
        mockMvc.perform(post(READY, restaurantId, order.getId()).session(customerSession)).andExpect(status().isForbidden());
        mockMvc.perform(post(READY, restaurantId, order.getId())).andExpect(status().isUnauthorized());
        mockMvc.perform(post(READY, restaurantId, UUID.randomUUID()).session(ownerSession)).andExpect(status().isNotFound());
        mockMvc.perform(post(READY, restaurantId, "not-a-uuid").session(ownerSession)).andExpect(status().isBadRequest());
        assertEquals(OrderStatus.PREPARING, orders.findById(order.getId()).orElseThrow().getStatus());
    }

    @Test
    void customerCancelsOwnPlacedOrder() throws Exception {
        Order order = placed(customerId);

        mockMvc.perform(post(CANCEL, order.getId()).session(customerSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
    }

    @Test
    void customerCancellationRules() throws Exception {
        Order ownAccepted = accepted(customerId);
        Order someoneElses = placed(UUID.randomUUID());

        mockMvc.perform(post(CANCEL, ownAccepted.getId()).session(customerSession))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Cannot cancel an order that is ACCEPTED"));
        mockMvc.perform(post(CANCEL, someoneElses.getId()).session(customerSession))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Order does not exist"));
        assertEquals(OrderStatus.PLACED, orders.findById(someoneElses.getId()).orElseThrow().getStatus());
    }

    @Test
    void operatorCancelsAcceptedOrderAndItLeavesTheQueue() throws Exception {
        Order first = accepted(UUID.randomUUID());
        Order second = accepted(UUID.randomUUID());

        mockMvc.perform(post(CANCEL, first.getId()).session(operatorSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
        mockMvc.perform(get(QUEUE, restaurantId).session(ownerSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(second.getId().toString()));
    }

    @Test
    void operatorCannotCancelReadyOrder() throws Exception {
        Order order = preparing();
        mockMvc.perform(post(READY, restaurantId, order.getId()).session(ownerSession)).andExpect(status().isOk());

        mockMvc.perform(post(CANCEL, order.getId()).session(operatorSession))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Cannot cancel an order that is READY"));
    }

    @Test
    void staffCouriersAndAnonymousUsersCannotCancel() throws Exception {
        Order order = placed(customerId);
        MockHttpSession courierSession = login(register(UserRole.COURIER));

        mockMvc.perform(post(CANCEL, order.getId()).session(ownerSession)).andExpect(status().isForbidden());
        mockMvc.perform(post(CANCEL, order.getId()).session(courierSession)).andExpect(status().isForbidden());
        mockMvc.perform(post(CANCEL, order.getId())).andExpect(status().isUnauthorized());
        mockMvc.perform(post(CANCEL, UUID.randomUUID()).session(operatorSession)).andExpect(status().isNotFound());
        assertEquals(OrderStatus.PLACED, orders.findById(order.getId()).orElseThrow().getStatus());
    }

    private Order placed(UUID customer) {
        return orders.save(new Order(UUID.randomUUID(), customer, restaurantId,
                List.of(new OrderLine(UUID.randomUUID(), 1, new BigDecimal("9.00"))), Instant.now()));
    }

    private Order accepted(UUID customer) {
        Order order = placed(customer);
        order.accept();
        transitions.save(order, staff);
        return orders.findById(order.getId()).orElseThrow();
    }

    private Order preparing() {
        Order order = accepted(UUID.randomUUID());
        order.startPreparing();
        return transitions.save(order, staff);
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
