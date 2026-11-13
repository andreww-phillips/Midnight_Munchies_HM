package com.midnightmunchies.application.ord;

import com.midnightmunchies.application.reg.AuthService;
import com.midnightmunchies.domain.ord.MenuItemRepository;
import com.midnightmunchies.domain.ord.OrderEventLog;
import com.midnightmunchies.domain.ord.OrderRepository;
import com.midnightmunchies.domain.ord.RestaurantRepository;
import com.midnightmunchies.domain.ord.RestaurantStatus;
import com.midnightmunchies.domain.reg.UserRole;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class OperatorControllerTests {

    private static final UUID BOBS_BURGERS = UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final String MIDNIGHT_BURGER = "20000000-0000-0000-0000-000000000001";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private AuthService authService;
    @Autowired
    private RestaurantRepository restaurants;
    @Autowired
    private MenuItemRepository menuItems;
    @Autowired
    private OrderRepository orders;
    @Autowired
    private OrderEventLog events;

    @Test
    void operatorResetRestoresTheSeededScenario() throws Exception {
        mockMvc.perform(post("/api/ord/restaurants/" + BOBS_BURGERS + "/orders")
                        .session(login("customer@example.com"))
                        .contentType("application/json")
                        .content("{\"lines\":[{\"menuItemId\":\"" + MIDNIGHT_BURGER + "\",\"quantity\":1}]}"))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/ord/restaurants/" + BOBS_BURGERS + "/pause").session(login("operator@example.com")))
                .andExpect(status().isOk());
        authService.register("newstaff@example.com", "password", UserRole.RESTAURANT_STAFF, "New Diner", null, null);
        assertNotNull(authService.authenticate("newstaff@example.com", "password"));

        mockMvc.perform(post("/api/operator/reset").session(login("operator@example.com")))
                .andExpect(status().isNoContent());

        assertEquals(0, orders.findAll().size());
        assertEquals(0, events.findAll().size());
        assertEquals(2, restaurants.findAll().size());
        assertEquals(RestaurantStatus.OPEN, restaurants.findById(BOBS_BURGERS).orElseThrow().getStatus());
        assertEquals(4, menuItems.findAll().size());
        assertNull(authService.authenticate("newstaff@example.com", "password"));
        assertNotNull(authService.authenticate("customer@example.com", "password"));
        assertNotNull(authService.authenticate("restaurant@example.com", "password"));
    }

    @Test
    void onlyTheOperatorCanReset() throws Exception {
        mockMvc.perform(post("/api/operator/reset")).andExpect(status().isUnauthorized());
        for (String username : new String[] {"customer@example.com", "restaurant@example.com", "courier@example.com"}) {
            mockMvc.perform(post("/api/operator/reset").session(login(username)))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.message").value("Only the operator can reset the simulation"));
        }
    }

    private MockHttpSession login(String username) {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("authenticatedUser", authService.authenticate(username, "password"));
        return session;
    }
}
