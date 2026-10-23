package com.midnightmunchies.application;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class InMemoryRegistrationMenuTests {
    @Autowired private MockMvc mvc;
    private static final String MENU = "/api/ord/restaurants/{id}/menu";

    @Test
    void registeringStaffCreatesAnOwnedRestaurantAndAllowsRepeatedItemsWithOnlyNameAndPrice() throws Exception {
        MockHttpSession staff = new MockHttpSession();
        String restaurantId = register(staff, "RESTAURANT_STAFF", "Memory Diner");
        String restaurants = mvc.perform(get("/api/ord/restaurants").session(staff))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        List<String> owners = JsonPath.read(restaurants, "$[?(@.id == '" + restaurantId + "')].ownerId");
        assertEquals(List.of(restaurantId), owners);

        String drinkId = add(staff, restaurantId, "Drink", "3.99");
        String secondDrinkId = add(staff, restaurantId, "Drink", "3.99");
        add(staff, restaurantId, "French Fries", "5.99");
        assertNotEquals(drinkId, secondDrinkId);
        mvc.perform(get(MENU, restaurantId).session(staff)).andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3));

        MockHttpSession otherStaff = new MockHttpSession();
        String otherRestaurantId = register(otherStaff, "RESTAURANT_STAFF", "Other Memory Diner");
        add(otherStaff, otherRestaurantId, "Drink", "3.99");
        mvc.perform(put(MENU + "/{item}", restaurantId, drinkId).session(otherStaff)
                        .contentType(APPLICATION_JSON).content("{\"name\":\"Stolen\",\"price\":\"0\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void registeredStaffCanEditToggleAndRemoveItemsWhileExistingOrdersKeepTheirDetails() throws Exception {
        MockHttpSession staff = new MockHttpSession();
        String restaurantId = register(staff, "RESTAURANT_STAFF", "Order Diner");
        String itemId = add(staff, restaurantId, "Drink", "3.99");
        MockHttpSession customer = new MockHttpSession();
        register(customer, "CUSTOMER", null);
        String originalOrder = place(customer, restaurantId, itemId, "Drink", 3.99);

        mvc.perform(put(MENU + "/{item}", restaurantId, itemId).session(staff).contentType(APPLICATION_JSON)
                        .content("{\"name\":\"Large Drink\",\"price\":\"5.99\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.name").value("Large Drink"));
        mvc.perform(patch(MENU + "/{item}/status", restaurantId, itemId).session(staff).contentType(APPLICATION_JSON)
                        .content("{\"status\":\"SOLD_OUT\"}"))
                .andExpect(status().isOk());
        mvc.perform(post("/api/ord/restaurants/{id}/orders", restaurantId).session(customer).contentType(APPLICATION_JSON)
                        .content(orderBody(itemId)))
                .andExpect(status().isBadRequest());
        mvc.perform(patch(MENU + "/{item}/status", restaurantId, itemId).session(staff).contentType(APPLICATION_JSON)
                        .content("{\"status\":\"AVAILABLE\"}"))
                .andExpect(status().isOk());
        String updatedOrder = place(customer, restaurantId, itemId, "Large Drink", 5.99);
        mvc.perform(delete(MENU + "/{item}", restaurantId, itemId).session(staff)).andExpect(status().isNoContent());
        mvc.perform(get(MENU, restaurantId).session(customer)).andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
        mvc.perform(post("/api/ord/restaurants/{id}/orders", restaurantId).session(customer).contentType(APPLICATION_JSON)
                        .content(orderBody(itemId)))
                .andExpect(status().isBadRequest());
        for (MockHttpSession session : List.of(customer, staff)) {
            mvc.perform(get("/api/ord/orders").session(session)).andExpect(status().isOk())
                    .andExpect(content().json("[" + originalOrder + "," + updatedOrder + "]"));
        }
    }

    private String register(MockHttpSession session, String role, String restaurantName) throws Exception {
        String details = restaurantName == null ? "" : ",\"restaurantName\":\"" + restaurantName + "\"";
        String response = mvc.perform(post("/api/auth/register").session(session).contentType(APPLICATION_JSON)
                        .content("{\"username\":\"%s\",\"password\":\"password\",\"role\":\"%s\"%s}"
                                .formatted(UUID.randomUUID() + "@example.com", role, details)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(response, "$.id");
    }

    private String add(MockHttpSession staff, String restaurantId, String name, String price) throws Exception {
        String response = mvc.perform(post(MENU, restaurantId).session(staff).contentType(APPLICATION_JSON)
                        .content("{\"name\":\"%s\",\"price\":\"%s\"}".formatted(name, price)))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.image").isEmpty())
                .andExpect(jsonPath("$.description").isEmpty()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(response, "$.id");
    }

    private String place(MockHttpSession customer, String restaurantId, String itemId, String name, double price) throws Exception {
        return mvc.perform(post("/api/ord/restaurants/{id}/orders", restaurantId).session(customer)
                        .contentType(APPLICATION_JSON).content(orderBody(itemId)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.orderLines[0].menuItemName").value(name))
                .andExpect(jsonPath("$.orderLines[0].unitPrice").value(price))
                .andReturn().getResponse().getContentAsString();
    }

    private String orderBody(String itemId) {
        return "{\"lines\":[{\"menuItemId\":\"%s\",\"quantity\":2}]}".formatted(itemId);
    }
}
