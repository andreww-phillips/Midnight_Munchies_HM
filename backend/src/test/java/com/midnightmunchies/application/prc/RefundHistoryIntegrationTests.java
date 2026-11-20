package com.midnightmunchies.application.prc;

import com.jayway.jsonpath.JsonPath;
import com.midnightmunchies.application.reg.AuthService;
import com.midnightmunchies.domain.ord.Order;
import com.midnightmunchies.domain.ord.OrderEventLog;
import com.midnightmunchies.domain.ord.OrderLine;
import com.midnightmunchies.domain.ord.OrderRepository;
import com.midnightmunchies.domain.ord.OrderStatus;
import com.midnightmunchies.domain.ord.Restaurant;
import com.midnightmunchies.domain.ord.RestaurantRepository;
import com.midnightmunchies.domain.ord.RestaurantStatus;
import com.midnightmunchies.domain.prc.Charge;
import com.midnightmunchies.domain.prc.LedgerEntry;
import com.midnightmunchies.domain.prc.Refund;
import com.midnightmunchies.domain.reg.UserAccount;
import com.midnightmunchies.domain.reg.UserRole;
import com.midnightmunchies.technicalservices.LedgerRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.hamcrest.Matchers.hasItems;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Real refund commands followed by role-scoped, read-only history requests. */
@SpringBootTest(properties = "spring.application.name=issue-52-refund-history-test")
@AutoConfigureMockMvc
class RefundHistoryIntegrationTests {

    private static final String HISTORY = "/api/prc/history";
    private static final Instant CREATED = Instant.parse("2026-11-13T01:00:00Z");
    private static final Charge CHARGE = new Charge(new BigDecimal("24.70"), new BigDecimal("3.99"),
            new BigDecimal("1.50"), BigDecimal.ZERO, new BigDecimal("30.19"));
    private static final Set<String> REFUND_FIELDS = Set.of("billing", "refund", "refunds", "refundId",
            "amount", "operatorId", "refundedAt", "totalCharge", "charge", "pricingPending");

    @Autowired private MockMvc mvc;
    @Autowired private AuthService auth;
    @Autowired private OrderRepository orders;
    @Autowired private RestaurantRepository restaurants;
    @Autowired private LedgerRepository ledger;
    @Autowired private OrderEventLog events;

    private UserAccount customer;
    private UserAccount staff;
    private UserAccount courier;
    private UserAccount operator;
    private LedgerEntry purchase;
    private LedgerEntry unrelated;

    @BeforeEach
    void seedIndependentPurchases() {
        customer = account(UserRole.CUSTOMER);
        staff = account(UserRole.RESTAURANT_STAFF);
        courier = account(UserRole.COURIER);
        operator = auth.authenticate("operator@example.com", "password");
        purchase = savePurchase(customer, staff, courier, OrderStatus.DELIVERED, CHARGE);
        unrelated = savePurchase(account(UserRole.CUSTOMER), account(UserRole.RESTAURANT_STAFF),
                account(UserRole.COURIER), OrderStatus.DELIVERED, CHARGE);
    }

