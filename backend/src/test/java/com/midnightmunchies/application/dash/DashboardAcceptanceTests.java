package com.midnightmunchies.application.dash;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.util.List;

import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Uses only authenticated HTTP commands and public read views across REG, ORD, DLV, DASH and PRC. */
@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class DashboardAcceptanceTests {
    private static final String DINER = "10000000-0000-0000-0000-000000000001";
    private static final String MEAL = "20000000-0000-0000-0000-000000000001";
    private static final String RESTAURANT = "/api/ord/restaurants/" + DINER;
    @Autowired private MockMvc mvc;

    @Test
    void authorizedWorkflowEnforcesStateGuardsAndCanBeRepeatedAfterReset() throws Exception {
        MockHttpSession operator = login("operator");
        reset(operator);
        MockHttpSession customer = login("customer");
        MockHttpSession staff = login("restaurant");
        MockHttpSession courier = login("courier");

        mvc.perform(get("/api/dash/map")).andExpect(status().isUnauthorized());
        for (MockHttpSession denied : List.of(staff, courier, operator)) {
            place(denied).andExpect(status().isForbidden());
        }
        for (MockHttpSession denied : List.of(customer, courier)) {
            restaurantStatus(denied, "PAUSED").andExpect(status().isForbidden());
        }
        restaurantStatus(staff, "PAUSED").andExpect(status().isOk());
        place(customer).andExpect(status().isBadRequest());
        restaurantStatus(staff, "OPEN").andExpect(status().isForbidden());
        restaurantStatus(operator, "OPEN").andExpect(status().isOk());

        availability(staff, "SOLD_OUT");
        place(customer).andExpect(status().isBadRequest());
        availability(staff, "AVAILABLE");
        mvc.perform(get("/api/ord/orders").session(operator)).andExpect(jsonPath("$.length()").value(0));

        String cancelled = order(customer);
        mvc.perform(post("/api/ord/orders/" + cancelled + "/cancel").session(customer))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CANCELLED"));
        mvc.perform(put("/api/dlv/courier/duty").session(courier).contentType(APPLICATION_JSON)
                .content("{\"onDuty\":true}")).andExpect(status().isOk());
        String delivered = order(customer);
        for (MockHttpSession denied : List.of(customer, courier, operator)) {
            mvc.perform(post(orderPath(delivered) + "/accept").session(denied)).andExpect(status().isForbidden());
        }
        mvc.perform(post("/api/dlv/orders/" + delivered + "/pickup").session(courier))
                .andExpect(status().isNotFound());
        mvc.perform(post(orderPath(delivered) + "/accept").session(staff)).andExpect(status().isOk());
        mvc.perform(post("/api/ord/orders/" + delivered + "/cancel").session(customer))
                .andExpect(status().isConflict());
        mvc.perform(post(RESTAURANT + "/preparation/next").session(staff)).andExpect(status().isOk());
        mvc.perform(post(orderPath(delivered) + "/ready").session(staff)).andExpect(status().isOk());
        mvc.perform(get("/api/dlv/courier").session(courier)).andExpect(status().isOk())
                .andExpect(jsonPath("$.assignment.order.id").value(delivered))
                .andExpect(jsonPath("$.assignment.order.status").value("READY"));
        mvc.perform(post("/api/dlv/orders/" + delivered + "/pickup").session(courier))
                .andExpect(status().isConflict());
        handoff(courier, delivered, "accept");
        mvc.perform(post("/api/dlv/orders/" + delivered + "/deliver").session(courier))
                .andExpect(status().isConflict());
        handoff(courier, delivered, "pickup");
        handoff(courier, delivered, "deliver");
        mvc.perform(get("/api/prc/orders/" + delivered + "/summary").session(customer))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalCharge").value(20.48));
        mvc.perform(post("/api/prc/orders/" + delivered + "/refund").session(customer))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/prc/orders/" + delivered + "/refund").session(operator))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("REFUNDED"));
        for (MockHttpSession denied : List.of(customer, staff, courier)) {
            mvc.perform(post("/api/dash/reset").session(denied)).andExpect(status().isForbidden());
        }
        mvc.perform(get("/api/prc/history").session(operator)).andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));

        reset(operator);
        for (String path : List.of("/api/ord/orders", "/api/prc/history")) {
            mvc.perform(get(path).session(operator)).andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(0));
        }
        mvc.perform(get("/api/dlv/operator/deliveries").session(operator)).andExpect(status().isOk())
                .andExpect(jsonPath("$.active.length()").value(0)).andExpect(jsonPath("$.queued.length()").value(0));
        mvc.perform(get("/api/dash/map").session(operator)).andExpect(status().isOk())
                .andExpect(jsonPath("$.restaurants.length()").value(2))
                .andExpect(jsonPath("$.couriers.length()").value(0))
                .andExpect(jsonPath("$.awaitingCourier.length()").value(0));
        mvc.perform(get("/api/dlv/courier").session(courier)).andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("OFFLINE"));
        // Seeded sessions and event subscriptions continue working after reset.
        String next = order(customer);
        mvc.perform(post(orderPath(next) + "/accept").session(staff)).andExpect(status().isOk());
        mvc.perform(get(RESTAURANT + "/preparation").session(staff)).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(next));
        reset(operator);
    }

    private MockHttpSession login(String role) throws Exception {
        MockHttpSession session = new MockHttpSession();
        mvc.perform(post("/api/auth/login").session(session).contentType(APPLICATION_JSON)
                .content("{\"username\":\"" + role + "@example.com\",\"password\":\"password\"}"))
                .andExpect(status().isOk());
        return session;
    }

    private void reset(MockHttpSession operator) throws Exception {
        mvc.perform(post("/api/dash/reset").session(operator)).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RESET"));
    }

    private ResultActions restaurantStatus(MockHttpSession session, String state) throws Exception {
        return mvc.perform(patch(RESTAURANT + "/status").session(session).contentType(APPLICATION_JSON)
                .content("{\"status\":\"" + state + "\"}"));
    }

    private void availability(MockHttpSession staff, String state) throws Exception {
        mvc.perform(patch(RESTAURANT + "/menu/" + MEAL + "/status").session(staff).contentType(APPLICATION_JSON)
                .content("{\"status\":\"" + state + "\"}")).andExpect(status().isOk());
    }

    private ResultActions place(MockHttpSession customer) throws Exception {
        return mvc.perform(post(RESTAURANT + "/orders").session(customer).contentType(APPLICATION_JSON)
                .content("{\"lines\":[{\"menuItemId\":\"" + MEAL + "\",\"quantity\":1}]}"));
    }

    private String order(MockHttpSession customer) throws Exception {
        String response = place(customer).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("PLACED"))
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(response, "$.id");
    }

    private String orderPath(String id) { return RESTAURANT + "/orders/" + id; }

    private void handoff(MockHttpSession courier, String id, String action) throws Exception {
        mvc.perform(post("/api/dlv/orders/" + id + "/" + action).session(courier)).andExpect(status().isOk());
    }
}
