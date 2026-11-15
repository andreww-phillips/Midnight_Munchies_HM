package com.midnightmunchies.application.prc;

import com.jayway.jsonpath.JsonPath;
import com.midnightmunchies.application.reg.AuthService;
import com.midnightmunchies.domain.ord.OrderLine;
import com.midnightmunchies.domain.ord.OrderStatus;
import com.midnightmunchies.domain.ord.Restaurant;
import com.midnightmunchies.domain.ord.RestaurantRepository;
import com.midnightmunchies.domain.ord.RestaurantStatus;
import com.midnightmunchies.domain.prc.Charge;
import com.midnightmunchies.domain.prc.LedgerEntry;
import com.midnightmunchies.domain.reg.UserAccount;
import com.midnightmunchies.domain.reg.UserRole;
import com.midnightmunchies.technicalservices.LedgerRepository;
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
import java.time.Instant;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Real PRC strategies, repositories, session gate, and JSON field authorization. */
@SpringBootTest(properties = "spring.application.name=issue-52-history-test")
@AutoConfigureMockMvc
class OrderHistoryControllerTests {

    private static final String HISTORY = "/api/prc/history";
    private static final Instant CREATED = Instant.parse("2026-11-15T01:00:00Z");
    private static final Charge CHARGE = new Charge(new BigDecimal("24.70"), new BigDecimal("3.99"),
            new BigDecimal("1.50"), new BigDecimal("0.00"), new BigDecimal("30.19"));
    private static final Set<String> BILLING_FIELDS = Set.of("billing", "unitPrice", "totalCharge", "charge",
            "itemSubtotal", "deliveryFee", "serviceFee", "smallOrderSurcharge", "pricingPending");

    @Autowired private MockMvc mvc;
    @Autowired private AuthService auth;
    @Autowired private LedgerRepository ledger;
    @Autowired private RestaurantRepository restaurants;

    private UserAccount customer;
    private UserAccount staff;
    private UserAccount courier;
    private UserAccount otherCourier;
    private LedgerEntry delivered;
    private LedgerEntry unrelated;
    private LedgerEntry placed;
    private LedgerEntry refunded;

    @BeforeEach
    void seedIndependentViewerAssociations() {
        customer = user(UserRole.CUSTOMER);
        staff = user(UserRole.RESTAURANT_STAFF);
        courier = user(UserRole.COURIER);
        otherCourier = user(UserRole.COURIER);
        UUID restaurant = restaurant(staff);
        UUID secondOwnedRestaurant = restaurant(staff);
        UUID otherRestaurant = restaurant(user(UserRole.RESTAURANT_STAFF));
        delivered = save(id(customer), restaurant, id(courier), OrderStatus.DELIVERED, CHARGE);
        unrelated = save(UUID.randomUUID(), otherRestaurant, id(otherCourier), OrderStatus.DELIVERED, CHARGE);
        placed = save(id(customer), secondOwnedRestaurant, null, OrderStatus.PLACED, null);
        refunded = save(UUID.randomUUID(), restaurant, id(otherCourier), OrderStatus.REFUNDED, CHARGE);
    }

