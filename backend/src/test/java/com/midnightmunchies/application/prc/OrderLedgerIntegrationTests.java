package com.midnightmunchies.application.prc;

import com.midnightmunchies.application.dlv.CourierService;
import com.midnightmunchies.application.ord.AuthorizedOrdService;
import com.midnightmunchies.application.ord.OrdService;
import com.midnightmunchies.application.ord.OrderNotFoundException;
import com.midnightmunchies.application.reg.AuthenticationRequiredException;
import com.midnightmunchies.domain.dlv.CourierState;
import com.midnightmunchies.domain.ord.IllegalOrderTransitionException;
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
import com.midnightmunchies.domain.prc.Fare;
import com.midnightmunchies.domain.prc.OrderSummary;
import com.midnightmunchies.domain.reg.UserAccount;
import com.midnightmunchies.domain.reg.UserRole;
import com.midnightmunchies.technicalservices.LedgerRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties = "spring.application.name=ledger-integration-test")
class OrderLedgerIntegrationTests {

    @Autowired private AuthorizedOrdService ordering;
    @Autowired private CourierService delivery;
    @Autowired private LedgerRepository ledger;
    @Autowired private BillingService billing;
    @Autowired private PricingService pricing;
    @Autowired private RestaurantRepository restaurants;
    @Autowired private MenuItemRepository menu;
    @Autowired private OrderEventLog eventLog;

    private final UUID restaurantId = UUID.randomUUID();
    private final UUID itemId = UUID.randomUUID();
    private final UUID customerId = UUID.randomUUID();
    private final UUID staffId = UUID.randomUUID();
    private final MockHttpSession customer = session(customerId, UserRole.CUSTOMER);
    private final MockHttpSession staff = session(staffId, UserRole.RESTAURANT_STAFF);

    @BeforeEach
    void setUp() {
        restaurants.save(new Restaurant(restaurantId, staffId, "Ledger diner " + restaurantId,
                RestaurantStatus.OPEN, 45.5, -73.5));
        menu.save(new MenuItem(itemId, restaurantId, "Poutine", new BigDecimal("12.35"), MenuItemStatus.AVAILABLE));
    }

    @Test
    void existingServicesRecordAndPriceDeliveryUsingOriginalPurchaseFacts() {
        Order order = place();
        LedgerEntry placed = record(order);
        assertEquals(OrderStatus.PLACED, placed.status());
        assertEquals(customerId, placed.customerId());
        assertEquals(Map.of(OrderStatus.PLACED, order.getCreatedAt()), placed.timestamps());
        assertTrue(eventLog.findByOrderId(order.getId()).isEmpty());
        assertTrue(billing.findSummary(order.getId()).isEmpty());
        ordering.editMenuItem(restaurantId, itemId, "Loaded Poutine", new BigDecimal("18.00"), staff);

        UUID courierId = UUID.randomUUID();
        UserAccount courier = new UserAccount(courierId.toString(), "ledger-courier@example.test", "hash", UserRole.COURIER);
        delivery.setDuty(courier, true);
        ordering.acceptOrder(restaurantId, order.getId(), staff);
        assertEquals(OrderStatus.ACCEPTED, record(order).status());
        assertNotNull(order.getAcceptedAt());
        ordering.startNextPreparation(restaurantId, staff);
        ordering.markOrderReady(restaurantId, order.getId(), staff);
        assertEquals(OrderStatus.READY, record(order).status());
        assertEquals(order.getId(), delivery.view(courier).assignment().order().getId());
        delivery.accept(courier, order.getId());
        assertNull(record(order).courierId());
        delivery.pickUp(courier, order.getId());
        assertEquals(courierId, record(order).courierId());
        assertTrue(billing.findSummary(order.getId()).isEmpty());
        delivery.deliver(courier, order.getId());

        LedgerEntry completed = record(order);
        assertEquals(OrderStatus.DELIVERED, completed.status());
        assertEquals(OrderStatus.DELIVERED, order.getStatus());
        assertEquals(CourierState.IDLE, delivery.view(courier).state());
        assertEquals(courierId, completed.courierId());
        Fare expected = pricing.calculateFare(order);
        assertEquals(expected.total(), completed.totalCharge());
        assertNotNull(completed.charge());
        assertEquals(expected.subtotal(), completed.charge().itemSubtotal());
        assertEquals(expected.deliveryFee(), completed.charge().deliveryFee());
        assertEquals(expected.serviceFee(), completed.charge().serviceFee());
        assertEquals(expected.smallOrderSurcharge(), completed.charge().smallOrderSurcharge());
        assertEquals("Poutine", completed.orderLines().getFirst().getMenuItemName());
        assertEquals(new BigDecimal("12.35"), completed.orderLines().getFirst().getUnitPrice());
        List<OrderEvent> events = eventLog.findByOrderId(order.getId());
        assertEquals(List.of(OrderStatus.ACCEPTED, OrderStatus.PREPARING, OrderStatus.READY,
                OrderStatus.PICKED_UP, OrderStatus.DELIVERED), events.stream().map(OrderEvent::toStatus).toList());
        events.forEach(event -> assertEquals(event.occurredAt(), completed.timestamps().get(event.toStatus())));
        assertEquals(6, completed.timestamps().size());
        OrderSummary summary = billing.findSummary(order.getId()).orElseThrow();
        assertEquals(completed.orderId(), summary.getOrderId());
        assertEquals(completed.timestamps().get(OrderStatus.DELIVERED), summary.getDeliveredAt());
        assertEquals("Poutine", summary.getOrderLines().getFirst().getMenuItemName());
        assertEquals(new BigDecimal("12.35"), summary.getOrderLines().getFirst().getUnitPrice());
        assertFalse(summary.isPricingPending());
        assertEquals(expected.total(), summary.getTotalCharge());
        assertEquals(completed.charge(), summary.getCharge());
        assertEquals(OrderStatus.PLACED, placed.status());
        assertEquals(1, placed.timestamps().size());
        delivery.setDuty(courier, false);
    }

