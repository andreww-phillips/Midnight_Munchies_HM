package com.midnightmunchies.application;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class OrdControllerPlacementTests {
    private static final String BOBS_BURGERS = "10000000-0000-0000-0000-000000000001";
    private static final String MIDNIGHT_BURGER = "20000000-0000-0000-0000-000000000001";
    private static final String PEPPERONI_PIZZA = "20000000-0000-0000-0000-000000000003";

    @Autowired
    private MockMvc mockMvc;

    @Test
    void anonymousPlacementReturnsUnauthorized() throws Exception {
        mockMvc.perform(post("/api/ord/restaurants/" + BOBS_BURGERS + "/orders")
                        .contentType("application/json")
                        .content(body(MIDNIGHT_BURGER, 1)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void nonCustomerPlacementReturnsForbidden() throws Exception {
        MockHttpSession session = login("restaurant@example.com");

        mockMvc.perform(post("/api/ord/restaurants/" + BOBS_BURGERS + "/orders")
                        .session(session)
                        .contentType("application/json")
                        .content(body(MIDNIGHT_BURGER, 1)))
                .andExpect(status().isForbidden());
    }

    @Test
    void customerPlacementReturnsPlacedOrderWithId() throws Exception {
        MockHttpSession session = login("customer@example.com");

        mockMvc.perform(post("/api/ord/restaurants/" + BOBS_BURGERS + "/orders")
                        .session(session)
                        .contentType("application/json")
                        .content(body(MIDNIGHT_BURGER, 2)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.status").value("PLACED"))
                .andExpect(jsonPath("$.restaurantId").value(BOBS_BURGERS));
    }

    @Test
    void invalidQuantityAndItemFromAnotherRestaurantReturnBadRequest() throws Exception {
        MockHttpSession session = login("customer@example.com");

        mockMvc.perform(post("/api/ord/restaurants/" + BOBS_BURGERS + "/orders")
                        .session(session)
                        .contentType("application/json")
                        .content(body(MIDNIGHT_BURGER, 0)))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/ord/restaurants/" + BOBS_BURGERS + "/orders")
                        .session(session)
                        .contentType("application/json")
                        .content(body(PEPPERONI_PIZZA, 1)))
                .andExpect(status().isBadRequest());
    }

    private MockHttpSession login(String username) throws Exception {
        MockHttpSession session = new MockHttpSession();
        mockMvc.perform(post("/api/auth/login")
                        .session(session)
                        .contentType("application/json")
                        .content("{\"username\":\"" + username + "\",\"password\":\"password\"}"))
                .andExpect(status().isOk());
        return session;
    }

    private String body(String menuItemId, int quantity) {
        return "{\"lines\":[{\"menuItemId\":\"" + menuItemId + "\",\"quantity\":" + quantity + "}]}";
    }
}
