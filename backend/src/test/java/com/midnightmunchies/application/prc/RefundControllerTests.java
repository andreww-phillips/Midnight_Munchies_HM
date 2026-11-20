package com.midnightmunchies.application.prc;

import com.jayway.jsonpath.JsonPath;
import com.midnightmunchies.application.reg.AuthService;
import com.midnightmunchies.domain.ord.Order;
import com.midnightmunchies.domain.ord.OrderEvent;
import com.midnightmunchies.domain.ord.OrderEventLog;
import com.midnightmunchies.domain.ord.OrderLine;
import com.midnightmunchies.domain.ord.OrderRepository;
import com.midnightmunchies.domain.ord.OrderStatus;
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
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = "spring.application.name=issue-52-refund-test")
@AutoConfigureMockMvc
class RefundControllerTests {

    private static final String REFUND = "/api/prc/orders/{id}/refund";
    private static final Instant PLACED = Instant.parse("2026-11-13T01:00:00Z");
    private static final Charge CHARGE = new Charge(new BigDecimal("24.70"), new BigDecimal("3.99"),
            new BigDecimal("1.50"), BigDecimal.ZERO, new BigDecimal("30.19"));
    @Autowired private MockMvc mvc;
    @Autowired private AuthService auth;
    @Autowired private OrderRepository orders;
    @Autowired private LedgerRepository ledger;
    @Autowired private OrderEventLog events;

    private Order order;
    private LedgerEntry original;
    private UserAccount operator;

    @BeforeEach
    void seedChargedDelivery() {
        order = saveOrder(OrderStatus.DELIVERED, CHARGE.total(), CHARGE);
        original = ledger.findByOrderId(order.getId()).orElseThrow();
        operator = auth.authenticate("operator@example.com", "password");
    }