    @Test
    void rejectionAndCancellationRemainUnchargedOrdersInTheLedger() {
        Order rejected = place();
        ordering.rejectOrder(restaurantId, rejected.getId(), "Kitchen closed", staff);
        Order cancelled = place();
        ordering.cancelOrder(cancelled.getId(), customer);

        assertEquals(OrderStatus.REJECTED, record(rejected).status());
        assertEquals(OrderStatus.CANCELLED, record(cancelled).status());
        for (Order order : List.of(rejected, cancelled)) {
            LedgerEntry entry = record(order);
            assertNull(entry.totalCharge());
            assertNull(entry.courierId());
            assertTrue(billing.findSummary(order.getId()).isEmpty());
            assertEquals(eventLog.findByOrderId(order.getId()).getFirst().occurredAt(),
                    entry.timestamps().get(entry.status()));
        }
    }

    @Test
    void refusedCommandsDoNotChangeTheLedgerOrExistingEventHistory() {
        Order order = place();
        LedgerEntry original = record(order);

        assertThrows(IllegalOrderTransitionException.class,
                () -> ordering.markOrderReady(restaurantId, order.getId(), staff));
        assertThrows(OrderNotFoundException.class,
                () -> ordering.cancelOrder(order.getId(), session(UUID.randomUUID(), UserRole.CUSTOMER)));

        assertEquals(original, record(order));
        assertEquals(OrderStatus.PLACED, order.getStatus());
        assertTrue(eventLog.findByOrderId(order.getId()).isEmpty());
    }

    @Test
    void invalidOrUnauthenticatedPlacementCreatesNoLedgerRecord() {
        int before = ledger.findAll().size();
        List<OrdService.OrderLineRequest> lines = List.of(new OrdService.OrderLineRequest(itemId, 1));

        assertThrows(AuthenticationRequiredException.class,
                () -> ordering.placeOrder(restaurantId, lines, new MockHttpSession()));
        ordering.changeMenuItemAvailability(restaurantId, itemId, MenuItemStatus.SOLD_OUT, staff);
        assertThrows(IllegalArgumentException.class, this::place);

        assertEquals(before, ledger.findAll().size());
    }

    private Order place() {
        return ordering.placeOrder(restaurantId, List.of(new OrdService.OrderLineRequest(itemId, 2)), customer);
    }

    private LedgerEntry record(Order order) {
        return ledger.findByOrderId(order.getId()).orElseThrow();
    }

    private static MockHttpSession session(UUID id, UserRole role) {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("authenticatedUser", new UserAccount(id.toString(), "ledger-user@example.test", "hash", role));
        return session;
    }
}