    @Test
    void customerListContainsOnlyTheirOrdersIncludingUnpricedOrders() throws Exception {
        mvc.perform(get(HISTORY).session(session(customer)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].orderId", containsInAnyOrder(
                        delivered.orderId().toString(), placed.orderId().toString())));
    }

    @Test
    void owningCustomerCanInspectRecordedPurchaseTimestampsAndCharge() throws Exception {
        mvc.perform(get(HISTORY + "/{id}", delivered.orderId()).session(session(customer)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orderId").value(delivered.orderId().toString()))
                .andExpect(jsonPath("$.customerId").value(customer.id()))
                .andExpect(jsonPath("$.restaurantId").value(delivered.restaurantId().toString()))
                .andExpect(jsonPath("$.courierId").value(courier.id()))
                .andExpect(jsonPath("$.status").value("DELIVERED"))
                .andExpect(jsonPath("$.createdAt").value(CREATED.toString()))
                .andExpect(jsonPath("$.timestamps.DELIVERED").value(CREATED.plusSeconds(60).toString()))
                .andExpect(jsonPath("$.orderLines[0].menuItemName").value("Original meal"))
                .andExpect(jsonPath("$.orderLines[0].quantity").value(2))
                .andExpect(jsonPath("$.orderLines[0].unitPrice").value(12.35))
                .andExpect(jsonPath("$.billing.totalCharge").value(30.19))
                .andExpect(jsonPath("$.billing.charge.deliveryFee").value(3.99))
                .andExpect(jsonPath("$.billing.pricingPending").value(false));
    }

    @Test
    void staffSeeAllOwnedRestaurantsUsingOwnershipRatherThanMatchingAccountIds() throws Exception {
        assertNotEquals(id(staff), delivered.restaurantId());
        mvc.perform(get(HISTORY).session(session(staff)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].orderId", containsInAnyOrder(delivered.orderId().toString(),
                        placed.orderId().toString(), refunded.orderId().toString())))
                .andExpect(jsonPath("$[*].billing").isEmpty());
        mvc.perform(get(HISTORY + "/{id}", delivered.orderId()).session(session(staff)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orderLines[0].unitPrice").value(12.35))
                .andExpect(jsonPath("$.billing").doesNotHaveJsonPath());
    }

    @Test
    void staffWithNoOwnedRestaurantReceiveNoHistory() throws Exception {
        MockHttpSession viewer = session(user(UserRole.RESTAURANT_STAFF));
        mvc.perform(get(HISTORY).session(viewer))
                .andExpect(status().isOk()).andExpect(content().json("[]"));
        mvc.perform(get(HISTORY + "/{id}", delivered.orderId()).session(viewer))
                .andExpect(status().isNotFound());
    }

    @Test
    void courierSeesOnlyCarriedOrdersAndNoMonetaryFieldsInListsOrDetails() throws Exception {
        String list = mvc.perform(get(HISTORY).session(session(courier)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].orderId", containsInAnyOrder(delivered.orderId().toString())))
                .andReturn().getResponse().getContentAsString();
        String detail = mvc.perform(get(HISTORY + "/{id}", delivered.orderId()).session(session(courier)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orderLines[0].quantity").value(2))
                .andExpect(jsonPath("$.timestamps.DELIVERED").exists())
                .andReturn().getResponse().getContentAsString();
        assertNoBilling(JsonPath.read(list, "$"));
        assertNoBilling(JsonPath.read(detail, "$"));
    }

    @Test
    void courierRetainsHistoryWhenACarriedOrderHasBeenRefunded() throws Exception {
        mvc.perform(get(HISTORY).session(session(otherCourier)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].orderId", containsInAnyOrder(
                        unrelated.orderId().toString(), refunded.orderId().toString())));
        mvc.perform(get(HISTORY + "/{id}", refunded.orderId()).session(session(otherCourier)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REFUNDED"))
                .andExpect(jsonPath("$.billing").doesNotHaveJsonPath());
    }

    @Test
    void operatorSeesAllOrdersAndRecordedBilling() throws Exception {
        MockHttpSession operator = session(user(UserRole.PLATFORM_OPERATOR));
        mvc.perform(get(HISTORY).session(operator))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].orderId", hasItems(delivered.orderId().toString(),
                        unrelated.orderId().toString(), placed.orderId().toString(), refunded.orderId().toString())));
        mvc.perform(get(HISTORY + "/{id}", refunded.orderId()).session(operator))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REFUNDED"))
                .andExpect(jsonPath("$.billing.totalCharge").value(30.19));
    }

    @ParameterizedTest
    @EnumSource(value = UserRole.class, names = {"CUSTOMER", "RESTAURANT_STAFF", "COURIER"})
    void unauthorizedAndMissingDetailsHaveTheSameResponse(UserRole role) throws Exception {
        UserAccount account = switch (role) {
            case CUSTOMER -> customer;
            case RESTAURANT_STAFF -> staff;
            case COURIER -> courier;
            default -> throw new AssertionError("Unexpected role");
        };
        MockHttpSession viewer = session(account);
        String hidden = mvc.perform(get(HISTORY + "/{id}", unrelated.orderId()).session(viewer))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Order does not exist"))
                .andReturn().getResponse().getContentAsString();
        String missing = mvc.perform(get(HISTORY + "/{id}", UUID.randomUUID()).session(viewer))
                .andExpect(status().isNotFound()).andReturn().getResponse().getContentAsString();
        assertEquals(missing, hidden);
    }

    @Test
    void queryParametersCannotOverrideIdentityRoleOrRestaurant() throws Exception {
        for (UserAccount account : List.of(customer, staff, courier)) {
            mvc.perform(get(HISTORY + "/{id}", unrelated.orderId()).session(session(account))
                            .param("userId", unrelated.customerId().toString())
                            .param("customerId", unrelated.customerId().toString())
                            .param("courierId", unrelated.courierId().toString())
                            .param("restaurantId", unrelated.restaurantId().toString())
                            .param("role", "PLATFORM_OPERATOR"))
                    .andExpect(status().isNotFound());
        }
        mvc.perform(get(HISTORY).session(session(customer))
                        .param("userId", unrelated.customerId().toString()).param("role", "PLATFORM_OPERATOR"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].orderId", containsInAnyOrder(
                        delivered.orderId().toString(), placed.orderId().toString())));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "/not-a-uuid", "/00000000-0000-0000-0000-000000000000"})
    void anonymousRequestsAreRejectedBeforeIdentifierValidation(String suffix) throws Exception {
        mvc.perform(get(HISTORY + suffix).param("role", "PLATFORM_OPERATOR"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Authentication is required"));
    }

    @Test
    void malformedOrderIdHasAClearBadRequestResponse() throws Exception {
        mvc.perform(get(HISTORY + "/not-a-uuid").session(session(customer)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Order ID must be a valid UUID"));
    }

    @Test
    void customerWithoutOrdersReceivesAnEmptyList() throws Exception {
        mvc.perform(get(HISTORY).session(session(user(UserRole.CUSTOMER))))
                .andExpect(status().isOk()).andExpect(content().json("[]"));
    }

    @Test
    void unrecordedPricingRemainsDistinctFromARecordedZeroCharge() throws Exception {
        mvc.perform(get(HISTORY + "/{id}", placed.orderId()).session(session(customer)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PLACED"))
                .andExpect(jsonPath("$.billing.totalCharge").value(nullValue()))
                .andExpect(jsonPath("$.billing.pricingPending").value(true));
        Charge zero = new Charge(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);
        LedgerEntry free = save(id(customer), delivered.restaurantId(), id(courier), OrderStatus.DELIVERED, zero);
        mvc.perform(get(HISTORY + "/{id}", free.orderId()).session(session(customer)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.billing.totalCharge").value(0))
                .andExpect(jsonPath("$.billing.pricingPending").value(false));
    }

    @Test
    void historyReadsPreserveTheLedgerAndItsStableOrdering() throws Exception {
        List<LedgerEntry> before = ledger.findAll();
        MockHttpSession operator = session(user(UserRole.PLATFORM_OPERATOR));
        String first = mvc.perform(get(HISTORY).session(operator)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String second = mvc.perform(get(HISTORY).session(operator)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        List<String> ids = JsonPath.read(first, "$[*].orderId");
        assertEquals(before.stream().map(entry -> entry.orderId().toString()).toList(), ids);
        assertEquals(first, second);
        assertEquals(before, ledger.findAll());
        assertSame(delivered, ledger.findByOrderId(delivered.orderId()).orElseThrow());
    }

    @Test
    void aRealLoginSessionSuppliesTheHistoryIdentity() throws Exception {
        UserAccount seeded = auth.authenticate("customer@example.com", "password");
        LedgerEntry purchase = save(id(seeded), delivered.restaurantId(), null, OrderStatus.PLACED, null);
        MockHttpSession loggedIn = (MockHttpSession) mvc.perform(post("/api/auth/login")
                        .contentType(APPLICATION_JSON)
                        .content("{\"username\":\"customer@example.com\",\"password\":\"password\"}"))
                .andExpect(status().isOk()).andReturn().getRequest().getSession(false);
        mvc.perform(get(HISTORY + "/{id}", purchase.orderId()).session(loggedIn))
                .andExpect(status().isOk()).andExpect(jsonPath("$.customerId").value(seeded.id()));
        mvc.perform(post("/api/auth/logout").session(loggedIn)).andExpect(status().isNoContent());
        mvc.perform(get(HISTORY)).andExpect(status().isUnauthorized());
    }

    @Test
    void frontendOriginCanMakeCredentialedHistoryRequests() throws Exception {
        for (String path : List.of(HISTORY, HISTORY + "/" + delivered.orderId())) {
            mvc.perform(options(path).header("Origin", "http://localhost:5173")
                            .header("Access-Control-Request-Method", "GET"))
                    .andExpect(status().isOk())
                    .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:5173"))
                    .andExpect(header().string("Access-Control-Allow-Credentials", "true"));
        }
    }

    private UserAccount user(UserRole role) {
        String id = UUID.randomUUID().toString();
        return new UserAccount(id, "history-" + id, "unused-test-hash", role);
    }

    private UUID id(UserAccount account) {
        return UUID.fromString(account.id());
    }

    private UUID restaurant(UserAccount owner) {
        UUID id = UUID.randomUUID();
        restaurants.save(new Restaurant(id, id(owner), "History restaurant " + id,
                RestaurantStatus.OPEN, 45.5, -73.6));
        return id;
    }

    private MockHttpSession session(UserAccount account) {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("authenticatedUser", account);
        return session;
    }

    private LedgerEntry save(UUID customerId, UUID restaurantId, UUID courierId, OrderStatus status, Charge charge) {
        Map<OrderStatus, Instant> timestamps = new EnumMap<>(OrderStatus.class);
        timestamps.put(OrderStatus.PLACED, CREATED);
        if (status != OrderStatus.PLACED) timestamps.put(status, CREATED.plusSeconds(60));
        if (status == OrderStatus.REFUNDED) {
            timestamps.put(OrderStatus.DELIVERED, CREATED.plusSeconds(60));
            timestamps.put(OrderStatus.REFUNDED, CREATED.plusSeconds(90));
        }
        return ledger.save(new LedgerEntry(UUID.randomUUID(), customerId, restaurantId, courierId,
                List.of(new OrderLine(UUID.randomUUID(), "Original meal", 2, new BigDecimal("12.35"))),
                status, CREATED, timestamps, charge == null ? null : charge.total(), charge));
    }

    private void assertNoBilling(Object value) {
        if (value instanceof Map<?, ?> map) {
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                assertFalse(BILLING_FIELDS.contains(entry.getKey()), "Leaked monetary field: " + entry.getKey());
                assertNoBilling(entry.getValue());
            }
        } else if (value instanceof List<?> list) {
            list.forEach(this::assertNoBilling);
        }
    }
}