    @Test
    void customerReceivesSeparateRefundFactsAlongsideTheUnchangedOriginalCharge() throws Exception {
        mvc.perform(get(HISTORY + "/{id}", purchase.orderId()).session(session(customer)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.billing.refunds").isEmpty());
        Refund refund = refund(purchase);

        mvc.perform(get(HISTORY + "/{id}", purchase.orderId()).session(session(customer)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REFUNDED"))
                .andExpect(jsonPath("$.timestamps.REFUNDED").value(refund.refundedAt().toString()))
                .andExpect(jsonPath("$.billing.totalCharge").value(30.19))
                .andExpect(jsonPath("$.billing.charge.deliveryFee").value(3.99))
                .andExpect(jsonPath("$.billing.pricingPending").value(false))
                .andExpect(jsonPath("$.billing.refunds.length()").value(1))
                .andExpect(jsonPath("$.billing.refunds[0].refundId").value(refund.refundId().toString()))
                .andExpect(jsonPath("$.billing.refunds[0].orderId").value(purchase.orderId().toString()))
                .andExpect(jsonPath("$.billing.refunds[0].amount").value(30.19))
                .andExpect(jsonPath("$.billing.refunds[0].refundedAt").value(refund.refundedAt().toString()))
                .andExpect(jsonPath("$.billing.refunds[0].operatorId").value(operator.id()));
        mvc.perform(get(HISTORY).session(session(customer)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].billing.refunds[0].refundId").value(refund.refundId().toString()));
    }

    @Test
    void operatorSeesEveryRefundAssociatedWithItsOwnParentOrder() throws Exception {
        Refund first = refund(purchase);
        Refund second = refund(unrelated);
        String json = mvc.perform(get(HISTORY).session(session(operator)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].billing.refunds[*].refundId",
                        hasItems(first.refundId().toString(), second.refundId().toString())))
                .andReturn().getResponse().getContentAsString();
        List<Map<String, Object>> history = JsonPath.read(json, "$");
        for (Map<String, Object> row : history) {
            Map<?, ?> billing = (Map<?, ?>) row.get("billing");
            for (Object item : (List<?>) billing.get("refunds")) {
                assertEquals(row.get("orderId"), ((Map<?, ?>) item).get("orderId"));
            }
        }
        mvc.perform(get(HISTORY + "/{id}", unrelated.orderId()).session(session(operator)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.billing.refunds[0].refundId").value(second.refundId().toString()));
    }

    @Test
    void staffSeeTheRefundedStateOfTheirRestaurantWithoutBillingOrRefundAmounts() throws Exception {
        refund(purchase);
        String list = mvc.perform(get(HISTORY).session(session(staff)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].status").value("REFUNDED"))
                .andReturn().getResponse().getContentAsString();
        String detail = mvc.perform(get(HISTORY + "/{id}", purchase.orderId()).session(session(staff)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.orderLines[0].unitPrice").value(12.35))
                .andExpect(jsonPath("$.timestamps.REFUNDED").isString())
                .andReturn().getResponse().getContentAsString();
        assertNoFields(JsonPath.read(list, "$"), REFUND_FIELDS);
        assertNoFields(JsonPath.read(detail, "$"), REFUND_FIELDS);
    }

    @Test
    void carryingCourierRetainsHistoryWithoutAnyMonetaryOrRefundTransactionFields() throws Exception {
        refund(purchase);
        String list = mvc.perform(get(HISTORY).session(session(courier)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].status").value("REFUNDED"))
                .andReturn().getResponse().getContentAsString();
        String detail = mvc.perform(get(HISTORY + "/{id}", purchase.orderId()).session(session(courier)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.orderLines[0].quantity").value(2))
                .andReturn().getResponse().getContentAsString();
        assertNoFields(JsonPath.read(list, "$"), REFUND_FIELDS);
        assertNoFields(JsonPath.read(detail, "$"), REFUND_FIELDS);
        assertNoFields(JsonPath.read(list, "$"), Set.of("unitPrice"));
        assertNoFields(JsonPath.read(detail, "$"), Set.of("unitPrice"));
    }

    @ParameterizedTest
    @EnumSource(value = UserRole.class, names = "PLATFORM_OPERATOR", mode = EnumSource.Mode.EXCLUDE)
    void unrelatedRolesCannotRetrieveRefundsByForgingIdsOrRequestParameters(UserRole role) throws Exception {
        Refund refund = refund(purchase);
        MockHttpSession viewer = session(account(role));
        mvc.perform(get(HISTORY).session(viewer).param("refundId", refund.refundId().toString())
                        .param("orderId", purchase.orderId().toString()).param("role", "PLATFORM_OPERATOR")
                        .param("customerId", customer.id()).param("restaurantId", purchase.restaurantId().toString())
                        .param("courierId", courier.id()))
                .andExpect(status().isOk()).andExpect(content().json("[]"));
        String hidden = mvc.perform(get(HISTORY + "/{id}", purchase.orderId()).session(viewer)
                        .param("refundId", refund.refundId().toString()).param("role", "PLATFORM_OPERATOR"))
                .andExpect(status().isNotFound()).andReturn().getResponse().getContentAsString();
        String missing = mvc.perform(get(HISTORY + "/{id}", UUID.randomUUID()).session(viewer))
                .andExpect(status().isNotFound()).andReturn().getResponse().getContentAsString();
        assertEquals(missing, hidden);
    }

    @Test
    void ordersWithoutRefundRecordsHaveAnEmptyArrayAndReadsNeverInventTransactions() throws Exception {
        LedgerEntry legacy = savePurchase(customer, staff, courier, OrderStatus.REFUNDED, CHARGE);
        for (LedgerEntry entry : List.of(purchase, legacy)) {
            mvc.perform(get(HISTORY + "/{id}", entry.orderId()).session(session(customer)))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.billing.refunds").isArray())
                    .andExpect(jsonPath("$.billing.refunds").isEmpty());
            assertTrue(ledger.findRefundByOrderId(entry.orderId()).isEmpty());
        }
    }

    @Test
    void unpricedHistoryKeepsPendingPricingAndAnEmptyRefundArray() throws Exception {
        LedgerEntry unpriced = savePurchase(customer, staff, courier, OrderStatus.DELIVERED, null);
        mvc.perform(get(HISTORY + "/{id}", unpriced.orderId()).session(session(customer)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.billing.pricingPending").value(true))
                .andExpect(jsonPath("$.billing.refunds").isEmpty());
        assertNull(ledger.findByOrderId(unpriced.orderId()).orElseThrow().totalCharge());
    }

    @Test
    void recordedZeroRefundIsVisibleWithoutBecomingPendingPricing() throws Exception {
        Charge zero = new Charge(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);
        LedgerEntry free = savePurchase(customer, staff, courier, OrderStatus.DELIVERED, zero);
        refund(free);
        mvc.perform(get(HISTORY + "/{id}", free.orderId()).session(session(customer)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.billing.totalCharge").value(0))
                .andExpect(jsonPath("$.billing.pricingPending").value(false))
                .andExpect(jsonPath("$.billing.refunds[0].amount").value(0));
    }

    @Test
    void repeatedReadsPreserveOriginalChargesRefundRecordsAndEvents() throws Exception {
        Refund refund = refund(purchase);
        LedgerEntry snapshot = ledger.findByOrderId(purchase.orderId()).orElseThrow();
        Order authoritative = orders.findById(purchase.orderId()).orElseThrow();
        List<Refund> before = ledger.findAllRefunds();
        var originalEvents = events.findByOrderId(purchase.orderId());
        for (UserAccount viewer : List.of(customer, operator)) {
            mvc.perform(get(HISTORY).session(session(viewer))).andExpect(status().isOk());
            mvc.perform(get(HISTORY + "/{id}", purchase.orderId()).session(session(viewer))).andExpect(status().isOk());
        }
        assertEquals(before, ledger.findAllRefunds());
        assertSame(refund, ledger.findRefundByOrderId(purchase.orderId()).orElseThrow());
        assertSame(snapshot, ledger.findByOrderId(purchase.orderId()).orElseThrow());
        assertSame(purchase.charge(), snapshot.charge());
        assertSame(authoritative, orders.findById(purchase.orderId()).orElseThrow());
        assertEquals(originalEvents, events.findByOrderId(purchase.orderId()));
    }

    private Refund refund(LedgerEntry entry) throws Exception {
        mvc.perform(post("/api/prc/orders/{id}/refund", entry.orderId()).session(session(operator)))
                .andExpect(status().isOk());
        return ledger.findRefundByOrderId(entry.orderId()).orElseThrow();
    }

    private LedgerEntry savePurchase(UserAccount customer, UserAccount staff, UserAccount courier,
                                     OrderStatus status, Charge charge) {
        UUID restaurantId = UUID.randomUUID();
        restaurants.save(new Restaurant(restaurantId, UUID.fromString(staff.id()),
                "Refund history " + restaurantId, RestaurantStatus.OPEN, 45.5, -73.6));
        Order order = orders.save(Order.restore(UUID.randomUUID(), UUID.fromString(customer.id()), restaurantId,
                List.of(new OrderLine(UUID.randomUUID(), "Original meal", 2, new BigDecimal("12.35"))),
                CREATED, status, CREATED.plusSeconds(1)));
        Map<OrderStatus, Instant> times = status == OrderStatus.REFUNDED
                ? Map.of(OrderStatus.PLACED, CREATED, OrderStatus.DELIVERED, CREATED.plusSeconds(60),
                        OrderStatus.REFUNDED, CREATED.plusSeconds(90))
                : Map.of(OrderStatus.PLACED, CREATED, OrderStatus.DELIVERED, CREATED.plusSeconds(60));
        return ledger.save(new LedgerEntry(order.getId(), order.getCustomerId(), restaurantId,
                UUID.fromString(courier.id()), order.getOrderLines(), status, CREATED, times,
                charge == null ? null : charge.total(), charge));
    }

    private UserAccount account(UserRole role) {
        String id = UUID.randomUUID().toString();
        return new UserAccount(id, "refund-history-" + id, "unused-test-hash", role);
    }

    private MockHttpSession session(UserAccount account) {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("authenticatedUser", account);
        return session;
    }

    private void assertNoFields(Object value, Set<String> forbidden) {
        if (value instanceof Map<?, ?> map) {
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                assertFalse(forbidden.contains(entry.getKey()), "Leaked field: " + entry.getKey());
                assertNoFields(entry.getValue(), forbidden);
            }
        } else if (value instanceof List<?> list) {
            list.forEach(item -> assertNoFields(item, forbidden));
        }
    }
}
