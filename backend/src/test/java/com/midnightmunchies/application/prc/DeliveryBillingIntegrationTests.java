package com.midnightmunchies.application.prc;

import com.midnightmunchies.application.dlv.CourierService;
import com.midnightmunchies.application.ord.AuthorizedOrdService;
import com.midnightmunchies.application.ord.OrdService;
import com.midnightmunchies.domain.ord.MenuItem;
import com.midnightmunchies.domain.ord.MenuItemRepository;
import com.midnightmunchies.domain.ord.MenuItemStatus;
import com.midnightmunchies.domain.ord.Order;
import com.midnightmunchies.domain.ord.OrderEvent;
import com.midnightmunchies.domain.ord.OrderEventLog;
import com.midnightmunchies.domain.ord.OrderStatus;
import com.midnightmunchies.domain.ord.Restaurant;
import com.midnightmunchies.domain.ord.RestaurantRepository;
import com.midnightmunchies.domain.ord.RestaurantStatus;
import com.midnightmunchies.domain.prc.Charge;
import com.midnightmunchies.domain.reg.UserAccount;
import com.midnightmunchies.domain.reg.UserRole;
import com.midnightmunchies.technicalservices.LedgerRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "spring.application.name=delivery-billing-test")
@AutoConfigureMockMvc
@Import(DeliveryBillingIntegrationTests.PricingFixture.class)
class DeliveryBillingIntegrationTests {

    // Test data only. Production fee values and calculation belong to issue #50.
    private static final Charge QUOTE = new Charge(new BigDecimal("24.70"), new BigDecimal("3.00"),
            new BigDecimal("2.00"), new BigDecimal("1.00"), new BigDecimal("30.70"));

    @TestConfiguration
    static class PricingFixture {
        @Bean
        DeliveryPricing testDeliveryPricing() {
            return mock(DeliveryPricing.class);
        }
    }

    @Autowired private AuthorizedOrdService ordering;
    @Autowired private CourierService delivery;
    @Autowired private DeliveryBillingService billing;
    @Autowired private DeliveryPricing pricing;
    @Autowired private LedgerRepository ledger;
    @Autowired private RestaurantRepository restaurants;
    @Autowired private MenuItemRepository menu;
    @Autowired private OrderEventLog eventLog;
    @Autowired private MockMvc mvc;

    private final UUID customerId = UUID.randomUUID();
    private final MockHttpSession customer = session(customerId, UserRole.CUSTOMER);

    @BeforeEach
    void setUp() {
        reset(pricing);
        when(pricing.calculate(anyList())).thenReturn(Optional.of(QUOTE));
    }

    @Test
    void actualDeliveryRecordsAndExposesOneFrozenBreakdownUsingOriginalPurchaseFacts() throws Exception {
        Order order = deliver();

        mvc.perform(get("/api/prc/orders/{orderId}/summary", order.getId()).session(customer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalCharge").value(30.70))
                .andExpect(jsonPath("$.pricingPending").value(false))
                .andExpect(jsonPath("$.charge.itemSubtotal").value(24.70))
                .andExpect(jsonPath("$.charge.deliveryFee").value(3.00))
                .andExpect(jsonPath("$.charge.serviceFee").value(2.00))
                .andExpect(jsonPath("$.charge.smallOrderSurcharge").value(1.00))
                .andExpect(jsonPath("$.charge.total").value(30.70))
                .andExpect(jsonPath("$.orderLines[0].menuItemName").value("Original meal"))
                .andExpect(jsonPath("$.orderLines[0].unitPrice").value(12.35));
        OrderEvent delivered = eventLog.findByOrderId(order.getId()).stream()
                .filter(event -> event.toStatus() == OrderStatus.DELIVERED).findFirst().orElseThrow();
        when(pricing.calculate(anyList())).thenThrow(new IllegalStateException("Changed rules must not reprice"));
        billing.onEvent(delivered);
        billing.recordDeliveryCharge(order.getId());
        mvc.perform(get("/api/prc/orders/{orderId}/summary", order.getId()).session(customer))
                .andExpect(status().isOk()).andExpect(jsonPath("$.charge.deliveryFee").value(3.00));

        assertEquals(QUOTE, ledger.findByOrderId(order.getId()).orElseThrow().charge());
        assertEquals(5, eventLog.findByOrderId(order.getId()).size());
        verify(pricing, times(1)).calculate(order.getOrderLines());
    }

    @Test
    void pricingFailurePreservesDeliveryAndPendingSummaryUntilAnExplicitRetry() throws Exception {
        when(pricing.calculate(anyList())).thenThrow(new IllegalStateException("Test pricing outage"));
        Order order = deliver();

        assertEquals(OrderStatus.DELIVERED, order.getStatus());
        mvc.perform(get("/api/prc/orders/{orderId}/summary", order.getId()).session(customer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pricingPending").value(true))
                .andExpect(jsonPath("$.totalCharge").value(nullValue()))
                .andExpect(jsonPath("$.charge").value(nullValue()));
        verify(pricing, times(1)).calculate(anyList());
        doReturn(Optional.of(QUOTE)).when(pricing).calculate(anyList());
        assertEquals(Optional.of(QUOTE), billing.recordDeliveryCharge(order.getId()));
        mvc.perform(get("/api/prc/orders/{orderId}/summary", order.getId()).session(customer))
                .andExpect(status().isOk()).andExpect(jsonPath("$.pricingPending").value(false))
                .andExpect(jsonPath("$.charge.total").value(30.70));
        verify(pricing, times(2)).calculate(anyList());
    }

    private Order deliver() {
        UUID restaurantId = UUID.randomUUID();
        UUID staffId = UUID.randomUUID();
        UUID itemId = UUID.randomUUID();
        MockHttpSession staff = session(staffId, UserRole.RESTAURANT_STAFF);
        restaurants.save(new Restaurant(restaurantId, staffId, "Billing diner " + restaurantId,
                RestaurantStatus.OPEN, 45.5, -73.5));
        menu.save(new MenuItem(itemId, restaurantId, "Original meal", new BigDecimal("12.35"), MenuItemStatus.AVAILABLE));
        Order order = ordering.placeOrder(restaurantId, List.of(new OrdService.OrderLineRequest(itemId, 2)), customer);
        ordering.editMenuItem(restaurantId, itemId, "Changed meal", new BigDecimal("99.99"), staff);
        UserAccount courier = new UserAccount(UUID.randomUUID().toString(), "billing-courier@example.test", "hash", UserRole.COURIER);
        delivery.setDuty(courier, true);
        ordering.acceptOrder(restaurantId, order.getId(), staff);
        ordering.startNextPreparation(restaurantId, staff);
        ordering.markOrderReady(restaurantId, order.getId(), staff);
        delivery.accept(courier, order.getId());
        delivery.pickUp(courier, order.getId());
        delivery.deliver(courier, order.getId());
        delivery.setDuty(courier, false);
        return order;
    }

    private static MockHttpSession session(UUID id, UserRole role) {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("authenticatedUser", new UserAccount(id.toString(), "billing-user@example.test", "hash", role));
        return session;
    }
}
