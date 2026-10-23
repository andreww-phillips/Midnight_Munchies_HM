package com.midnightmunchies.application;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Exercises REG, the authorization Proxy, ORD, FIFO, and Observer wiring through HTTP. */
@SpringBootTest
@AutoConfigureMockMvc
class RestaurantWorkflowIntegrationTests {
    private static final String RESTAURANT = "/api/ord/restaurants/{restaurantId}";
    private static final String ORDER = RESTAURANT + "/orders/{orderId}";
    @Autowired private MockMvc mvc;

    @Test
    void customerPlacementToReadyIsVisibleToBothRolesAndRecordsEverySuccessfulTransition() throws Exception {
        Account staff = registerAndLogin("RESTAURANT_STAFF");
        Account customer = registerAndLogin("CUSTOMER");
        String itemId = addItem(staff);
        String orderId = place(customer, staff.id(), itemId);

        mvc.perform(get("/api/ord/orders").session(staff.session())).andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(orderId))
                .andExpect(jsonPath("$[0].status").value("PLACED"))
                .andExpect(jsonPath("$[0].orderLines[0].menuItemName").value("Workflow Fries"));
        mvc.perform(post(ORDER + "/ready", staff.id(), orderId).session(staff.session()))
                .andExpect(status().isConflict());
        assertEquals(List.of(), eventValues(staff, orderId, "eventId"));

