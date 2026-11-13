package com.midnightmunchies.application.prc;

import com.midnightmunchies.domain.prc.HistoryEntry;
import com.midnightmunchies.domain.prc.HistorySession;
import com.midnightmunchies.domain.reg.UserRole;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class HistoryServiceTests {

    private record Entry(UUID customerId, UUID restaurantId, UUID courierId) implements HistoryEntry {
    }

    private final HistoryService service = new HistoryService();

    private final UUID customer = UUID.randomUUID();
    private final UUID restaurant = UUID.randomUUID();
    private final UUID courier = UUID.randomUUID();
    private final Entry mine = new Entry(customer, restaurant, courier);
    private final Entry someoneElses = new Entry(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
    private final List<Entry> ledger = List.of(mine, someoneElses);

    @Test
    void customerRoleUsesTheCustomerScope() {
        HistorySession session = new HistorySession(customer, UserRole.CUSTOMER, null);

        assertEquals(List.of(mine), service.getHistory(session, ledger));
    }

    @Test
    void restaurantStaffRoleUsesTheRestaurantScope() {
        HistorySession session = new HistorySession(UUID.randomUUID(), UserRole.RESTAURANT_STAFF, restaurant);

        assertEquals(List.of(mine), service.getHistory(session, ledger));
    }

    @Test
    void courierRoleUsesTheCourierScope() {
        HistorySession session = new HistorySession(courier, UserRole.COURIER, null);

        assertEquals(List.of(mine), service.getHistory(session, ledger));
    }

    @Test
    void operatorRoleSeesEverything() {
        HistorySession session = new HistorySession(UUID.randomUUID(), UserRole.PLATFORM_OPERATOR, null);

        assertEquals(ledger, service.getHistory(session, ledger));
    }

    @Test
    void everyRoleHasAScope() {
        for (UserRole role : UserRole.values()) {
            HistorySession session = new HistorySession(UUID.randomUUID(), role, null);
            service.getHistory(session, ledger);
        }
    }

    @Test
    void roleIsTakenFromTheSessionNotTheEntries() {
        HistorySession customerWhoIsAlsoListedAsCourier = new HistorySession(courier, UserRole.CUSTOMER, null);

        assertEquals(List.of(), service.getHistory(customerWhoIsAlsoListedAsCourier, ledger));
    }

    @Test
    void missingSessionIsRejected() {
        assertThrows(NullPointerException.class, () -> service.getHistory(null, ledger));
    }
}
