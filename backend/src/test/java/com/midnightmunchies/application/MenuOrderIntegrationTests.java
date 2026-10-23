package com.midnightmunchies.application;

import com.jayway.jsonpath.JsonPath;
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
import org.springframework.test.web.servlet.ResultActions;

import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class MenuOrderIntegrationTests {
    private static final String MENU = "/api/ord/restaurants/{restaurantId}/menu";
    private static final String ORDERS = "/api/ord/restaurants/{restaurantId}/orders";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AuthService authService;

    @Autowired
    private RestaurantRepository restaurants;

    @Autowired
    private OrderRepository orders;

    private UUID restaurantId;
    private MockHttpSession staffSession;
    private MockHttpSession customerSession;

    @BeforeEach
    void setUp() throws Exception {
        UserAccount staff = authService.register(UUID.randomUUID() + "@example.com", "password", UserRole.RESTAURANT_STAFF);
        UserAccount customer = authService.register(UUID.randomUUID() + "@example.com", "password", UserRole.CUSTOMER);
        staffSession = login(staff.username());
        customerSession = login(customer.username());
        restaurantId = UUID.randomUUID();
        // Restaurant provisioning is fixture setup; menu changes and orders go through HTTP and the Proxy.
        restaurants.save(new Restaurant(restaurantId, UUID.fromString(staff.id()), "Diner", RestaurantStatus.OPEN, 0, 0));
    }

    @Test
    void editsAffectNewOrdersWhilePreviouslyPlacedOrdersKeepAllOriginalDetails() throws Exception {
        UUID itemId = addItem("Poutine", "12.35");
        String originalOrder = placeOrder(itemId, "Poutine", 12.35);

        mockMvc.perform(put(MENU + "/{menuItemId}", restaurantId, itemId).session(staffSession)
                        .contentType(APPLICATION_JSON)
                        .content("{\"name\":\"Loaded Poutine\",\"price\":\"15.00\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(itemId.toString()))
                .andExpect(jsonPath("$.name").value("Loaded Poutine"))
                .andExpect(jsonPath("$.price").value(15.00))
                .andExpect(jsonPath("$.status").value("AVAILABLE"));

        mockMvc.perform(get(MENU, restaurantId).session(customerSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Loaded Poutine"))
                .andExpect(jsonPath("$[0].price").value(15.00));
        assertReadableHistory(customerSession, originalOrder);

        String newOrder = placeOrder(itemId, "Loaded Poutine", 15.00);

        assertReadableHistory(customerSession, originalOrder, newOrder);
        assertReadableHistory(staffSession, originalOrder, newOrder);
    }

    @Test
    void soldOutRejectsNewOrdersAndRestoringAvailabilityMakesTheItemOrderableAgain() throws Exception {
        UUID itemId = addItem("Poutine", "12.35");
        UUID otherItemId = addItem("Fries", "5.49");
        String originalOrder = placeOrder(itemId, "Poutine", 12.35);

        changeStatus(itemId, "SOLD_OUT");
        Set<?> previousOrders = Set.copyOf(orders.findAll());
        assertRejectedOrder("{\"lines\":[{\"menuItemId\":\"%s\",\"quantity\":1}]}".formatted(itemId));
        // A valid first line must not produce a partial order when a later line is unavailable.
        assertRejectedOrder("""
                {"lines":[{"menuItemId":"%s","quantity":1},{"menuItemId":"%s","quantity":1}]}
                """.formatted(otherItemId, itemId));
        assertEquals(previousOrders, Set.copyOf(orders.findAll()));
        assertReadableHistory(customerSession, originalOrder);

        changeStatus(itemId, "AVAILABLE");
        String restoredOrder = placeOrder(itemId, "Poutine", 12.35);

        assertReadableHistory(customerSession, originalOrder, restoredOrder);
    }

    @Test
    void removingAnItemBlocksFutureOrdersAndPreservesReadableCustomerAndStaffHistory() throws Exception {
        UUID itemId = addItem("Poutine", "12.35");
        UUID otherItemId = addItem("Fries", "5.49");
        String originalOrder = placeOrder(itemId, "Poutine", 12.35);

        mockMvc.perform(delete(MENU + "/{menuItemId}", restaurantId, itemId).session(staffSession))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));
        mockMvc.perform(get(MENU, restaurantId).session(customerSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(otherItemId.toString()));

        Set<?> previousOrders = Set.copyOf(orders.findAll());
        assertRejectedOrder("{\"lines\":[{\"menuItemId\":\"%s\",\"quantity\":1}]}".formatted(itemId));
        assertRejectedOrder("""
                {"lines":[{"menuItemId":"%s","quantity":1},{"menuItemId":"%s","quantity":1}]}
                """.formatted(otherItemId, itemId));
        assertEquals(previousOrders, Set.copyOf(orders.findAll()));
        assertReadableHistory(customerSession, originalOrder);
        assertReadableHistory(staffSession, originalOrder);

        // The remaining menu is still usable after removal of a different item.
        String remainingItemOrder = placeOrder(otherItemId, "Fries", 5.49);
        assertReadableHistory(customerSession, originalOrder, remainingItemOrder);
    }

    private UUID addItem(String name, String price) throws Exception {
        String response = mockMvc.perform(post(MENU, restaurantId).session(staffSession)
                        .contentType(APPLICATION_JSON)
                        .content("{\"name\":\"%s\",\"price\":\"%s\"}".formatted(name, price)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.restaurantId").value(restaurantId.toString()))
                .andExpect(jsonPath("$.name").value(name))
                .andExpect(jsonPath("$.status").value("AVAILABLE"))
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(JsonPath.read(response, "$.id"));
    }

    private String placeOrder(UUID itemId, String expectedName, double expectedPrice) throws Exception {
        // Client-supplied names and prices must not override the server's menu snapshots.
        return mockMvc.perform(post(ORDERS, restaurantId).session(customerSession)
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"lines":[{"menuItemId":"%s","quantity":2,"menuItemName":"Spoofed","unitPrice":0}]}
                                """.formatted(itemId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PLACED"))
                .andExpect(jsonPath("$.restaurantId").value(restaurantId.toString()))
                .andExpect(jsonPath("$.orderLines.length()").value(1))
                .andExpect(jsonPath("$.orderLines[0].menuItemId").value(itemId.toString()))
                .andExpect(jsonPath("$.orderLines[0].menuItemName").value(expectedName))
                .andExpect(jsonPath("$.orderLines[0].quantity").value(2))
                .andExpect(jsonPath("$.orderLines[0].unitPrice").value(expectedPrice))
                .andReturn().getResponse().getContentAsString();
    }

    private void changeStatus(UUID itemId, String status) throws Exception {
        mockMvc.perform(patch(MENU + "/{menuItemId}/status", restaurantId, itemId).session(staffSession)
                        .contentType(APPLICATION_JSON).content("{\"status\":\"%s\"}".formatted(status)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(status));
    }

    private void assertRejectedOrder(String body) throws Exception {
        mockMvc.perform(post(ORDERS, restaurantId).session(customerSession).contentType(APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").isNotEmpty());
    }

    private ResultActions assertReadableHistory(MockHttpSession session, String... orderResponses) throws Exception {
        // Compare every original response field, including captured name, price, quantity, ID, status, and timestamp.
        return mockMvc.perform(get("/api/ord/orders").session(session))
                .andExpect(status().isOk())
                .andExpect(content().json("[" + String.join(",", orderResponses) + "]"));
    }

    private MockHttpSession login(String username) throws Exception {
        MockHttpSession session = new MockHttpSession();
        mockMvc.perform(post("/api/auth/login").session(session).contentType(APPLICATION_JSON)
                        .content("{\"username\":\"%s\",\"password\":\"password\"}".formatted(username)))
                .andExpect(status().isOk());
        return session;
    }
}
