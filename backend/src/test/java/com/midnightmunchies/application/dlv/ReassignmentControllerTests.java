package com.midnightmunchies.application.dlv;

import com.midnightmunchies.domain.ord.Actor;
import com.midnightmunchies.domain.ord.Order;
import com.midnightmunchies.domain.ord.OrderEvent;
import com.midnightmunchies.domain.ord.OrderLine;
import com.midnightmunchies.domain.ord.OrderRepository;
import com.midnightmunchies.domain.ord.OrderStatus;
import com.midnightmunchies.domain.reg.UserAccount;
import com.midnightmunchies.domain.reg.UserRole;
import com.midnightmunchies.technicalservices.AssignmentRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class ReassignmentControllerTests {
    private static final String REASSIGN = "/api/dlv/orders/{orderId}/reassign";
    private static final String DELIVERIES = "/api/dlv/operator/deliveries";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CourierService courierService;

    @Autowired
    private OrderRepository orders;

    @Autowired
    private AssignmentRepository assignments;

    @Autowired
    private DispatchQueueService dispatchQueue;

    @Test
    void operatorReassignsAnAcceptedOrderAndTheCommandIsRecorded() throws Exception {
        UserAccount courier = offeredCourier(readyOrderWithIdleCourier());
        Order order = orders.findById(assignments.findActiveByCourierId(id(courier)).orElseThrow().orderId()).orElseThrow();
        courierService.accept(courier, order.getId());
        UserAccount operator = account(UserRole.PLATFORM_OPERATOR);

        mockMvc.perform(post(REASSIGN, order.getId()).session(session(operator)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.previous.status").value("REASSIGNED"))
                .andExpect(jsonPath("$.previous.releasedBy").value(operator.id()))
                .andExpect(jsonPath("$.previous.courierId").value(courier.id()))
                .andExpect(jsonPath("$.order.id").value(order.getId().toString()));
    }

    @Test
    void nonOperatorsCannotReassign() throws Exception {
        UserAccount courier = offeredCourier(readyOrderWithIdleCourier());
        UUID orderId = assignments.findActiveByCourierId(id(courier)).orElseThrow().orderId();

        mockMvc.perform(post(REASSIGN, orderId).session(session(courier)))
                .andExpect(status().isForbidden());
        mockMvc.perform(post(REASSIGN, orderId).session(session(account(UserRole.RESTAURANT_STAFF))))
                .andExpect(status().isForbidden());
        mockMvc.perform(post(REASSIGN, orderId).session(session(account(UserRole.CUSTOMER))))
                .andExpect(status().isForbidden());
        mockMvc.perform(post(REASSIGN, orderId))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void pickedUpOrderCannotBeReassigned() throws Exception {
        UserAccount courier = offeredCourier(readyOrderWithIdleCourier());
        UUID orderId = assignments.findActiveByCourierId(id(courier)).orElseThrow().orderId();
        courierService.accept(courier, orderId);
        courierService.pickUp(courier, orderId);

        mockMvc.perform(post(REASSIGN, orderId).session(session(account(UserRole.PLATFORM_OPERATOR))))
                .andExpect(status().isConflict());
    }

    @Test
    void unknownOrderIsNotFound() throws Exception {
        mockMvc.perform(post(REASSIGN, UUID.randomUUID()).session(session(account(UserRole.PLATFORM_OPERATOR))))
                .andExpect(status().isNotFound());
    }

    @Test
    void onlyOperatorsCanListActiveDeliveries() throws Exception {
        mockMvc.perform(get(DELIVERIES).session(session(account(UserRole.PLATFORM_OPERATOR))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").isArray())
                .andExpect(jsonPath("$.queued").isArray());
        mockMvc.perform(get(DELIVERIES).session(session(account(UserRole.COURIER))))
                .andExpect(status().isForbidden());
    }

    private Order readyOrderWithIdleCourier() {
        dispatchQueue.view().forEach(dispatchQueue::remove);
        Order order = new Order(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                List.of(new OrderLine(UUID.randomUUID(), "Meal", 1, new BigDecimal("8.50"))), Instant.now());
        order.accept();
        order.startPreparing();
        order.markReady();
        order.pullTransitions();
        orders.save(order);
        courierService.onEvent(OrderEvent.of(UUID.randomUUID(), order.getId(), order.getRestaurantId(),
                OrderStatus.PREPARING, OrderStatus.READY, Instant.now(),
                new Actor(UUID.randomUUID(), UserRole.RESTAURANT_STAFF)));
        courierService.setDuty(account(UserRole.COURIER), true);
        return order;
    }

    private UserAccount offeredCourier(Order order) {
        UUID courierId = assignments.findActiveByOrderId(order.getId()).orElseThrow().courierId();
        return new UserAccount(courierId.toString(), "courier@example.test", "hash", UserRole.COURIER);
    }

    private MockHttpSession session(UserAccount account) {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("authenticatedUser", account);
        return session;
    }

    private UserAccount account(UserRole role) {
        return new UserAccount(UUID.randomUUID().toString(), role + "@example.test", "hash", role);
    }

    private UUID id(UserAccount account) {
        return UUID.fromString(account.id());
    }
}
