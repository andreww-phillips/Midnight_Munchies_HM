package com.midnightmunchies.application.ord;

import com.jayway.jsonpath.JsonPath;
import com.midnightmunchies.application.dlv.CourierService;
import com.midnightmunchies.application.dlv.DispatchQueueService;
import com.midnightmunchies.application.reg.AuthService;
import com.midnightmunchies.domain.dlv.CourierState;
import com.midnightmunchies.domain.dlv.Location;
import com.midnightmunchies.domain.ord.*;
import com.midnightmunchies.domain.reg.UserAccount;
import com.midnightmunchies.technicalservices.AssignmentRepository;
import com.midnightmunchies.technicalservices.CourierRepository;
import com.midnightmunchies.technicalservices.LedgerRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Exercises the coordinated HTTP reset across all modules and then reuses the running application. */
@SpringBootTest(properties = {"logging.level.org.springframework=ERROR", "logging.level.root=ERROR"})
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class ModuleResetIntegrationTests {
    private static final UUID DINER = UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final UUID MEAL = UUID.fromString("20000000-0000-0000-0000-000000000001");
    @Autowired private MockMvc mvc;
    @Autowired private CourierService delivery;
    @Autowired private AuthService auth;
    @Autowired private RestaurantRepository restaurants;
    @Autowired private MenuItemRepository menuItems;
    @Autowired private OrderRepository orders;
    @Autowired private OrderEventLog events;
    @Autowired private LedgerRepository ledger;
    @Autowired private AssignmentRepository assignments;
    @Autowired private CourierRepository couriers;
    @Autowired private DispatchQueueService dispatch;

    @BeforeEach
    void startFromSeed() throws Exception {
        resetModules();
    }

    @Test
    void restoresAllModuleStateAndPreservesSeededIdentitiesPositionsAndEventSubscriptions() throws Exception {
        MockHttpSession customer = login("customer");
        MockHttpSession staff = login("restaurant");
        MockHttpSession operator = login("operator");
        MockHttpSession courier = login("courier");
        UserAccount courierAccount = account(courier);
        List<UserAccount> seededAccounts = List.of(account(customer), account(staff), account(operator), courierAccount);
        delivery.view(courierAccount);
        Location startingPosition = couriers.findById(UUID.fromString(courierAccount.id())).orElseThrow().getLocation();
        MockHttpSession runtimeCustomer = register("CUSTOMER");
        MockHttpSession runtimeStaff = register("RESTAURANT_STAFF");
        String runtimeUsername = account(runtimeCustomer).username();
        UUID extraRestaurant = UUID.fromString(account(runtimeStaff).id());
        assertTrue(restaurants.findById(extraRestaurant).isPresent());

        mvc.perform(put("/api/dlv/courier/duty").session(courier).contentType(APPLICATION_JSON)
                .content("{\"onDuty\":true}")).andExpect(status().isOk());
        String delivered = ready(customer, staff);
        handoff(courier, delivered, "accept");
        handoff(courier, delivered, "pickup");
        handoff(courier, delivered, "deliver");
        mvc.perform(post("/api/prc/orders/" + delivered + "/refund").session(operator)).andExpect(status().isOk());
        String pickedUp = ready(customer, staff);
        handoff(courier, pickedUp, "accept");
        handoff(courier, pickedUp, "pickup");
        String queuedDelivery = ready(customer, staff);
        String queuedPreparation = place(customer);
        accept(staff, queuedPreparation);
        UUID oldAssignment = assignments.findActiveByOrderId(UUID.fromString(pickedUp)).orElseThrow().id();
        assertEquals(CourierState.CARRYING, delivery.view(courierAccount).state());
        assertEquals(List.of(UUID.fromString(queuedDelivery)), dispatch.view());
        assertEquals(1, orders.findPreparationQueue(DINER).size());
        assertEquals(1, ledger.findAllRefunds().size());
        assertNotNull(ledger.findByOrderId(UUID.fromString(delivered)).orElseThrow().charge());
        assertFalse(events.findAll().isEmpty());

        mvc.perform(patch("/api/ord/restaurants/" + DINER + "/status").session(staff)
                .contentType(APPLICATION_JSON).content("{\"status\":\"PAUSED\"}")).andExpect(status().isOk());
        mvc.perform(delete("/api/ord/restaurants/" + DINER + "/menu/" + MEAL).session(staff))
                .andExpect(status().isNoContent());
        mvc.perform(post("/api/ord/restaurants/" + DINER + "/menu").session(staff)
                .contentType(APPLICATION_JSON).content("{\"name\":\"Temporary meal\",\"price\":99}"))
                .andExpect(status().isCreated());

        resetModules();
        assertInitialState();
        for (String id : List.of(delivered, pickedUp, queuedDelivery, queuedPreparation)) {
            assertTrue(orders.findById(UUID.fromString(id)).isEmpty());
        }
        assertTrue(assignments.findById(oldAssignment).isEmpty());
        assertTrue(assignments.findByCourierId(UUID.fromString(courierAccount.id())).isEmpty());
        assertTrue(couriers.findAll().isEmpty());
        assertTrue(restaurants.findById(extraRestaurant).isEmpty());
        assertNull(auth.authenticate(runtimeUsername, "password"));
        assertTrue(runtimeCustomer.isInvalid());
        assertTrue(runtimeStaff.isInvalid());
        mvc.perform(get("/api/ord/restaurants").session(runtimeStaff)).andExpect(status().isUnauthorized());
        for (UserAccount seed : seededAccounts) {
            assertEquals(seed, auth.authenticate(seed.username(), "password"));
        }
        mvc.perform(get("/api/auth/me").session(operator)).andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(account(operator).id()));
        assertEquals(CourierState.OFFLINE, delivery.view(courierAccount).state());
        assertEquals(startingPosition, couriers.findById(UUID.fromString(courierAccount.id())).orElseThrow().getLocation());
        mvc.perform(get("/api/dash/map").session(operator)).andExpect(status().isOk())
                .andExpect(jsonPath("$.restaurants.length()").value(2))
                .andExpect(jsonPath("$.couriers.length()").value(0))
                .andExpect(jsonPath("$.awaitingCourier.length()").value(0));

        // Reuse the original services and sessions: subscribers must still enqueue and project new orders.
        String newOrder = place(customer);
        accept(staff, newOrder);
        assertNotNull(orders.findById(UUID.fromString(newOrder)).orElseThrow().getAcceptedAt());
        mvc.perform(post("/api/ord/restaurants/" + DINER + "/preparation/next").session(staff)).andExpect(status().isOk());
        mvc.perform(post(orderPath(newOrder) + "/ready").session(staff)).andExpect(status().isOk());
        assertEquals(List.of(UUID.fromString(newOrder)), dispatch.view());
        assertEquals(OrderStatus.READY, ledger.findByOrderId(UUID.fromString(newOrder)).orElseThrow().status());
        assertFalse(events.findByOrderId(UUID.fromString(newOrder)).isEmpty());
        resetModules();
        resetModules();
        assertInitialState();
        assertEquals(courierAccount, auth.authenticate("courier@example.com", "password"));
    }

    private void resetModules() throws Exception {
        mvc.perform(post("/api/dash/reset").session(login("operator")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("RESET"));
    }

    private void assertInitialState() {
        assertEquals(2, restaurants.findAll().size());
        assertTrue(restaurants.findAll().stream().allMatch(restaurant -> restaurant.getStatus() == RestaurantStatus.OPEN));
        assertEquals(45.5048, restaurants.findById(DINER).orElseThrow().getLatitude());
        assertEquals(-73.5772, restaurants.findById(DINER).orElseThrow().getLongitude());
        assertEquals(4, menuItems.findAll().size());
        assertEquals("Midnight Burger", menuItems.findById(MEAL).orElseThrow().getName());
        assertEquals(new BigDecimal("12.99"), menuItems.findById(MEAL).orElseThrow().getPrice());
        assertTrue(menuItems.findAll().stream().allMatch(item -> item.getStatus() == MenuItemStatus.AVAILABLE));
        assertTrue(orders.findAll().isEmpty());
        assertTrue(orders.findPreparationQueue(DINER).isEmpty());
        assertTrue(events.findAll().isEmpty());
        assertTrue(ledger.findAll().isEmpty());
        assertTrue(ledger.findAllRefunds().isEmpty());
        assertTrue(assignments.findActive().isEmpty());
        assertTrue(dispatch.view().isEmpty());
        assertTrue(dispatch.restoreOrders().isEmpty());
    }

    private MockHttpSession login(String role) throws Exception {
        MockHttpSession session = new MockHttpSession();
        mvc.perform(post("/api/auth/login").session(session).contentType(APPLICATION_JSON)
                .content("{\"username\":\"" + role + "@example.com\",\"password\":\"password\"}"))
                .andExpect(status().isOk());
        return session;
    }

    private MockHttpSession register(String role) throws Exception {
        MockHttpSession session = new MockHttpSession();
        String name = "reset-" + UUID.randomUUID();
        mvc.perform(post("/api/auth/register").session(session).contentType(APPLICATION_JSON)
                .content("{\"username\":\"" + name + "@example.test\",\"password\":\"password\",\"role\":\""
                        + role + "\",\"restaurantName\":\"" + name + "\"}"))
                .andExpect(status().isCreated());
        return session;
    }

    private String place(MockHttpSession customer) throws Exception {
        String response = mvc.perform(post("/api/ord/restaurants/" + DINER + "/orders").session(customer)
                .contentType(APPLICATION_JSON).content("{\"lines\":[{\"menuItemId\":\"" + MEAL + "\",\"quantity\":1}]}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(response, "$.id");
    }

    private void accept(MockHttpSession staff, String id) throws Exception {
        mvc.perform(post(orderPath(id) + "/accept").session(staff)).andExpect(status().isOk());
    }

    private String ready(MockHttpSession customer, MockHttpSession staff) throws Exception {
        String id = place(customer);
        accept(staff, id);
        mvc.perform(post("/api/ord/restaurants/" + DINER + "/preparation/next").session(staff))
                .andExpect(status().isOk()).andExpect(jsonPath("$.id").value(id));
        mvc.perform(post(orderPath(id) + "/ready").session(staff)).andExpect(status().isOk());
        return id;
    }

    private void handoff(MockHttpSession courier, String id, String action) throws Exception {
        mvc.perform(post("/api/dlv/orders/" + id + "/" + action).session(courier)).andExpect(status().isOk());
    }

    private String orderPath(String id) { return "/api/ord/restaurants/" + DINER + "/orders/" + id; }
    private UserAccount account(MockHttpSession session) { return (UserAccount) session.getAttribute("authenticatedUser"); }
}