        mvc.perform(post(ORDER + "/accept", staff.id(), orderId).session(staff.session()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("ACCEPTED"))
                .andExpect(jsonPath("$.acceptedAt").isNotEmpty());
        assertEquals(List.of(orderId), queueIds(staff));
        mvc.perform(post(RESTAURANT + "/preparation/next", staff.id()).session(staff.session()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.id").value(orderId))
                .andExpect(jsonPath("$.status").value("PREPARING"));
        assertEquals(List.of(), queueIds(staff));
        mvc.perform(post(ORDER + "/ready", staff.id(), orderId).session(staff.session()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("READY"));

        for (Account viewer : List.of(staff, customer)) {
            mvc.perform(get("/api/ord/orders").session(viewer.session())).andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].id").value(orderId))
                    .andExpect(jsonPath("$[0].status").value("READY"))
                    .andExpect(jsonPath("$[0].orderLines[0].quantity").value(2))
                    .andExpect(jsonPath("$[0].orderLines[0].unitPrice").value(5.49));
            assertEquals(List.of("PLACED", "ACCEPTED", "PREPARING"), eventValues(viewer, orderId, "fromStatus"));
            assertEquals(List.of("ACCEPTED", "PREPARING", "READY"), eventValues(viewer, orderId, "toStatus"));
        }
        List<String> eventIds = eventValues(staff, orderId, "eventId");
        assertEquals(3, new HashSet<>(eventIds).size());
        eventIds.forEach(UUID::fromString);
        eventValues(staff, orderId, "occurredAt").forEach(Instant::parse);
        assertEquals(List.of(staff.id(), staff.id(), staff.id()), eventValues(staff, orderId, "actorId"));
        assertEquals(List.of("RESTAURANT_STAFF", "RESTAURANT_STAFF", "RESTAURANT_STAFF"), eventValues(staff, orderId, "actorRole"));
        mvc.perform(post(ORDER + "/ready", staff.id(), orderId).session(staff.session()))
                .andExpect(status().isConflict());
        assertEquals(eventIds, eventValues(staff, orderId, "eventId"));
    }

    @Test
    void rejectionAndReverseAcceptancePreserveFifoAndAllowMoreThanOnePreparingOrder() throws Exception {
        Account staff = registerAndLogin("RESTAURANT_STAFF");
        Account customer = registerAndLogin("CUSTOMER");
        String itemId = addItem(staff);
        String placedFirst = place(customer, staff.id(), itemId);
        String placedSecond = place(customer, staff.id(), itemId);
        String rejected = place(customer, staff.id(), itemId);

        mvc.perform(post(ORDER + "/reject", staff.id(), rejected).session(staff.session())
                        .contentType(APPLICATION_JSON).content("{\"reason\":\"   \"}"))
                .andExpect(status().isBadRequest());
        assertEquals(List.of(), eventValues(staff, rejected, "eventId"));
        mvc.perform(post(ORDER + "/reject", staff.id(), rejected).session(staff.session())
                        .contentType(APPLICATION_JSON).content("{\"reason\":\"  Kitchen closed  \"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("REJECTED"))
                .andExpect(jsonPath("$.rejectionReason").value("Kitchen closed"));
        mvc.perform(post(ORDER + "/accept", staff.id(), rejected).session(staff.session()))
                .andExpect(status().isConflict());
        assertEquals(List.of("REJECTED"), eventValues(customer, rejected, "toStatus"));

        for (String id : List.of(placedSecond, placedFirst)) {
            mvc.perform(post(ORDER + "/accept", staff.id(), id).session(staff.session())).andExpect(status().isOk());
        }
        assertEquals(List.of(placedSecond, placedFirst), queueIds(staff));
        mvc.perform(post(RESTAURANT + "/preparation/next", staff.id()).session(staff.session()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.id").value(placedSecond));
        assertEquals(List.of(placedFirst), queueIds(staff));
        mvc.perform(post(RESTAURANT + "/preparation/next", staff.id()).session(staff.session()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.id").value(placedFirst))
                .andExpect(jsonPath("$.status").value("PREPARING"));
        assertEquals(List.of(), queueIds(staff));
        mvc.perform(post(RESTAURANT + "/preparation/next", staff.id()).session(staff.session()))
                .andExpect(status().isConflict());
        assertEquals(List.of("ACCEPTED", "PREPARING"), eventValues(staff, placedFirst, "toStatus"));
        assertEquals(List.of("ACCEPTED", "PREPARING"), eventValues(staff, placedSecond, "toStatus"));
    }

    @Test
    void realSessionsEnforceRestaurantOwnershipAndRejectCommandsWithoutChangingOrdersOrEvents() throws Exception {
        Account staff = registerAndLogin("RESTAURANT_STAFF");
        Account otherStaff = registerAndLogin("RESTAURANT_STAFF");
        Account customer = registerAndLogin("CUSTOMER");
        Account otherCustomer = registerAndLogin("CUSTOMER");
        String orderId = place(customer, staff.id(), addItem(staff));
        String foreignOrder = place(customer, otherStaff.id(), addItem(otherStaff));

        mvc.perform(post(ORDER + "/accept", staff.id(), orderId)).andExpect(status().isUnauthorized());
        mvc.perform(post(ORDER + "/accept", staff.id(), orderId).session(customer.session()))
                .andExpect(status().isForbidden());
        for (String command : List.of("accept", "ready", "reject")) {
            String body = command.equals("reject") ? "{\"reason\":\"Not mine\"}" : "{}";
            mvc.perform(post(ORDER + "/" + command, staff.id(), orderId).session(otherStaff.session())
                            .contentType(APPLICATION_JSON).content(body)).andExpect(status().isForbidden());
            mvc.perform(post(ORDER + "/" + command, staff.id(), foreignOrder).session(staff.session())
                            .contentType(APPLICATION_JSON).content(body)).andExpect(status().isNotFound());
        }
        mvc.perform(get(RESTAURANT + "/preparation", staff.id()).session(otherStaff.session()))
                .andExpect(status().isForbidden());
        mvc.perform(post(RESTAURANT + "/preparation/next", staff.id()).session(otherStaff.session()))
                .andExpect(status().isForbidden());
        for (Account viewer : List.of(staff, otherStaff)) {
            String visibleId = viewer == staff ? orderId : foreignOrder;
            mvc.perform(get("/api/ord/orders").session(viewer.session())).andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(1)).andExpect(jsonPath("$[0].id").value(visibleId))
                    .andExpect(jsonPath("$[0].status").value("PLACED"));
        }
        mvc.perform(get("/api/ord/orders").session(otherCustomer.session())).andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
        for (Account denied : List.of(otherStaff, otherCustomer)) {
            mvc.perform(get("/api/ord/orders/{id}/events", orderId).session(denied.session()))
                    .andExpect(status().isNotFound());
        }
        assertEquals(List.of(), queueIds(staff));
        assertEquals(List.of(), eventValues(staff, orderId, "eventId"));
        assertEquals(List.of(), eventValues(otherStaff, foreignOrder, "eventId"));
    }

    private Account registerAndLogin(String role) throws Exception {
        String username = UUID.randomUUID() + "@example.com";
        MockHttpSession registration = new MockHttpSession();
        String response = mvc.perform(post("/api/auth/register").session(registration).contentType(APPLICATION_JSON)
                        .content("{\"username\":\"%s\",\"password\":\"password\",\"role\":\"%s\",\"restaurantName\":\"Workflow Diner\"}".formatted(username, role)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        String id = JsonPath.read(response, "$.id");
        mvc.perform(post("/api/auth/logout").session(registration)).andExpect(status().isNoContent());
        MockHttpSession session = new MockHttpSession();
        mvc.perform(post("/api/auth/login").session(session).contentType(APPLICATION_JSON)
                        .content("{\"username\":\"%s\",\"password\":\"password\"}".formatted(username)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.id").value(id));
        return new Account(id, session);
    }

    private String addItem(Account staff) throws Exception {
        String response = mvc.perform(post(RESTAURANT + "/menu", staff.id()).session(staff.session())
                        .contentType(APPLICATION_JSON).content("{\"name\":\"Workflow Fries\",\"price\":\"5.49\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(response, "$.id");
    }

    private String place(Account customer, String restaurantId, String itemId) throws Exception {
        String response = mvc.perform(post(RESTAURANT + "/orders", restaurantId).session(customer.session())
                        .contentType(APPLICATION_JSON).content("{\"lines\":[{\"menuItemId\":\"%s\",\"quantity\":2}]}".formatted(itemId)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("PLACED"))
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(response, "$.id");
    }

    private List<String> queueIds(Account staff) throws Exception {
        String response = mvc.perform(get(RESTAURANT + "/preparation", staff.id()).session(staff.session()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(response, "$[*].id");
    }

    private List<String> eventValues(Account viewer, String orderId, String field) throws Exception {
        String response = mvc.perform(get("/api/ord/orders/{id}/events", orderId).session(viewer.session()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(response, "$[*]." + field);
    }

    private record Account(String id, MockHttpSession session) {}
}
