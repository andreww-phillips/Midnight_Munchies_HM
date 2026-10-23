package com.midnightmunchies.application;

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
class OrderAcceptRejectControllerTests {
    private static final String ACCEPT = "/api/ord/restaurants/{restaurantId}/orders/{orderId}/accept";
    private static final String REJECT = "/api/ord/restaurants/{restaurantId}/orders/{orderId}/reject";
    private static final String QUEUE = "/api/ord/restaurants/{restaurantId}/preparation";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AuthService authService;

    @Autowired
    private RestaurantRepository restaurants;

    @Autowired
    private OrderRepository orders;

    private UUID restaurantId;
    private MockHttpSession ownerSession;
    private MockHttpSession customerSession;

    @BeforeEach
    void setUp() throws Exception {
        UserAccount owner = register(UserRole.RESTAURANT_STAFF);
        ownerSession = login(owner);
        customerSession = login(register(UserRole.CUSTOMER));
        restaurantId = UUID.randomUUID();
        restaurants.save(new Restaurant(restaurantId, UUID.fromString(owner.id()), "Diner", RestaurantStatus.OPEN, 0, 0));
    }

    @Test
    void acceptingAnOrderMovesItIntoTheRestaurantsQueue() throws Exception {
        Order order = placed();

        mockMvc.perform(post(ACCEPT, restaurantId, order.getId()).session(ownerSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(order.getId().toString()))
                .andExpect(jsonPath("$.status").value("ACCEPTED"));

        mockMvc.perform(get(QUEUE, restaurantId).session(ownerSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(order.getId().toString()));
    }

    @Test
    void rejectingAnOrderRecordsTheReasonAndKeepsItOutOfTheQueue() throws Exception {
        Order order = placed();

        mockMvc.perform(post(REJECT, restaurantId, order.getId()).session(ownerSession)
                        .contentType(APPLICATION_JSON).content("{\"reason\":\"Out of burgers\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJECTED"))
                .andExpect(jsonPath("$.rejectionReason").value("Out of burgers"));

        mockMvc.perform(get(QUEUE, restaurantId).session(ownerSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
        mockMvc.perform(post(ACCEPT, restaurantId, order.getId()).session(ownerSession))
                .andExpect(status().isConflict());
    }

    @Test
    void rejectionWithoutAReasonOrBodyIsABadRequest() throws Exception {
        Order order = placed();

        mockMvc.perform(post(REJECT, restaurantId, order.getId()).session(ownerSession)
                        .contentType(APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post(REJECT, restaurantId, order.getId()).session(ownerSession)
                        .contentType(APPLICATION_JSON).content("{\"reason\":\"  \"}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post(REJECT, restaurantId, order.getId()).session(ownerSession))
                .andExpect(status().isBadRequest());
        assertEquals(OrderStatus.PLACED, orders.findById(order.getId()).orElseThrow().getStatus());
    }

    @Test
    void ordersThatAreNotPlacedCannotBeAcceptedOrRejected() throws Exception {
        Order order = placed();
        mockMvc.perform(post(ACCEPT, restaurantId, order.getId()).session(ownerSession)).andExpect(status().isOk());

        mockMvc.perform(post(ACCEPT, restaurantId, order.getId()).session(ownerSession))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Cannot accept an order that is ACCEPTED"));
        mockMvc.perform(post(REJECT, restaurantId, order.getId()).session(ownerSession)
                        .contentType(APPLICATION_JSON).content("{\"reason\":\"Too late\"}"))
                .andExpect(status().isConflict());
        assertEquals(OrderStatus.ACCEPTED, orders.findById(order.getId()).orElseThrow().getStatus());
    }

    @Test
    void permissionsAndLookupErrors() throws Exception {
        Order order = placed();
        MockHttpSession otherStaff = login(register(UserRole.RESTAURANT_STAFF));
        String body = "{\"reason\":\"No\"}";

        mockMvc.perform(post(ACCEPT, restaurantId, order.getId()).session(otherStaff)).andExpect(status().isForbidden());
        mockMvc.perform(post(REJECT, restaurantId, order.getId()).session(otherStaff)
                .contentType(APPLICATION_JSON).content(body)).andExpect(status().isForbidden());
        mockMvc.perform(post(ACCEPT, restaurantId, order.getId()).session(customerSession)).andExpect(status().isForbidden());
        mockMvc.perform(post(ACCEPT, restaurantId, order.getId())).andExpect(status().isUnauthorized());
        mockMvc.perform(post(ACCEPT, restaurantId, UUID.randomUUID()).session(ownerSession)).andExpect(status().isNotFound());
        mockMvc.perform(post(ACCEPT, restaurantId, "not-a-uuid").session(ownerSession)).andExpect(status().isBadRequest());
        assertEquals(OrderStatus.PLACED, orders.findById(order.getId()).orElseThrow().getStatus());
    }

    private Order placed() {
        return orders.save(new Order(UUID.randomUUID(), UUID.randomUUID(), restaurantId,
                List.of(new OrderLine(UUID.randomUUID(), 1, new BigDecimal("9.00"))), Instant.now()));
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
