package com.midnightmunchies.application.ord;

import com.midnightmunchies.domain.ord.MenuItem;
import com.midnightmunchies.domain.ord.MenuItemRepository;
import com.midnightmunchies.domain.ord.MenuItemStatus;
import com.midnightmunchies.domain.ord.Order;
import com.midnightmunchies.domain.ord.OrderRepository;
import com.midnightmunchies.domain.ord.Restaurant;
import com.midnightmunchies.domain.ord.RestaurantRepository;
import com.midnightmunchies.domain.ord.RestaurantStatus;
import com.midnightmunchies.domain.reg.UserAccount;
import com.midnightmunchies.domain.reg.UserRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.hasItem;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class RestaurantStatusControllerTests {
    @Autowired private MockMvc mvc;
    @Autowired private RestaurantRepository restaurants;
    @Autowired private MenuItemRepository menuItems;
    @Autowired private OrderRepository orders;

    private UUID restaurantId;
    private UUID ownerId;
    private UUID customerId;
    private UUID itemId;
    private MockHttpSession owner;
    private MockHttpSession operator;
    private MockHttpSession customer;

    @BeforeEach
    void setUp() {
        restaurantId = UUID.randomUUID();
        ownerId = UUID.randomUUID();
        customerId = UUID.randomUUID();
        itemId = UUID.randomUUID();
        restaurants.save(new Restaurant(restaurantId, ownerId, "Status Diner " + restaurantId,
                RestaurantStatus.OPEN, 45.5048, -73.5772, "logo.png", "menu.pdf"));
        menuItems.save(new MenuItem(itemId, restaurantId, "Status meal", BigDecimal.TEN, MenuItemStatus.AVAILABLE));
        owner = session(ownerId, UserRole.RESTAURANT_STAFF);
        operator = session(UUID.randomUUID(), UserRole.PLATFORM_OPERATOR);
        customer = session(customerId, UserRole.CUSTOMER);
    }

    @Test
    void ownerPausesTheirRestaurantWithoutChangingItsOtherDetails() throws Exception {
        mvc.perform(patch(statusPath()).session(owner).contentType(APPLICATION_JSON).content("{\"status\":\"PAUSED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PAUSED"))
                .andExpect(jsonPath("$.id").value(restaurantId.toString()))
                .andExpect(jsonPath("$.ownerId").value(ownerId.toString()))
                .andExpect(jsonPath("$.name").value("Status Diner " + restaurantId))
                .andExpect(jsonPath("$.latitude").value(45.5048))
                .andExpect(jsonPath("$.longitude").value(-73.5772))
                .andExpect(jsonPath("$.logo").value("logo.png"))
                .andExpect(jsonPath("$.menu").value("menu.pdf"));
        assertEquals(RestaurantStatus.PAUSED, current().getStatus());
    }

    @Test
    void operatorPausesAndReopensARestaurantTheyDoNotOwn() throws Exception {
        change(operator, "PAUSED");
        change(operator, "OPEN");
        assertEquals(RestaurantStatus.OPEN, current().getStatus());
    }

    @Test
    void owningStaffCannotReopenEvenTheirOwnRestaurant() throws Exception {
        restaurants.save(current().withStatus(RestaurantStatus.PAUSED));
        mvc.perform(patch(statusPath()).session(owner).contentType(APPLICATION_JSON).content("{\"status\":\"OPEN\"}"))
                .andExpect(status().isForbidden());
        assertEquals(RestaurantStatus.PAUSED, current().getStatus());
    }

    @Test
    void otherStaffCannotChangeTheRestaurant() throws Exception {
        denied(session(UUID.randomUUID(), UserRole.RESTAURANT_STAFF));
    }

    @ParameterizedTest
    @EnumSource(value = UserRole.class, names = {"CUSTOMER", "COURIER"})
    void customersAndCouriersCannotChangeRestaurantStatus(UserRole role) throws Exception {
        denied(session(ownerId, role));
    }

    @Test
    void anonymousCommandsAreUnauthorized() throws Exception {
        mvc.perform(patch(statusPath()).contentType(APPLICATION_JSON).content("{\"status\":\"PAUSED\"}"))
                .andExpect(status().isUnauthorized());
        assertEquals(RestaurantStatus.OPEN, current().getStatus());
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"status\":null}", "{\"status\":\"CLOSED\"}",
            "{\"status\":\"paused\"}", "{\"status\":true}", "{", "null"})
    void invalidRequestsDoNotChangeState(String body) throws Exception {
        mvc.perform(patch(statusPath()).session(operator).contentType(APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest());
        assertEquals(RestaurantStatus.OPEN, current().getStatus());
    }

    @Test
    void missingAndMalformedRestaurantIdsAreBadRequests() throws Exception {
        for (String id : List.of(UUID.randomUUID().toString(), "invalid")) {
            mvc.perform(patch("/api/ord/restaurants/" + id + "/status").session(operator)
                            .contentType(APPLICATION_JSON).content("{\"status\":\"PAUSED\"}"))
                    .andExpect(status().isBadRequest());
        }
    }

    @Test
    void pausedRestaurantFinishesAcceptedOrdersAndMapUsesItsActualQueueAfterReopening() throws Exception {
        List<UUID> accepted = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            Order placed = place();
            accepted.add(placed.getId());
            mvc.perform(post(orderPath(placed.getId()) + "/accept").session(owner)).andExpect(status().isOk());
        }
        mapBand("AMBER");
        change(owner, "PAUSED");
        mapBand("RED");
        assertEquals(accepted, orders.findPreparationQueue(restaurantId).stream().map(Order::getId).toList());
        mvc.perform(post("/api/ord/restaurants/" + restaurantId + "/orders").session(customer)
                        .contentType(APPLICATION_JSON).content(orderBody()))
                .andExpect(status().isBadRequest());

        mvc.perform(post("/api/ord/restaurants/" + restaurantId + "/preparation/next").session(owner))
                .andExpect(status().isOk()).andExpect(jsonPath("$.id").value(accepted.get(0).toString()));
        mvc.perform(post(orderPath(accepted.get(0)) + "/ready").session(owner))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("READY"));
        assertEquals(accepted.subList(1, 3), orders.findPreparationQueue(restaurantId).stream().map(Order::getId).toList());
        mapBand("RED");
        change(operator, "OPEN");
        mapBand("GREEN");
        place();
    }

    private void denied(MockHttpSession viewer) throws Exception {
        for (String target : List.of("OPEN", "PAUSED")) {
            mvc.perform(patch(statusPath()).session(viewer).contentType(APPLICATION_JSON)
                            .content("{\"status\":\"" + target + "\"}"))
                    .andExpect(status().isForbidden());
        }
        assertEquals(RestaurantStatus.OPEN, current().getStatus());
    }

    private void change(MockHttpSession viewer, String target) throws Exception {
        mvc.perform(patch(statusPath()).session(viewer).contentType(APPLICATION_JSON)
                        .content("{\"status\":\"" + target + "\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value(target));
    }

    private void mapBand(String band) throws Exception {
        mvc.perform(get("/api/dash/map").session(operator)).andExpect(status().isOk())
                .andExpect(jsonPath("$.restaurants[?(@.id == '" + restaurantId + "')].band").value(hasItem(band)));
    }

    private Order place() throws Exception {
        String response = mvc.perform(post("/api/ord/restaurants/" + restaurantId + "/orders").session(customer)
                        .contentType(APPLICATION_JSON).content(orderBody()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String id = com.jayway.jsonpath.JsonPath.read(response, "$.id");
        return orders.findById(UUID.fromString(id)).orElseThrow();
    }

    private String orderBody() { return "{\"lines\":[{\"menuItemId\":\"" + itemId + "\",\"quantity\":1}]}"; }
    private String statusPath() { return "/api/ord/restaurants/" + restaurantId + "/status"; }
    private String orderPath(UUID id) { return "/api/ord/restaurants/" + restaurantId + "/orders/" + id; }
    private Restaurant current() { return restaurants.findById(restaurantId).orElseThrow(); }

    private MockHttpSession session(UUID id, UserRole role) {
        MockHttpSession result = new MockHttpSession();
        result.setAttribute("authenticatedUser", new UserAccount(id.toString(), "status@example.test", "hash", role));
        return result;
    }
}
