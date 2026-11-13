package com.midnightmunchies.application.prc;

import com.midnightmunchies.application.reg.AuthService;
import com.midnightmunchies.domain.ord.OrderLine;
import com.midnightmunchies.domain.ord.OrderStatus;
import com.midnightmunchies.domain.prc.LedgerEntry;
import com.midnightmunchies.domain.reg.UserAccount;
import com.midnightmunchies.domain.reg.UserRole;
import com.midnightmunchies.technicalservices.LedgerRepository;
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
import java.util.UUID;

import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "spring.application.name=billing-controller-test")
@AutoConfigureMockMvc
class BillingControllerTests {

    private static final String SUMMARY = "/api/prc/orders/{orderId}/summary";
    private static final Instant CREATED_AT = Instant.parse("2026-11-13T01:00:00Z");

    @Autowired private MockMvc mvc;
    @Autowired private AuthService auth;
    @Autowired private LedgerRepository ledger;

    @Test
    void owningCustomerReceivesTheDeliveredPurchaseWithExplicitPendingPricing() throws Exception {
        UserAccount customer = account("customer@example.com");
        LedgerEntry entry = save(UUID.fromString(customer.id()), OrderStatus.DELIVERED, null);

        mvc.perform(get(SUMMARY, entry.orderId()).session(session(customer)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orderId").value(entry.orderId().toString()))
                .andExpect(jsonPath("$.customerId").value(customer.id()))
                .andExpect(jsonPath("$.restaurantId").value(entry.restaurantId().toString()))
                .andExpect(jsonPath("$.courierId").value(entry.courierId().toString()))
                .andExpect(jsonPath("$.status").value("DELIVERED"))
                .andExpect(jsonPath("$.createdAt").value(CREATED_AT.toString()))
                .andExpect(jsonPath("$.deliveredAt").value(CREATED_AT.plusSeconds(600).toString()))
                .andExpect(jsonPath("$.timestamps.PLACED").value(CREATED_AT.toString()))
                .andExpect(jsonPath("$.timestamps.DELIVERED").value(CREATED_AT.plusSeconds(600).toString()))
                .andExpect(jsonPath("$.orderLines[0].menuItemName").value("Poutine"))
                .andExpect(jsonPath("$.orderLines[0].quantity").value(2))
                .andExpect(jsonPath("$.orderLines[0].unitPrice").value(12.35))
                .andExpect(jsonPath("$.totalCharge").hasJsonPath())
                .andExpect(jsonPath("$.totalCharge").value(nullValue()))
                .andExpect(jsonPath("$.pricingPending").value(true));
    }

    @ParameterizedTest
    @ValueSource(strings = {"0.00", "30.70"})
    void recordedChargesIncludingZeroAreSerializedWithoutPendingPricing(String amount) throws Exception {
        UserAccount customer = account("customer@example.com");
        LedgerEntry entry = save(UUID.fromString(customer.id()), OrderStatus.DELIVERED, new BigDecimal(amount));

        mvc.perform(get(SUMMARY, entry.orderId()).session(session(customer)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalCharge").value(Double.parseDouble(amount)))
                .andExpect(jsonPath("$.pricingPending").value(false));
    }

    @Test
    void operatorCanInspectAnotherCustomersSummary() throws Exception {
        LedgerEntry entry = save(UUID.randomUUID(), OrderStatus.DELIVERED, new BigDecimal("30.70"));

        mvc.perform(get(SUMMARY, entry.orderId()).session(session(account("operator@example.com"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orderId").value(entry.orderId().toString()))
                .andExpect(jsonPath("$.totalCharge").value(30.70));
    }

    @Test
    void otherCustomersOrderIsIndistinguishableFromAMissingOrder() throws Exception {
        LedgerEntry anotherCustomers = save(UUID.randomUUID(), OrderStatus.DELIVERED, new BigDecimal("30.70"));
        MockHttpSession customer = session(account("customer@example.com"));
        String hidden = mvc.perform(get(SUMMARY, anotherCustomers.orderId()).session(customer))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Order does not exist"))
                .andExpect(jsonPath("$.totalCharge").doesNotExist())
                .andReturn().getResponse().getContentAsString();
        String missing = mvc.perform(get(SUMMARY, UUID.randomUUID()).session(customer))
                .andExpect(status().isNotFound()).andReturn().getResponse().getContentAsString();

        assertEquals(missing, hidden);
    }

    @Test
    void ownershipIsCheckedBeforeRevealingThatASummaryIsNotYetAvailable() throws Exception {
        LedgerEntry entry = save(UUID.randomUUID(), OrderStatus.PLACED, null);

        mvc.perform(get(SUMMARY, entry.orderId()).session(session(account("customer@example.com"))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Order does not exist"));
    }

    @Test
    void queryParametersCannotOverrideTheAuthenticatedIdentityOrRole() throws Exception {
        LedgerEntry entry = save(UUID.randomUUID(), OrderStatus.DELIVERED, new BigDecimal("30.70"));

        mvc.perform(get(SUMMARY, entry.orderId()).session(session(account("customer@example.com")))
                        .param("customerId", entry.customerId().toString()).param("role", "PLATFORM_OPERATOR"))
                .andExpect(status().isNotFound());
    }

    @ParameterizedTest
    @ValueSource(strings = {"courier@example.com", "restaurant@example.com"})
    void courierAndRestaurantRolesCannotReadBillingDetailsOrEnumerateOrders(String username) throws Exception {
        LedgerEntry entry = save(UUID.randomUUID(), OrderStatus.DELIVERED, new BigDecimal("30.70"));
        MockHttpSession user = session(account(username));

        mvc.perform(get(SUMMARY, entry.orderId()).session(user))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("This role cannot view billing summaries"))
                .andExpect(jsonPath("$.totalCharge").doesNotExist());
        mvc.perform(get(SUMMARY, UUID.randomUUID()).session(user)).andExpect(status().isForbidden());
        mvc.perform(get(SUMMARY, "invalid-id").session(user)).andExpect(status().isForbidden());
    }

    @Test
    void anonymousRequestsRequireAuthenticationBeforeAnyLookupOrIdentifierValidation() throws Exception {
        LedgerEntry entry = save(UUID.randomUUID(), OrderStatus.DELIVERED, null);

        for (String id : List.of(entry.orderId().toString(), UUID.randomUUID().toString(), "invalid-id")) {
            mvc.perform(get(SUMMARY, id))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.message").value("Authentication is required"));
        }
    }

    @ParameterizedTest
    @EnumSource(value = OrderStatus.class, names = {"DELIVERED", "REFUNDED"}, mode = EnumSource.Mode.EXCLUDE)
    void owningCustomerGetsAConflictUntilTheOrderHasBeenDelivered(OrderStatus orderStatus) throws Exception {
        UserAccount customer = account("customer@example.com");
        LedgerEntry entry = save(UUID.fromString(customer.id()), orderStatus, null);

        mvc.perform(get(SUMMARY, entry.orderId()).session(session(customer)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Order summary is available only after delivery"));
    }

    @Test
    void authenticatedCustomerReceivesAStructuredErrorForAMalformedIdentifier() throws Exception {
        mvc.perform(get(SUMMARY, "invalid-id").session(session(account("customer@example.com"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Order ID must be a valid UUID"));
    }

    @Test
    void aFutureRefundedOrderCanRetainItsOriginalChargeInTheSummary() throws Exception {
        UserAccount customer = account("customer@example.com");
        LedgerEntry entry = save(UUID.fromString(customer.id()), OrderStatus.REFUNDED, new BigDecimal("30.70"));

        mvc.perform(get(SUMMARY, entry.orderId()).session(session(customer)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REFUNDED"))
                .andExpect(jsonPath("$.deliveredAt").value(CREATED_AT.plusSeconds(600).toString()))
                .andExpect(jsonPath("$.totalCharge").value(30.70));
    }

    @Test
    void repeatedGetsDoNotChangeTheLedgerOrItsCharge() throws Exception {
        UserAccount customer = account("customer@example.com");
        LedgerEntry entry = save(UUID.fromString(customer.id()), OrderStatus.DELIVERED, new BigDecimal("30.70"));
        List<LedgerEntry> before = ledger.findAll();
        MockHttpSession user = session(customer);

        mvc.perform(get(SUMMARY, entry.orderId()).session(user)).andExpect(status().isOk());
        mvc.perform(get(SUMMARY, entry.orderId()).session(user)).andExpect(status().isOk());

        assertEquals(before, ledger.findAll());
        assertSame(entry, ledger.findByOrderId(entry.orderId()).orElseThrow());
    }

    @Test
    void frontendOriginCanUseCredentialedSummaryRequests() throws Exception {
        mvc.perform(options(SUMMARY, UUID.randomUUID()).header("Origin", "http://localhost:5173")
                        .header("Access-Control-Request-Method", "GET"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:5173"))
                .andExpect(header().string("Access-Control-Allow-Credentials", "true"));
    }

    private UserAccount account(String username) {
        return auth.authenticate(username, "password");
    }

    private MockHttpSession session(UserAccount account) {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("authenticatedUser", account);
        return session;
    }

    private LedgerEntry save(UUID customerId, OrderStatus status, BigDecimal charge) {
        Map<OrderStatus, Instant> times = new EnumMap<>(OrderStatus.class);
        times.put(OrderStatus.PLACED, CREATED_AT);
        if (status != OrderStatus.PLACED) {
            times.put(status, CREATED_AT.plusSeconds(600));
        }
        if (status == OrderStatus.REFUNDED) {
            times.put(OrderStatus.DELIVERED, CREATED_AT.plusSeconds(600));
            times.put(OrderStatus.REFUNDED, CREATED_AT.plusSeconds(660));
        }
        return ledger.save(new LedgerEntry(UUID.randomUUID(), customerId, UUID.randomUUID(), UUID.randomUUID(),
                List.of(new OrderLine(UUID.randomUUID(), "Poutine", 2, new BigDecimal("12.35"))), status,
                CREATED_AT, times, charge));
    }
}
