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
import com.midnightmunchies.domain.prc.LedgerEntry;
import com.midnightmunchies.domain.reg.UserAccount;
import com.midnightmunchies.domain.reg.UserRole;
import com.midnightmunchies.technicalservices.LedgerRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.ApplicationContext;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Uses the real application pricing.properties and production adapter; no pricing mocks. */
@SpringBootTest(properties = "spring.application.name=configured-delivery-pricing-test")
@AutoConfigureMockMvc
class ConfiguredDeliveryPricingIntegrationTests {

    @Autowired private AuthorizedOrdService ordering;
    @Autowired private CourierService delivery;
    @Autowired private DeliveryBillingService billing;
    @Autowired private LedgerRepository ledger;
    @Autowired private RestaurantRepository restaurants;
    @Autowired private MenuItemRepository menu;
    @Autowired private OrderEventLog eventLog;
    @Autowired private MockMvc mvc;
    @Autowired private ApplicationContext context;

    private final UUID customerId = UUID.randomUUID();
    private final MockHttpSession customer = session(customerId, UserRole.CUSTOMER);

    @ParameterizedTest
    @CsvSource({
            "8.50, 1, 8.50, 2.00, 15.99",
            "7.50, 2, 15.00, 0.00, 20.49",
            "12.35, 2, 24.70, 0.00, 30.19",
            "14.995, 1, 15.00, 0.00, 20.49",
            "14.994, 1, 14.99, 2.00, 22.48"
    })
    void actualDeliveryExposesTheConfiguredChargeAndOriginalPurchase(String unitPrice, int quantity,
            String subtotal, String surcharge, String total) throws Exception {
        assertEquals(1, context.getBeansOfType(DeliveryPricing.class).size());
        assertInstanceOf(ConfiguredDeliveryPricing.class, context.getBean(DeliveryPricing.class));
        Order order = deliver(unitPrice, quantity);

        mvc.perform(get("/api/prc/orders/{orderId}/summary", order.getId()).session(customer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pricingPending").value(false))
                .andExpect(jsonPath("$.totalCharge").value(Double.parseDouble(total)))
                .andExpect(jsonPath("$.charge.itemSubtotal").value(Double.parseDouble(subtotal)))
                .andExpect(jsonPath("$.charge.deliveryFee").value(3.99))
                .andExpect(jsonPath("$.charge.serviceFee").value(1.50))
                .andExpect(jsonPath("$.charge.smallOrderSurcharge").value(Double.parseDouble(surcharge)))
                .andExpect(jsonPath("$.charge.total").value(Double.parseDouble(total)))
                .andExpect(jsonPath("$.orderLines[0].menuItemName").value("Original meal"))
                .andExpect(jsonPath("$.orderLines[0].quantity").value(quantity))
                .andExpect(jsonPath("$.orderLines[0].unitPrice").value(Double.parseDouble(unitPrice)));
        assertEquals(new BigDecimal(total), ledger.findByOrderId(order.getId()).orElseThrow().totalCharge());
    }

    @Test
    void replayingDeliveryAndReadingTheSummaryPreserveTheOriginalConfiguredCharge() throws Exception {
        Order order = deliver("12.35", 2);
        LedgerEntry original = ledger.findByOrderId(order.getId()).orElseThrow();
        List<OrderEvent> originalEvents = eventLog.findByOrderId(order.getId());
        OrderEvent delivered = originalEvents.stream().filter(event -> event.toStatus() == OrderStatus.DELIVERED)
                .findFirst().orElseThrow();

        billing.onEvent(delivered);
        billing.onEvent(delivered);
        assertEquals(original.charge(), billing.recordDeliveryCharge(order.getId()).orElseThrow());
        for (int i = 0; i < 2; i++) {
            mvc.perform(get("/api/prc/orders/{orderId}/summary", order.getId()).session(customer))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.totalCharge").value(30.19));
        }

        assertSame(original, ledger.findByOrderId(order.getId()).orElseThrow());
        assertEquals(originalEvents, eventLog.findByOrderId(order.getId()));
        assertEquals(5, originalEvents.size());
    }

    private Order deliver(String unitPrice, int quantity) {
        UUID restaurantId = UUID.randomUUID();
        UUID staffId = UUID.randomUUID();
        UUID itemId = UUID.randomUUID();
        MockHttpSession staff = session(staffId, UserRole.RESTAURANT_STAFF);
        restaurants.save(new Restaurant(restaurantId, staffId, "Configured diner " + restaurantId,
                RestaurantStatus.OPEN, 45.5, -73.5));
        menu.save(new MenuItem(itemId, restaurantId, "Original meal", new BigDecimal(unitPrice), MenuItemStatus.AVAILABLE));
        Order order = ordering.placeOrder(restaurantId, List.of(new OrdService.OrderLineRequest(itemId, quantity)), customer);
        assertNull(ledger.findByOrderId(order.getId()).orElseThrow().totalCharge());
        ordering.editMenuItem(restaurantId, itemId, "Changed meal", new BigDecimal("99.99"), staff);
        UserAccount courier = new UserAccount(UUID.randomUUID().toString(), "configured-courier@example.test", "hash", UserRole.COURIER);
        delivery.setDuty(courier, true);
        ordering.acceptOrder(restaurantId, order.getId(), staff);
        ordering.startNextPreparation(restaurantId, staff);
        ordering.markOrderReady(restaurantId, order.getId(), staff);
        delivery.accept(courier, order.getId());
        delivery.pickUp(courier, order.getId());
        assertNull(ledger.findByOrderId(order.getId()).orElseThrow().totalCharge());
        delivery.deliver(courier, order.getId());
        delivery.setDuty(courier, false);
        return order;
    }

    private static MockHttpSession session(UUID id, UserRole role) {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("authenticatedUser", new UserAccount(id.toString(), "configured-user@example.test", "hash", role));
        return session;
    }
}
