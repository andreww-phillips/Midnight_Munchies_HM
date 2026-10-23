package com.midnightmunchies.application;

import com.jayway.jsonpath.JsonPath;
import com.midnightmunchies.technicalservices.ord.repository.SupabaseCatalogClient;
import com.midnightmunchies.technicalservices.ord.repository.SupabaseMenuItemRepository;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Exercises the real HTTP catalog adapters against a local PostgREST substitute, never live Supabase. */
@SpringBootTest
@AutoConfigureMockMvc
class RegistrationMenuIntegrationTests {
    private static final JsonMapper JSON = JsonMapper.builder().enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS).build();
    private static final Map<String, Map<String, JsonNode>> TABLES = new ConcurrentHashMap<>();
    private static final HttpServer CATALOG = startCatalog();
    private static volatile boolean unavailable;
    private static volatile boolean refuseDeletes;
    private static volatile boolean legacyUniqueNames;
    private static volatile boolean legacyRequiredImage;
    private static final String MENU = "/api/ord/restaurants/{id}/menu";

    @Autowired private MockMvc mvc;

    @DynamicPropertySource
    static void catalogSettings(DynamicPropertyRegistry properties) {
        properties.add("catalog.mode", () -> "supabase");
        properties.add("SUPABASE_URL", () -> "http://127.0.0.1:" + CATALOG.getAddress().getPort());
        properties.add("SUPABASE_KEY", () -> "test-key");
    }

    @BeforeEach
    void resetCatalog() {
        unavailable = false;
        refuseDeletes = false;
        legacyUniqueNames = false;
        legacyRequiredImage = false;
        TABLES.clear();
        TABLES.put("Restaurant", new ConcurrentHashMap<>());
        TABLES.put("Menu_Items", new ConcurrentHashMap<>());
    }

    @Test
    void registeredStaffCanManageTheSameRestaurantCreatedByTheRegistrationScreen() throws Exception {
        MockHttpSession staff = new MockHttpSession();
        String staffId = register(staff, "RESTAURANT_STAFF");
        // Exact ownership convention of registration_page: Restaurant.id = returned user.id.
        TABLES.get("Restaurant").put(staffId, JSON.readTree("""
                {"id":"%s","restaurant_name":"New Diner","restaurant_logo":"logos/new.png",
                 "restaurant_latitude":null,"restaurant_longitude":null,"menu":"https://example.com/menu.pdf",
                 "created_at":"2026-10-23T00:00:00Z"}
                """.formatted(staffId)));

        mvc.perform(get("/api/ord/restaurants").session(staff))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].ownerId").value(staffId))
                .andExpect(jsonPath("$[0].logo").value("logos/new.png"));
        String added = mvc.perform(post(MENU, staffId).session(staff).contentType(APPLICATION_JSON)
                        .content("{\"name\":\"Poutine\",\"price\":\"12.35\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        String itemId = JsonPath.read(added, "$.id");
        JsonNode stored = TABLES.get("Menu_Items").get(itemId);
        assertEquals(staffId, stored.get("restaurant_id").asText());
        assertEquals("12.35", stored.get("item_cost").asText());
        assertTrue(stored.get("item_image").isNull(), "Adding an item must not require an image");
        assertTrue(stored.get("item_description").isEmpty(), "Adding an item must not require a description");

        MockHttpSession customer = new MockHttpSession();
        register(customer, "CUSTOMER");
        mvc.perform(get(MENU, staffId).session(customer)).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Poutine"));
        String originalOrder = mvc.perform(post("/api/ord/restaurants/{id}/orders", staffId).session(customer)
                        .contentType(APPLICATION_JSON).content("{\"lines\":[{\"menuItemId\":\"%s\",\"quantity\":2}]}".formatted(itemId)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();

        mvc.perform(put(MENU + "/{item}", staffId, itemId).session(staff).contentType(APPLICATION_JSON)
                        .content("{\"name\":\"Loaded Poutine\",\"price\":\"15.00\"}"))
                .andExpect(status().isOk());
        assertEquals("Loaded Poutine", TABLES.get("Menu_Items").get(itemId).get("item_name").asText());
        mvc.perform(patch(MENU + "/{item}/status", staffId, itemId).session(staff).contentType(APPLICATION_JSON)
                        .content("{\"status\":\"SOLD_OUT\"}"))
                .andExpect(status().isOk());
        assertFalse(TABLES.get("Menu_Items").get(itemId).get("availability").asBoolean());
        mvc.perform(post("/api/ord/restaurants/{id}/orders", staffId).session(customer)
                        .contentType(APPLICATION_JSON).content("{\"lines\":[{\"menuItemId\":\"%s\",\"quantity\":1}]}".formatted(itemId)))
                .andExpect(status().isBadRequest());
        mvc.perform(delete(MENU + "/{item}", staffId, itemId).session(staff)).andExpect(status().isNoContent());
        assertFalse(TABLES.get("Menu_Items").containsKey(itemId));
        mvc.perform(get("/api/ord/orders").session(customer)).andExpect(status().isOk())
                .andExpect(content().json("[" + originalOrder + "]"));
    }

    @Test
    void registeredStaffAndCustomersCannotEditAnotherRestaurantsSupabaseRows() throws Exception {
        MockHttpSession owner = new MockHttpSession();
        String ownerId = register(owner, "RESTAURANT_STAFF");
        TABLES.get("Restaurant").put(ownerId, JSON.readTree("{\"id\":\"%s\",\"restaurant_name\":\"Owned Diner\"}".formatted(ownerId)));
        MockHttpSession stranger = new MockHttpSession();
        register(stranger, "RESTAURANT_STAFF");
        MockHttpSession customer = new MockHttpSession();
        register(customer, "CUSTOMER");
        for (MockHttpSession session : List.of(stranger, customer, new MockHttpSession())) {
            mvc.perform(post(MENU, ownerId).session(session).contentType(APPLICATION_JSON)
                            .content("{\"name\":\"Spoofed\",\"price\":\"1\"}"))
                    .andExpect(status().is(session == stranger || session == customer ? 403 : 401));
        }
        assertTrue(TABLES.get("Menu_Items").isEmpty());
    }

    @Test
    void editingAndAvailabilityPreserveExistingDishImagesAndDescriptions() throws Exception {
        MockHttpSession staff = new MockHttpSession();
        String staffId = register(staff, "RESTAURANT_STAFF");
        TABLES.get("Restaurant").put(staffId, JSON.readTree("{\"id\":\"%s\",\"restaurant_name\":\"Diner\"}".formatted(staffId)));
        String itemId = UUID.randomUUID().toString();
        TABLES.get("Menu_Items").put(itemId, JSON.readTree("""
                {"id":"%s","restaurant_id":"%s","item_name":"Poutine","item_cost":12.35,
                 "availability":true,"item_image":"poutine.png","item_description":["Fries","Cheese curds"]}
                """.formatted(itemId, staffId)));
        mvc.perform(put(MENU + "/{item}", staffId, itemId).session(staff).contentType(APPLICATION_JSON)
                        .content("{\"name\":\"Loaded Poutine\",\"price\":\"0.12345678901234567890\"}"))
                .andExpect(status().isOk());
        mvc.perform(patch(MENU + "/{item}/status", staffId, itemId).session(staff).contentType(APPLICATION_JSON)
                        .content("{\"status\":\"SOLD_OUT\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.image").value("poutine.png"))
                .andExpect(jsonPath("$.description[1]").value("Cheese curds"));
        assertEquals("0.12345678901234567890", TABLES.get("Menu_Items").get(itemId).get("item_cost").asText());
    }

    @Test
    void catalogOutageIsReportedRatherThanShownAsAnEmptyRestaurantList() throws Exception {
        MockHttpSession session = new MockHttpSession();
        register(session, "CUSTOMER");
        unavailable = true;
        mvc.perform(get("/api/ord/restaurants").session(session)).andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("Unable to access the catalog")));
    }

    @Test
    void aNewRepositoryInstanceReadsPreviouslySavedMenuChangesFromTheDatabase() throws Exception {
        MockHttpSession staff = new MockHttpSession();
        String staffId = register(staff, "RESTAURANT_STAFF");
        TABLES.get("Restaurant").put(staffId, JSON.readTree("{\"id\":\"%s\",\"restaurant_name\":\"Diner\"}".formatted(staffId)));
        String added = mvc.perform(post(MENU, staffId).session(staff).contentType(APPLICATION_JSON)
                        .content("{\"name\":\"Database Poutine\",\"price\":\"12.35\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        String itemId = JsonPath.read(added, "$.id");
        var freshRepository = new SupabaseMenuItemRepository(new SupabaseCatalogClient(
                "http://127.0.0.1:" + CATALOG.getAddress().getPort(), "test-key"));
        var stored = freshRepository.findById(UUID.fromString(itemId)).orElseThrow();
        assertEquals("Database Poutine", stored.getName());
        assertEquals(UUID.fromString(staffId), stored.getRestaurantId());
        freshRepository.deleteById(stored.getId());
        mvc.perform(get(MENU, staffId).session(staff)).andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void databasePolicyRejectingDeletionDoesNotProduceAFakeSuccessfulResponse() throws Exception {
        MockHttpSession staff = new MockHttpSession();
        String staffId = register(staff, "RESTAURANT_STAFF");
        TABLES.get("Restaurant").put(staffId, JSON.readTree("{\"id\":\"%s\",\"restaurant_name\":\"Diner\"}".formatted(staffId)));
        String added = mvc.perform(post(MENU, staffId).session(staff).contentType(APPLICATION_JSON)
                        .content("{\"name\":\"Poutine\",\"price\":\"12.35\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        String itemId = JsonPath.read(added, "$.id");
        refuseDeletes = true;
        mvc.perform(delete(MENU + "/{item}", staffId, itemId).session(staff))
                .andExpect(status().isServiceUnavailable());
        assertTrue(TABLES.get("Menu_Items").containsKey(itemId));
    }

    @Test
    void itemsWithOnlyNameAndPriceCanRepeatWithinAndAcrossRestaurants() throws Exception {
        MockHttpSession staff = new MockHttpSession();
        String staffId = register(staff, "RESTAURANT_STAFF");
        TABLES.get("Restaurant").put(staffId, JSON.readTree("{\"id\":\"%s\",\"restaurant_name\":\"Diner\"}".formatted(staffId)));
        String drink = mvc.perform(post(MENU, staffId).session(staff).contentType(APPLICATION_JSON)
                        .content("{\"name\":\"Drink\",\"price\":\"3.99\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        String fries = mvc.perform(post(MENU, staffId).session(staff).contentType(APPLICATION_JSON)
                        .content("{\"name\":\"French Fries\",\"price\":\"5.99\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        String drinkId = JsonPath.read(drink, "$.id");
        String friesId = JsonPath.read(fries, "$.id");
        assertTrue(TABLES.get("Menu_Items").get(drinkId).get("item_image").isNull());
        assertTrue(TABLES.get("Menu_Items").get(friesId).get("item_image").isNull());
        assertTrue(TABLES.get("Menu_Items").get(drinkId).get("item_description").isEmpty());
        assertTrue(TABLES.get("Menu_Items").get(friesId).get("item_description").isEmpty());
        mvc.perform(get(MENU, staffId).session(staff)).andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].image").isEmpty()).andExpect(jsonPath("$[1].image").isEmpty())
                .andExpect(jsonPath("$[0].description").isEmpty()).andExpect(jsonPath("$[1].description").isEmpty());

        mvc.perform(put(MENU + "/{item}", staffId, friesId).session(staff).contentType(APPLICATION_JSON)
                        .content("{\"name\":\"Large French Fries\",\"price\":\"6.99\"}"))
                .andExpect(status().isOk());
        mvc.perform(patch(MENU + "/{item}/status", staffId, friesId).session(staff).contentType(APPLICATION_JSON)
                        .content("{\"status\":\"SOLD_OUT\"}"))
                .andExpect(status().isOk());
        assertTrue(TABLES.get("Menu_Items").get(friesId).get("item_image").isNull());
        assertTrue(TABLES.get("Menu_Items").get(friesId).get("item_description").isEmpty());
        mvc.perform(post(MENU, staffId).session(staff).contentType(APPLICATION_JSON)
                        .content("{\"name\":\"Drink\",\"price\":\"3.99\"}"))
                .andExpect(status().isCreated());

        MockHttpSession otherStaff = new MockHttpSession();
        String otherStaffId = register(otherStaff, "RESTAURANT_STAFF");
        TABLES.get("Restaurant").put(otherStaffId, JSON.readTree("{\"id\":\"%s\",\"restaurant_name\":\"Other Diner\"}".formatted(otherStaffId)));
        mvc.perform(post(MENU, otherStaffId).session(otherStaff).contentType(APPLICATION_JSON)
                        .content("{\"name\":\"Drink\",\"price\":\"3.99\"}"))
                .andExpect(status().isCreated());
        assertEquals(4, TABLES.get("Menu_Items").size());
    }

    @Test
    void editingAnExistingBlankImageItemKeepsItsMetadataOptional() throws Exception {
        MockHttpSession staff = new MockHttpSession();
        String staffId = register(staff, "RESTAURANT_STAFF");
        TABLES.get("Restaurant").put(staffId, JSON.readTree("{\"id\":\"%s\",\"restaurant_name\":\"Diner\"}".formatted(staffId)));
        String itemId = UUID.randomUUID().toString();
        TABLES.get("Menu_Items").put(itemId, JSON.readTree("""
                {"id":"%s","restaurant_id":"%s","item_name":"Drink","item_cost":3.99,
                 "availability":true,"item_image":"","item_description":[]}
                """.formatted(itemId, staffId)));
        mvc.perform(put(MENU + "/{item}", staffId, itemId).session(staff).contentType(APPLICATION_JSON)
                        .content("{\"name\":\"Drink\",\"price\":\"4.99\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.image").isEmpty())
                .andExpect(jsonPath("$.description").isEmpty());
        assertTrue(TABLES.get("Menu_Items").get(itemId).get("item_image").isNull());
        assertTrue(TABLES.get("Menu_Items").get(itemId).get("item_description").isEmpty());
        mvc.perform(get(MENU, staffId).session(staff)).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].image").isEmpty());
    }

    @Test
    void legacyGlobalNameConstraintReturnsAnActionableConflictInsteadOfACatalogOutage() throws Exception {
        MockHttpSession staff = new MockHttpSession();
        String staffId = register(staff, "RESTAURANT_STAFF");
        TABLES.get("Restaurant").put(staffId, JSON.readTree("{\"id\":\"%s\",\"restaurant_name\":\"Diner\"}".formatted(staffId)));
        mvc.perform(post(MENU, staffId).session(staff).contentType(APPLICATION_JSON)
                        .content("{\"name\":\"Drink\",\"price\":\"3.99\"}"))
                .andExpect(status().isCreated());
        legacyUniqueNames = true;
        mvc.perform(post(MENU, staffId).session(staff).contentType(APPLICATION_JSON)
                        .content("{\"name\":\"Drink\",\"price\":\"3.99\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("issue 7 schema update")));
        assertEquals(1, TABLES.get("Menu_Items").size());
    }

    @Test
    void legacyImageRequirementReturnsASchemaUpdateMessageInsteadOfAskingForAnImage() throws Exception {
        MockHttpSession staff = new MockHttpSession();
        String staffId = register(staff, "RESTAURANT_STAFF");
        TABLES.get("Restaurant").put(staffId, JSON.readTree("{\"id\":\"%s\",\"restaurant_name\":\"Diner\"}".formatted(staffId)));
        legacyRequiredImage = true;
        mvc.perform(post(MENU, staffId).session(staff).contentType(APPLICATION_JSON)
                        .content("{\"name\":\"Drink\",\"price\":\"3.99\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("images and descriptions must be optional")));
        assertTrue(TABLES.get("Menu_Items").isEmpty());
    }

    @Test
    void oldReservedMetadataIsHiddenAndReplacedByOrdinaryEmptyValuesWhenEdited() throws Exception {
        MockHttpSession staff = new MockHttpSession();
        String staffId = register(staff, "RESTAURANT_STAFF");
        TABLES.get("Restaurant").put(staffId, JSON.readTree("{\"id\":\"%s\",\"restaurant_name\":\"Diner\"}".formatted(staffId)));
        String itemId = UUID.randomUUID().toString();
        TABLES.get("Menu_Items").put(itemId, JSON.readTree("""
                {"id":"%s","restaurant_id":"%s","item_name":"Drink","item_cost":3.99,
                 "availability":true,"item_image":"__midnight_munchies_no_image__/%s",
                 "item_description":["__midnight_munchies_no_description__/%s"]}
                """.formatted(itemId, staffId, itemId, itemId)));
        mvc.perform(get(MENU, staffId).session(staff)).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].image").isEmpty()).andExpect(jsonPath("$[0].description").isEmpty());
        mvc.perform(put(MENU + "/{item}", staffId, itemId).session(staff).contentType(APPLICATION_JSON)
                        .content("{\"name\":\"Drink\",\"price\":\"3.99\"}"))
                .andExpect(status().isOk());
        assertTrue(TABLES.get("Menu_Items").get(itemId).get("item_image").isNull());
        assertTrue(TABLES.get("Menu_Items").get(itemId).get("item_description").isEmpty());
    }

    private String register(MockHttpSession session, String role) throws Exception {
        String username = UUID.randomUUID() + "@example.com";
        String response = mvc.perform(post("/api/auth/register").session(session).contentType(APPLICATION_JSON)
                        .content("{\"username\":\"%s\",\"password\":\"password\",\"role\":\"%s\"}".formatted(username, role)))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.role").value(role))
                .andReturn().getResponse().getContentAsString();
        mvc.perform(get("/api/auth/me").session(session)).andExpect(status().isOk());
        return JsonPath.read(response, "$.id");
    }

    private static HttpServer startCatalog() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/rest/v1/", RegistrationMenuIntegrationTests::handle);
            server.start();
            return server;
        } catch (IOException exception) { throw new IllegalStateException(exception); }
    }

    private static void handle(HttpExchange exchange) throws IOException {
        try (exchange) {
            if (unavailable) { respond(exchange, 503, "{}"); return; }
            if (!"test-key".equals(exchange.getRequestHeaders().getFirst("apikey"))) {
                respond(exchange, 401, "{}"); return;
            }
            String table = exchange.getRequestURI().getPath().substring("/rest/v1/".length());
            Map<String, JsonNode> rows = TABLES.get(table);
            if (exchange.getRequestMethod().equals("POST")) {
                JsonNode row = JSON.readTree(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
                if (table.equals("Menu_Items") && legacyRequiredImage && row.path("item_image").isNull()) {
                    respond(exchange, 400, "{\"code\":\"23502\",\"message\":\"item_image must not be null\"}");
                    return;
                }
                if (table.equals("Menu_Items") && legacyUniqueNames && rows.values().stream().anyMatch(existing ->
                        !existing.get("id").asText().equals(row.get("id").asText())
                                && existing.path("item_name").asText().equals(row.path("item_name").asText()))) {
                    respond(exchange, 409, "{\"code\":\"23505\",\"message\":\"duplicate key value violates unique constraint Menu_Items_item_name_key\"}");
                    return;
                }
                rows.put(row.get("id").asText(), row);
                exchange.sendResponseHeaders(201, -1);
                return;
            }
            List<JsonNode> matching = new ArrayList<>(rows.values());
            String query = exchange.getRequestURI().getRawQuery();
            if (query != null) for (String parameter : query.split("&")) {
                String[] parts = URLDecoder.decode(parameter, StandardCharsets.UTF_8).split("=", 2);
                if (parts.length == 2 && parts[1].startsWith("eq.")) {
                    matching.removeIf(row -> !row.path(parts[0]).asText().equals(parts[1].substring(3)));
                }
            }
            if (exchange.getRequestMethod().equals("DELETE")) {
                if (refuseDeletes) matching.clear();
                else matching.forEach(row -> rows.remove(row.get("id").asText()));
            }
            respond(exchange, 200, JSON.writeValueAsString(matching));
        }
    }

    private static void respond(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
    }
}
