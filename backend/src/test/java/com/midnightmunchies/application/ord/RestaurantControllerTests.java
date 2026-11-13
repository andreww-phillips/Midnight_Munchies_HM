package com.midnightmunchies.application.ord;

import com.midnightmunchies.application.reg.AuthService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class RestaurantControllerTests {

    private static final String BOBS_BURGERS = "10000000-0000-0000-0000-000000000001";
    private static final String PIZZA_PALACE = "10000000-0000-0000-0000-000000000002";
    private static final String MIDNIGHT_BURGER = "20000000-0000-0000-0000-000000000001";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AuthService authService;

    @Test
    void staffPauseTheirRestaurantWhichTurnsRedAndStopsTakingOrders() throws Exception {
        mockMvc.perform(post(path(BOBS_BURGERS, "pause")).session(login("restaurant@example.com")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PAUSED"));

        mockMvc.perform(get("/api/dash/map").session(login("customer@example.com")))
                .andExpect(jsonPath("$.restaurants[?(@.name == 'Bobs Burgers')].band").value(hasItem("RED")));
        mockMvc.perform(post("/api/ord/restaurants/" + BOBS_BURGERS + "/orders")
                        .session(login("customer@example.com"))
                        .contentType("application/json")
                        .content("{\"lines\":[{\"menuItemId\":\"" + MIDNIGHT_BURGER + "\",\"quantity\":1}]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Restaurant is not open"));
    }

    @Test
    void staffAndOperatorCanReopen() throws Exception {
        MockHttpSession staff = login("restaurant@example.com");
        mockMvc.perform(post(path(BOBS_BURGERS, "pause")).session(staff)).andExpect(status().isOk());
        mockMvc.perform(post(path(BOBS_BURGERS, "reopen")).session(staff))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("OPEN"));

        MockHttpSession operator = login("operator@example.com");
        mockMvc.perform(post(path(PIZZA_PALACE, "pause")).session(operator)).andExpect(status().isOk());
        mockMvc.perform(post(path(PIZZA_PALACE, "reopen")).session(operator))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("OPEN"));
    }

    @Test
    void forbiddenForOtherRolesAndOtherStaff() throws Exception {
        mockMvc.perform(post(path(BOBS_BURGERS, "pause")).session(login("customer@example.com")))
                .andExpect(status().isForbidden());
        mockMvc.perform(post(path(BOBS_BURGERS, "pause")).session(login("courier@example.com")))
                .andExpect(status().isForbidden());
        mockMvc.perform(post(path(PIZZA_PALACE, "pause")).session(login("restaurant@example.com")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Staff can only pause or reopen their own restaurant"));
    }

    @Test
    void invalidRequestsAreRejected() throws Exception {
        MockHttpSession operator = login("operator@example.com");
        mockMvc.perform(post(path(BOBS_BURGERS, "pause"))).andExpect(status().isUnauthorized());
        mockMvc.perform(post(path(UUID.randomUUID().toString(), "pause")).session(operator))
                .andExpect(status().isNotFound());
        mockMvc.perform(post(path("not-a-uuid", "pause")).session(operator)).andExpect(status().isBadRequest());
        mockMvc.perform(post(path(BOBS_BURGERS, "reopen")).session(operator))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("The restaurant is already open"));
    }

    private MockHttpSession login(String username) {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("authenticatedUser", authService.authenticate(username, "password"));
        return session;
    }

    private static String path(String restaurantId, String action) {
        return "/api/ord/restaurants/" + restaurantId + "/" + action;
    }
}