    @Test
    void operatorRefundReturnsRecordedAmountAndUpdatesOrdLedgerAndEventHistory() throws Exception {
        String response = mvc.perform(post(REFUND, order.getId()).session(session(operator)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orderId").value(order.getId().toString()))
                .andExpect(jsonPath("$.status").value("REFUNDED"))
                .andExpect(jsonPath("$.refund.orderId").value(order.getId().toString()))
                .andExpect(jsonPath("$.refund.amount").value(30.19))
                .andExpect(jsonPath("$.refund.operatorId").value(operator.id()))
                .andExpect(jsonPath("$.refund.refundId").isString())
                .andExpect(jsonPath("$.refund.refundedAt").isString())
                .andExpect(jsonPath("$.eventId").isString())
                .andReturn().getResponse().getContentAsString();

        Refund refund = ledger.findRefundByOrderId(order.getId()).orElseThrow();
        LedgerEntry updated = ledger.findByOrderId(order.getId()).orElseThrow();
        assertEquals(OrderStatus.REFUNDED, orders.findById(order.getId()).orElseThrow().getStatus());
        assertEquals(OrderStatus.REFUNDED, updated.status());
        assertSame(original.charge(), updated.charge());
        assertEquals(original.totalCharge(), updated.totalCharge());
        assertEquals(original.timestamps().get(OrderStatus.DELIVERED), updated.timestamps().get(OrderStatus.DELIVERED));
        assertEquals(refund.refundedAt(), updated.timestamps().get(OrderStatus.REFUNDED));
        OrderEvent event = events.findByOrderId(order.getId()).getFirst();
        assertEquals(JsonPath.read(response, "$.eventId"), event.eventId().toString());
        assertEquals(refund.refundedAt(), event.occurredAt());
        assertEquals(UUID.fromString(operator.id()), event.actorId());
        assertEquals(UserRole.PLATFORM_OPERATOR, event.actorRole());

        mvc.perform(get("/api/ord/orders/{id}/events", order.getId()).session(session(operator)))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].toStatus").value("REFUNDED"));
        mvc.perform(get("/api/prc/history/{id}", order.getId()).session(session(operator)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("REFUNDED"))
                .andExpect(jsonPath("$.billing.totalCharge").value(30.19));
    }

    @ParameterizedTest
    @EnumSource(value = UserRole.class, names = "PLATFORM_OPERATOR", mode = EnumSource.Mode.EXCLUDE)
    void nonOperatorsAreRejectedBeforeOrderLookupOrIdValidation(UserRole role) throws Exception {
        for (String id : List.of(order.getId().toString(), UUID.randomUUID().toString(), "invalid")) {
            mvc.perform(post(REFUND, id).session(session(account(role)))
                            .param("role", "PLATFORM_OPERATOR").param("operatorId", operator.id()))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.message").value("Only platform operators can refund orders"));
        }
        assertUnchanged(order, original);
    }

    @ParameterizedTest
    @ValueSource(strings = {"invalid", "00000000-0000-0000-0000-000000000000"})
    void anonymousRefundsRequireASessionBeforeIdValidation(String id) throws Exception {
        mvc.perform(post(REFUND, id)).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Authentication is required"));
    }

    @Test
    void operatorGetsClearErrorsForMalformedAndMissingOrderIds() throws Exception {
        mvc.perform(post(REFUND, "invalid").session(session(operator)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Order ID must be a valid UUID"));
        mvc.perform(post(REFUND, UUID.randomUUID()).session(session(operator)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Order does not exist"));
    }

    @ParameterizedTest
    @EnumSource(value = OrderStatus.class, names = "DELIVERED", mode = EnumSource.Mode.EXCLUDE)
    void otherStatesAreRejectedWithoutChanges(OrderStatus state) throws Exception {
        Order other = saveOrder(state, null, null);
        LedgerEntry before = ledger.findByOrderId(other.getId()).orElseThrow();

        mvc.perform(post(REFUND, other.getId()).session(session(operator)))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.message").isString());
        assertUnchanged(other, before);
    }

    @ParameterizedTest
    @ValueSource(strings = {"missing-ledger", "missing-charge"})
    void missingRecordedChargesAreConflicts(String scenario) throws Exception {
        Order unpriced;
        if (scenario.equals("missing-ledger")) {
            unpriced = orders.save(Order.restore(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                    order.getOrderLines(), PLACED, OrderStatus.DELIVERED, PLACED.plusSeconds(1)));
        } else {
            unpriced = saveOrder(OrderStatus.DELIVERED, null, null);
        }
        mvc.perform(post(REFUND, unpriced.getId()).session(session(operator)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("A recorded charge is required for a refund"));
        assertEquals(OrderStatus.DELIVERED, orders.findById(unpriced.getId()).orElseThrow().getStatus());
        assertTrue(ledger.findRefundByOrderId(unpriced.getId()).isEmpty());
        assertTrue(events.findByOrderId(unpriced.getId()).isEmpty());
    }

    @Test
    void duplicateCommandPreservesFirstRefundAndRecordsNoAdditionalEvent() throws Exception {
        mvc.perform(post(REFUND, order.getId()).session(session(operator))).andExpect(status().isOk());
        Refund first = ledger.findRefundByOrderId(order.getId()).orElseThrow();
        LedgerEntry snapshot = ledger.findByOrderId(order.getId()).orElseThrow();

        mvc.perform(post(REFUND, order.getId()).session(session(operator)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Order has already been refunded"));
        assertSame(first, ledger.findRefundByOrderId(order.getId()).orElseThrow());
        assertSame(snapshot, ledger.findByOrderId(order.getId()).orElseThrow());
        assertEquals(1, events.findByOrderId(order.getId()).size());
    }

    @Test
    void requestBodyAndParametersCannotSetAmountOrOperatorIdentity() throws Exception {
        mvc.perform(post(REFUND, order.getId()).session(session(operator))
                        .param("amount", "0.01").param("operatorId", UUID.randomUUID().toString())
                        .contentType(APPLICATION_JSON)
                        .content("{\"amount\":0.01,\"operatorId\":\"forged\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.refund.amount").value(30.19))
                .andExpect(jsonPath("$.refund.operatorId").value(operator.id()));
    }

    @ParameterizedTest
    @ValueSource(strings = {"0.00", "30.705"})
    void zeroAndLegacyChargesAreRefundedExactlyWithoutRepricing(String total) throws Exception {
        Order legacy = saveOrder(OrderStatus.DELIVERED, new BigDecimal(total), null);
        mvc.perform(post(REFUND, legacy.getId()).session(session(operator)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.refund.amount").value(Double.parseDouble(total)));
        assertEquals(new BigDecimal(total), ledger.findRefundByOrderId(legacy.getId()).orElseThrow().amount());
    }

    @Test
    void internalCommitErrorsHaveASanitizedServerErrorResponse() throws Exception {
        RefundService failing = mock(RefundService.class);
        when(failing.refund(any(), any())).thenThrow(new IllegalStateException("internal storage details"));
        MockMvc isolated = MockMvcBuilders.standaloneSetup(new RefundController(failing)).build();
        isolated.perform(post(REFUND, order.getId()).session(session(operator)))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.message").value("Refund could not be completed"));
    }

    @Test
    void frontendCanSendACredentialedRefundCommand() throws Exception {
        mvc.perform(options(REFUND, order.getId()).header("Origin", "http://localhost:5173")
                        .header("Access-Control-Request-Method", "POST"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:5173"))
                .andExpect(header().string("Access-Control-Allow-Credentials", "true"));
    }

    private void assertUnchanged(Order expected, LedgerEntry expectedLedger) {
        assertSame(expected, orders.findById(expected.getId()).orElseThrow());
        assertSame(expectedLedger, ledger.findByOrderId(expected.getId()).orElseThrow());
        assertTrue(ledger.findRefundByOrderId(expected.getId()).isEmpty());
        assertTrue(events.findByOrderId(expected.getId()).isEmpty());
    }

    private UserAccount account(UserRole role) {
        String id = UUID.randomUUID().toString();
        return new UserAccount(id, "refund-" + id, "unused-test-hash", role);
    }

    private MockHttpSession session(UserAccount account) {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("authenticatedUser", account);
        return session;
    }

    private Order saveOrder(OrderStatus status, BigDecimal total, Charge charge) {
        Order saved = orders.save(Order.restore(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                List.of(new OrderLine(UUID.randomUUID(), "Original meal", 2, new BigDecimal("12.35"))),
                PLACED, status, PLACED.plusSeconds(1)));
        Map<OrderStatus, Instant> timestamps = new EnumMap<>(OrderStatus.class);
        timestamps.put(OrderStatus.PLACED, PLACED);
        if (status != OrderStatus.PLACED) timestamps.put(status, PLACED.plusSeconds(60));
        if (status == OrderStatus.REFUNDED) timestamps.put(OrderStatus.DELIVERED, PLACED.plusSeconds(30));
        ledger.save(new LedgerEntry(saved.getId(), saved.getCustomerId(), saved.getRestaurantId(), UUID.randomUUID(),
                saved.getOrderLines(), status, PLACED, timestamps, total, charge));
        return saved;
    }
}
