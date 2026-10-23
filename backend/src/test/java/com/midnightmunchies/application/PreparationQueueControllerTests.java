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
class PreparationQueueControllerTests {
    private static final String QUEUE = "/api/ord/restaurants/{restaurantId}/preparation";
    private static final String NEXT = QUEUE + "/next";

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
    private MockHttpSession ownerSession;

    @BeforeEach
    void setUp() throws Exception {
        UserAccount owner = authService.register(UUID.randomUUID() + "@example.com", "password", UserRole.RESTAURANT_STAFF);
        ownerSession = login(owner.username());
        restaurantId = UUID.randomUUID();
        staff = new Actor(UUID.fromString(owner.id()), UserRole.RESTAURANT_STAFF);
        restaurants.save(new Restaurant(restaurantId, UUID.fromString(owner.id()), "Diner", RestaurantStatus.OPEN, 0, 0));
    }

    @Test
    void ownerViewsQueueOldestFirstAndStartsTheNextOrder() throws Exception {
        Order first = enqueue();
        Order second = enqueue();

        mockMvc.perform(get(QUEUE, restaurantId).session(ownerSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].id").value(first.getId().toString()))
                .andExpect(jsonPath("$[1].id").value(second.getId().toString()));

        mockMvc.perform(post(NEXT, restaurantId).session(ownerSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(first.getId().toString()))
                .andExpect(jsonPath("$.status").value("PREPARING"));

        mockMvc.perform(get(QUEUE, restaurantId).session(ownerSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(second.getId().toString()));
    }

    @Test
    void emptyQueueReturnsConflict() throws Exception {
        mockMvc.perform(post(NEXT, restaurantId).session(ownerSession))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("No accepted orders are waiting to be prepared"));
    }

    @Test
    void otherStaffAreForbiddenAndQueueIsUnchanged() throws Exception {
        Order waiting = enqueue();
        UserAccount other = authService.register(UUID.randomUUID() + "@example.com", "password", UserRole.RESTAURANT_STAFF);
        MockHttpSession otherSession = login(other.username());

        mockMvc.perform(post(NEXT, restaurantId).session(otherSession)).andExpect(status().isForbidden());
        mockMvc.perform(get(QUEUE, restaurantId).session(otherSession)).andExpect(status().isForbidden());
        assertEquals(OrderStatus.ACCEPTED, orders.findById(waiting.getId()).orElseThrow().getStatus());
    }

    @Test
    void customersAreForbidden() throws Exception {
        enqueue();
        UserAccount customer = authService.register(UUID.randomUUID() + "@example.com", "password", UserRole.CUSTOMER);
        MockHttpSession customerSession = login(customer.username());

        mockMvc.perform(post(NEXT, restaurantId).session(customerSession)).andExpect(status().isForbidden());
    }

    @Test
    void unauthenticatedRequestsAreUnauthorized() throws Exception {
        mockMvc.perform(post(NEXT, restaurantId)).andExpect(status().isUnauthorized());
        mockMvc.perform(get(QUEUE, restaurantId)).andExpect(status().isUnauthorized());
    }

    @Test
    void unknownRestaurantAndInvalidIdAreBadRequests() throws Exception {
        mockMvc.perform(post(NEXT, UUID.randomUUID()).session(ownerSession)).andExpect(status().isBadRequest());
        mockMvc.perform(post(NEXT, "not-a-uuid").session(ownerSession)).andExpect(status().isBadRequest());
    }

    private Order enqueue() {
        Order order = new Order(UUID.randomUUID(), UUID.randomUUID(), restaurantId,
                List.of(new OrderLine(UUID.randomUUID(), 1, new BigDecimal("8.00"))), Instant.now());
        order.accept();
        transitions.save(order, staff);
        return orders.findById(order.getId()).orElseThrow();
    }

    private MockHttpSession login(String username) throws Exception {
        MockHttpSession session = new MockHttpSession();
        mockMvc.perform(post("/api/auth/login").session(session).contentType(APPLICATION_JSON)
                        .content("{\"username\":\"%s\",\"password\":\"password\"}".formatted(username)))
                .andExpect(status().isOk());
        return session;
    }
}
