package com.midnightmunchies.application;

import com.midnightmunchies.domain.ord.MenuItem;
import com.midnightmunchies.domain.ord.MenuItemStatus;
import com.midnightmunchies.domain.ord.Restaurant;
import com.midnightmunchies.domain.ord.RestaurantStatus;
import com.midnightmunchies.domain.ord.repository.MenuItemRepository;
import com.midnightmunchies.domain.ord.repository.RestaurantRepository;
import com.midnightmunchies.domain.reg.UserAccount;
import com.midnightmunchies.domain.reg.UserRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class OrdControllerTests {
    private static final String MENU = "/api/ord/restaurants/{restaurantId}/menu";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AuthService authService;

    @Autowired
    private RestaurantRepository restaurants;

    @Autowired
    private MenuItemRepository menuItems;

    private UUID restaurantId;
    private UUID otherRestaurantId;
    private UUID ownerId;
    private MenuItem original;
    private MenuItem otherItem;
    private MockHttpSession ownerSession;

    @BeforeEach
    void setUp() throws Exception {
        UserAccount owner = authService.register(UUID.randomUUID() + "@example.com", "password", UserRole.RESTAURANT_STAFF);
        ownerId = UUID.fromString(owner.id());
        ownerSession = login(owner.username());
        restaurantId = UUID.randomUUID();
        otherRestaurantId = UUID.randomUUID();
        restaurants.save(new Restaurant(restaurantId, ownerId, "Diner", RestaurantStatus.OPEN, 0, 0));
        restaurants.save(new Restaurant(otherRestaurantId, UUID.randomUUID(), "Other Diner", RestaurantStatus.OPEN, 0, 0));
        original = menuItems.save(new MenuItem(UUID.randomUUID(), restaurantId, "Poutine",
                new BigDecimal("12.35"), MenuItemStatus.AVAILABLE));
        otherItem = menuItems.save(new MenuItem(UUID.randomUUID(), otherRestaurantId, "Pizza",
                new BigDecimal("16.99"), MenuItemStatus.AVAILABLE));
    }

    @Test
    void ownerCanAddAnAvailableItemWithServerControlledIdentifiers() throws Exception {
        ResultActions response = mockMvc.perform(post(MENU, restaurantId).session(ownerSession)
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"name":"Fries","price":0,"id":"%s","restaurantId":"%s",
                                 "userId":"%s","role":"CUSTOMER","status":"SOLD_OUT"}
                                """.formatted(original.getId(), otherRestaurantId, UUID.randomUUID())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Fries"))
                .andExpect(jsonPath("$.price").value(0))
                .andExpect(jsonPath("$.restaurantId").value(restaurantId.toString()))
                .andExpect(jsonPath("$.status").value("AVAILABLE"));

        List<MenuItem> currentMenu = menuItems.findByRestaurantId(restaurantId);
        assertEquals(2, currentMenu.size());
        MenuItem added = currentMenu.stream().filter(item -> item.getName().equals("Fries")).findFirst().orElseThrow();
        assertNotEquals(original.getId(), added.getId());
        response.andExpect(jsonPath("$.id").value(added.getId().toString()));
        assertSame(original, menuItems.findById(original.getId()).orElseThrow());
        assertEquals(List.of(otherItem), menuItems.findByRestaurantId(otherRestaurantId));
    }

    @Test
    void ownerCanEditNameAndPriceWhileIdentifiersAndStatusRemainServerControlled() throws Exception {
        menuItems.save(new MenuItem(original.getId(), restaurantId, original.getName(), original.getPrice(), MenuItemStatus.SOLD_OUT));

        mockMvc.perform(put(MENU + "/{menuItemId}", restaurantId, original.getId()).session(ownerSession)
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"name":"Loaded Poutine","price":14.50,"id":"%s",
                                 "restaurantId":"%s","status":"AVAILABLE"}
                                """.formatted(otherItem.getId(), otherRestaurantId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(original.getId().toString()))
                .andExpect(jsonPath("$.restaurantId").value(restaurantId.toString()))
                .andExpect(jsonPath("$.name").value("Loaded Poutine"))
                .andExpect(jsonPath("$.price").value(14.50))
                .andExpect(jsonPath("$.status").value("SOLD_OUT"));

        assertEquals("Loaded Poutine", menuItems.findById(original.getId()).orElseThrow().getName());
        assertSame(otherItem, menuItems.findById(otherItem.getId()).orElseThrow());
    }

    @Test
    void ownerCanRemoveItemAndItDisappearsFromTheMenu() throws Exception {
        mockMvc.perform(delete(MENU + "/{menuItemId}", restaurantId, original.getId()).session(ownerSession))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));

        mockMvc.perform(get(MENU, restaurantId).session(ownerSession))
                .andExpect(status().isOk())
                .andExpect(content().json("[]"));
        assertTrue(menuItems.findById(original.getId()).isEmpty());
        assertSame(otherItem, menuItems.findById(otherItem.getId()).orElseThrow());
    }

    @ParameterizedTest
    @EnumSource(MenuItemStatus.class)
    void ownerCanChangeStatusInBothDirectionsWithoutEditingOtherFields(MenuItemStatus targetStatus) throws Exception {
        MenuItemStatus previousStatus = targetStatus == MenuItemStatus.AVAILABLE
                ? MenuItemStatus.SOLD_OUT : MenuItemStatus.AVAILABLE;
        menuItems.save(new MenuItem(original.getId(), restaurantId, original.getName(), original.getPrice(), previousStatus));

        mockMvc.perform(patch(MENU + "/{menuItemId}/status", restaurantId, original.getId()).session(ownerSession)
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"status":"%s","id":"%s","restaurantId":"%s","name":"Ignored","price":0}
                                """.formatted(targetStatus, otherItem.getId(), otherRestaurantId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(original.getId().toString()))
                .andExpect(jsonPath("$.restaurantId").value(restaurantId.toString()))
                .andExpect(jsonPath("$.name").value("Poutine"))
                .andExpect(jsonPath("$.price").value(12.35))
                .andExpect(jsonPath("$.status").value(targetStatus.name()));

        assertEquals(targetStatus, menuItems.findById(original.getId()).orElseThrow().getStatus());
    }

    @ParameterizedTest
    @EnumSource(MenuOperation.class)
    void unauthenticatedMutationsReturnUnauthorized(MenuOperation operation) throws Exception {
        assertRejectedWithoutChanges(menuRequest(operation, restaurantId, original.getId()), 401);
    }

    @ParameterizedTest
    @EnumSource(MenuOperation.class)
    void staffCannotMutateAnotherRestaurantsMenu(MenuOperation operation) throws Exception {
        assertRejectedWithoutChanges(menuRequest(operation, otherRestaurantId, otherItem.getId()).session(ownerSession), 403);
    }

    @ParameterizedTest
    @EnumSource(value = UserRole.class, names = "RESTAURANT_STAFF", mode = EnumSource.Mode.EXCLUDE)
    void otherRolesCannotGainAccessBySupplyingOwnerIdentityAndRole(UserRole role) throws Exception {
        UserAccount account = authService.register(UUID.randomUUID() + "@example.com", "password", role);
        MockHttpSession session = login(account.username());
        // Matching ownership alone must not grant a non-staff account access.
        restaurants.save(new Restaurant(restaurantId, UUID.fromString(account.id()), "Diner", RestaurantStatus.OPEN, 0, 0));

        for (MenuOperation operation : MenuOperation.values()) {
            String body = operation == MenuOperation.STATUS
                    ? "{\"status\":\"SOLD_OUT\",\"userId\":\"%s\",\"role\":\"RESTAURANT_STAFF\"}"
                    : "{\"name\":\"Changed\",\"price\":1,\"userId\":\"%s\",\"role\":\"RESTAURANT_STAFF\"}";
            assertRejectedWithoutChanges(menuRequest(operation, restaurantId, original.getId()).session(session)
                    .param("userId", ownerId.toString()).param("role", "RESTAURANT_STAFF")
                    .contentType(APPLICATION_JSON).content(body.formatted(ownerId)), 403);
        }
    }

    @ParameterizedTest
    @EnumSource(value = MenuOperation.class, names = {"EDIT", "REMOVE", "STATUS"})
    void nonexistentMenuItemsReturnNotFound(MenuOperation operation) throws Exception {
        assertRejectedWithoutChanges(menuRequest(operation, restaurantId, UUID.randomUUID()).session(ownerSession), 404);
    }

    @ParameterizedTest
    @EnumSource(value = MenuOperation.class, names = {"EDIT", "REMOVE", "STATUS"})
    void itemsFromAnotherMenuReturnNotFoundEvenIfStaffOwnsBothRestaurants(MenuOperation operation) throws Exception {
        restaurants.save(new Restaurant(otherRestaurantId, ownerId, "Other Diner", RestaurantStatus.OPEN, 0, 0));

        assertRejectedWithoutChanges(menuRequest(operation, restaurantId, otherItem.getId()).session(ownerSession), 404);
    }

    @ParameterizedTest
    @EnumSource(MenuOperation.class)
    void nonexistentRestaurantsFollowExistingBadRequestConvention(MenuOperation operation) throws Exception {
        assertRejectedWithoutChanges(menuRequest(operation, UUID.randomUUID(), original.getId()).session(ownerSession), 400);
    }

    @ParameterizedTest
    @EnumSource(MenuOperation.class)
    void malformedRestaurantIdentifiersReturnBadRequest(MenuOperation operation) throws Exception {
        assertRejectedWithoutChanges(menuRequest(operation, "invalid-uuid", original.getId()).session(ownerSession), 400);
    }

    @ParameterizedTest
    @EnumSource(value = MenuOperation.class, names = {"EDIT", "REMOVE", "STATUS"})
    void malformedItemIdentifiersReturnBadRequest(MenuOperation operation) throws Exception {
        assertRejectedWithoutChanges(menuRequest(operation, restaurantId, "invalid-uuid").session(ownerSession), 400);
    }

    @ParameterizedTest
    @MethodSource("invalidItemRequests")
    void invalidItemInputsReturnBadRequestWithoutMutatingTheMenu(MenuOperation operation, String body) throws Exception {
        assertRejectedWithoutChanges(menuRequest(operation, restaurantId, original.getId()).session(ownerSession)
                .content(body), 400);
    }

    static Stream<Arguments> invalidItemRequests() {
        return Stream.of(MenuOperation.ADD, MenuOperation.EDIT).flatMap(operation -> Stream.of(
                "{}",
                "{\"name\":\"Poutine\"}",
                "{\"name\":null,\"price\":1}",
                "{\"name\":\"\",\"price\":1}",
                "{\"name\":\" \\t\\n\",\"price\":1}",
                "{\"name\":\"Poutine\",\"price\":null}",
                "{\"name\":\"Poutine\",\"price\":-0.01}",
                "{\"name\":\"Poutine\",\"price\":\"free\"}",
                "{\"name\":\"Poutine\",\"price\":{}}",
                "null", "[]", "{", ""
        ).map(body -> Arguments.of(operation, body)));
    }

    @ParameterizedTest
    @MethodSource("invalidStatusRequests")
    void invalidStatusInputsReturnBadRequestWithoutMutatingTheMenu(String body) throws Exception {
        assertRejectedWithoutChanges(menuRequest(MenuOperation.STATUS, restaurantId, original.getId()).session(ownerSession)
                .content(body), 400);
    }

    static Stream<String> invalidStatusRequests() {
        return Stream.of("{}", "{\"status\":null}", "{\"status\":\"INVALID\"}",
                "{\"status\":\"available\"}", "{\"status\":0}", "{\"status\":1}", "{\"status\":true}",
                "{\"status\":{}}", "{\"status\":[]}", "null", "{", "");
    }

    private MockHttpServletRequestBuilder menuRequest(MenuOperation operation, Object restaurantId, Object itemId) {
        MockHttpServletRequestBuilder request = switch (operation) {
            case ADD -> post(MENU, restaurantId);
            case EDIT -> put(MENU + "/{menuItemId}", restaurantId, itemId);
            case REMOVE -> delete(MENU + "/{menuItemId}", restaurantId, itemId);
            case STATUS -> patch(MENU + "/{menuItemId}/status", restaurantId, itemId);
        };
        if (operation != MenuOperation.REMOVE) {
            request.contentType(APPLICATION_JSON).content(operation == MenuOperation.STATUS
                    ? "{\"status\":\"SOLD_OUT\"}" : "{\"name\":\"Changed\",\"price\":1}");
        }
        return request;
    }

    private void assertRejectedWithoutChanges(MockHttpServletRequestBuilder request, int expectedStatus) throws Exception {
        Set<MenuItem> previousMenu = Set.copyOf(menuItems.findAll());

        mockMvc.perform(request)
                .andExpect(status().is(expectedStatus))
                .andExpect(content().contentTypeCompatibleWith(APPLICATION_JSON))
                .andExpect(jsonPath("$.message").isNotEmpty());

        assertEquals(previousMenu, Set.copyOf(menuItems.findAll()));
    }

    private MockHttpSession login(String username) throws Exception {
        MockHttpSession session = new MockHttpSession();
        mockMvc.perform(post("/api/auth/login").session(session).contentType(APPLICATION_JSON)
                        .content("{\"username\":\"%s\",\"password\":\"password\"}".formatted(username)))
                .andExpect(status().isOk());
        return session;
    }

    private enum MenuOperation {
        ADD, EDIT, REMOVE, STATUS
    }
}
