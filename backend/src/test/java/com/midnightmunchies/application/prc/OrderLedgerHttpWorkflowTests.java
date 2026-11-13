package com.midnightmunchies.application.prc;

import com.jayway.jsonpath.JsonPath;
import com.midnightmunchies.domain.ord.OrderStatus;
import com.midnightmunchies.domain.prc.LedgerEntry;
import com.midnightmunchies.domain.reg.UserRole;
import com.midnightmunchies.technicalservices.LedgerRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Exercises real login, role commands, dispatch, configured pricing, and summary HTTP routes. */
@SpringBootTest(properties = "spring.application.name=issue-51-http-workflow-test")
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class OrderLedgerHttpWorkflowTests {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final String SUMMARY = "/api/prc/orders/{orderId}/summary";
    private static final String PASSWORD = "issue51-test-password";

    @Autowired private MockMvc mvc;
    @Autowired private LedgerRepository ledger;

    @Test
    void happyPathUsesRealSessionsAndPreservesThePurchaseThroughThePricedSummary() throws Exception {
        Scenario scenario = place("12.35", 2);
        SessionUser courier = registerAndLogin(UserRole.COURIER);
        setDuty(courier, true);
        mvc.perform(get(SUMMARY, scenario.orderId()).session(scenario.customer().session()))
                .andExpect(status().isConflict());

        // Once purchased, even editing and removing the menu item must not alter the receipt.
        mvc.perform(put("/api/ord/restaurants/{id}/menu/{itemId}", scenario.restaurantId(), scenario.itemId())
                        .session(scenario.staff().session()).contentType(APPLICATION_JSON)
                        .content(JSON.writeValueAsString(Map.of("name", "Edited meal", "price", "99.99"))))
                .andExpect(status().isOk());
        mvc.perform(delete("/api/ord/restaurants/{id}/menu/{itemId}", scenario.restaurantId(), scenario.itemId())
                        .session(scenario.staff().session())).andExpect(status().isNoContent());
        prepare(scenario);
        completeDelivery(scenario, courier);

        mvc.perform(get(SUMMARY, scenario.orderId()).session(scenario.customer().session()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orderId").value(scenario.orderId()))
                .andExpect(jsonPath("$.customerId").value(scenario.customer().id()))
                .andExpect(jsonPath("$.courierId").value(courier.id()))
                .andExpect(jsonPath("$.status").value("DELIVERED"))
                .andExpect(jsonPath("$.pricingPending").value(false))
                .andExpect(jsonPath("$.totalCharge").value(30.19))
                .andExpect(jsonPath("$.charge.itemSubtotal").value(24.70))
                .andExpect(jsonPath("$.charge.deliveryFee").value(3.99))
                .andExpect(jsonPath("$.charge.serviceFee").value(1.50))
                .andExpect(jsonPath("$.charge.smallOrderSurcharge").value(0.00))
                .andExpect(jsonPath("$.orderLines[0].menuItemName").value("Receipt meal"))
                .andExpect(jsonPath("$.orderLines[0].quantity").value(2))
                .andExpect(jsonPath("$.orderLines[0].unitPrice").value(12.35))
                .andExpect(jsonPath("$.timestamps.DELIVERED").isString());
        String events = mvc.perform(get("/api/ord/orders/{id}/events", scenario.orderId())
                        .session(scenario.customer().session()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        List<String> states = JsonPath.read(events, "$[*].toStatus");
        assertEquals(List.of("ACCEPTED", "PREPARING", "READY", "PICKED_UP", "DELIVERED"), states);
        List<String> eventIds = JsonPath.read(events, "$[*].eventId");
        assertEquals(5, new HashSet<>(eventIds).size());

        LedgerEntry original = record(scenario);
        mvc.perform(post("/api/dlv/orders/{id}/deliver", scenario.orderId()).session(courier.session()))
                .andExpect(status().isNotFound());
        mvc.perform(get(SUMMARY, scenario.orderId()).session(scenario.customer().session()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalCharge").value(30.19));
        assertSame(original, record(scenario));
        assertEquals(6, original.timestamps().size());

        SessionUser otherCustomer = registerAndLogin(UserRole.CUSTOMER);
        mvc.perform(get(SUMMARY, scenario.orderId()).session(otherCustomer.session()))
                .andExpect(status().isNotFound());
        mvc.perform(get(SUMMARY, scenario.orderId()).session(scenario.staff().session()))
                .andExpect(status().isForbidden());
        mvc.perform(get(SUMMARY, scenario.orderId()).session(courier.session()))
                .andExpect(status().isForbidden());
        mvc.perform(get(SUMMARY, scenario.orderId()).session(login("operator@example.com", "password").session()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalCharge").value(30.19));
        setDuty(courier, false);
        mvc.perform(post("/api/auth/logout").session(scenario.customer().session())).andExpect(status().isNoContent());
        mvc.perform(get(SUMMARY, scenario.orderId())).andExpect(status().isUnauthorized());
    }

    @Test
    void noCourierScenarioStaysUnchargedUntilAWaitingOrderIsActuallyDelivered() throws Exception {
        Scenario scenario = place("8.50", 1);
        SessionUser courier = registerAndLogin(UserRole.COURIER);
        mvc.perform(get("/api/dlv/courier").session(courier.session()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.state").value("OFFLINE"))
                .andExpect(jsonPath("$.assignment").value(nullValue()));
        prepare(scenario);

        mvc.perform(get("/api/dlv/operator/deliveries").session(login("operator@example.com", "password").session()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.queued").value(hasItem(scenario.orderId())));
        mvc.perform(get(SUMMARY, scenario.orderId()).session(scenario.customer().session()))
                .andExpect(status().isConflict());
        assertEquals(OrderStatus.READY, record(scenario).status());
        assertNull(record(scenario).totalCharge());
        assertNull(record(scenario).charge());

        setDuty(courier, true);
        completeDelivery(scenario, courier);

        mvc.perform(get(SUMMARY, scenario.orderId()).session(scenario.customer().session()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.pricingPending").value(false))
                .andExpect(jsonPath("$.charge.itemSubtotal").value(8.50))
                .andExpect(jsonPath("$.charge.smallOrderSurcharge").value(2.00))
                .andExpect(jsonPath("$.totalCharge").value(15.99));
        assertEquals(new BigDecimal("15.99"), record(scenario).totalCharge());
        setDuty(courier, false);
    }

    @Test
    void rejectedAndCancelledOrdersRemainInTheLedgerWithoutChargesOrDeliverySummaries() throws Exception {
        Scenario rejected = place("8.50", 1);
        mvc.perform(post("/api/ord/restaurants/{id}/orders/{orderId}/reject", rejected.restaurantId(), rejected.orderId())
                        .session(rejected.staff().session()).contentType(APPLICATION_JSON)
                        .content(JSON.writeValueAsString(Map.of("reason", "Kitchen unavailable"))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("REJECTED"));
        Scenario cancelled = place("8.50", 1);
        mvc.perform(post("/api/ord/orders/{id}/cancel", cancelled.orderId()).session(cancelled.customer().session()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CANCELLED"));

        assertEquals(OrderStatus.REJECTED, record(rejected).status());
        assertEquals(OrderStatus.CANCELLED, record(cancelled).status());
        for (Scenario scenario : List.of(rejected, cancelled)) {
            assertNull(record(scenario).totalCharge());
            assertNull(record(scenario).charge());
            assertNull(record(scenario).courierId());
            mvc.perform(get("/api/ord/orders").session(scenario.customer().session()))
                    .andExpect(status().isOk()).andExpect(jsonPath("$[0].id").value(scenario.orderId()));
            mvc.perform(get(SUMMARY, scenario.orderId()).session(scenario.customer().session()))
                    .andExpect(status().isConflict());
        }
    }

    private Scenario place(String price, int quantity) throws Exception {
        SessionUser staff = registerAndLogin(UserRole.RESTAURANT_STAFF);
        SessionUser customer = registerAndLogin(UserRole.CUSTOMER);
        String catalog = mvc.perform(get("/api/ord/restaurants").session(staff.session()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        List<Map<String, Object>> restaurants = JsonPath.read(catalog, "$");
        String restaurantId = (String) restaurants.stream().filter(row -> staff.id().equals(row.get("ownerId")))
                .findFirst().orElseThrow().get("id");
        String itemJson = mvc.perform(post("/api/ord/restaurants/{id}/menu", restaurantId).session(staff.session())
                        .contentType(APPLICATION_JSON).content(JSON.writeValueAsString(Map.of("name", "Receipt meal", "price", price))))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        String itemId = JsonPath.read(itemJson, "$.id");
        String orderJson = mvc.perform(post("/api/ord/restaurants/{id}/orders", restaurantId).session(customer.session())
                        .contentType(APPLICATION_JSON)
                        .content(JSON.writeValueAsString(Map.of("lines", List.of(Map.of("menuItemId", itemId, "quantity", quantity))))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("PLACED"))
                .andReturn().getResponse().getContentAsString();
        String orderId = JsonPath.read(orderJson, "$.id");
        Scenario scenario = new Scenario(customer, staff, restaurantId, itemId, orderId);
        assertEquals(OrderStatus.PLACED, record(scenario).status());
        assertNull(record(scenario).totalCharge());
        return scenario;
    }

    private void prepare(Scenario scenario) throws Exception {
        mvc.perform(post("/api/ord/restaurants/{id}/orders/{orderId}/accept", scenario.restaurantId(), scenario.orderId())
                .session(scenario.staff().session())).andExpect(status().isOk());
        mvc.perform(post("/api/ord/restaurants/{id}/preparation/next", scenario.restaurantId())
                .session(scenario.staff().session())).andExpect(status().isOk());
        mvc.perform(post("/api/ord/restaurants/{id}/orders/{orderId}/ready", scenario.restaurantId(), scenario.orderId())
                .session(scenario.staff().session())).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("READY"));
    }

    private void completeDelivery(Scenario scenario, SessionUser courier) throws Exception {
        mvc.perform(get("/api/dlv/courier").session(courier.session()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.assignment.order.id").value(scenario.orderId()));
        mvc.perform(post("/api/dlv/orders/{id}/accept", scenario.orderId()).session(courier.session())).andExpect(status().isOk());
        mvc.perform(post("/api/dlv/orders/{id}/pickup", scenario.orderId()).session(courier.session()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.order.status").value("PICKED_UP"));
        assertNull(record(scenario).totalCharge());
        mvc.perform(get(SUMMARY, scenario.orderId()).session(scenario.customer().session())).andExpect(status().isConflict());
        mvc.perform(post("/api/dlv/orders/{id}/deliver", scenario.orderId()).session(courier.session()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.order.status").value("DELIVERED"));
    }

    private void setDuty(SessionUser courier, boolean onDuty) throws Exception {
        mvc.perform(put("/api/dlv/courier/duty").session(courier.session()).contentType(APPLICATION_JSON)
                .content(JSON.writeValueAsString(Map.of("onDuty", onDuty)))).andExpect(status().isOk());
    }

    private SessionUser registerAndLogin(UserRole role) throws Exception {
        String username = "issue51-" + UUID.randomUUID() + "@example.test";
        Map<String, Object> request = new HashMap<>(Map.of("username", username, "password", PASSWORD, "role", role.name()));
        if (role == UserRole.RESTAURANT_STAFF) {
            request.put("restaurantName", "Issue 51 diner " + UUID.randomUUID());
        }
        MockHttpSession registration = new MockHttpSession();
        mvc.perform(post("/api/auth/register").session(registration).contentType(APPLICATION_JSON)
                        .content(JSON.writeValueAsString(request)))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.role").value(role.name()));
        mvc.perform(post("/api/auth/logout").session(registration)).andExpect(status().isNoContent());
        return login(username, PASSWORD);
    }

    private SessionUser login(String username, String password) throws Exception {
        MockHttpSession session = new MockHttpSession();
        String result = mvc.perform(post("/api/auth/login").session(session).contentType(APPLICATION_JSON)
                        .content(JSON.writeValueAsString(Map.of("username", username, "password", password))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return new SessionUser(JsonPath.read(result, "$.id"), session);
    }

    private LedgerEntry record(Scenario scenario) {
        return ledger.findByOrderId(UUID.fromString(scenario.orderId())).orElseThrow();
    }

    private record SessionUser(String id, MockHttpSession session) { }

    private record Scenario(SessionUser customer, SessionUser staff, String restaurantId, String itemId, String orderId) { }
}
