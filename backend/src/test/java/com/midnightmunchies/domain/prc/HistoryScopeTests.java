package com.midnightmunchies.domain.prc;

import com.midnightmunchies.domain.reg.UserRole;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HistoryScopeTests {

	private final UUID customerA = UUID.randomUUID();
	private final UUID customerB = UUID.randomUUID();
	private final UUID restaurantA = UUID.randomUUID();
	private final UUID restaurantB = UUID.randomUUID();
	private final UUID courierA = UUID.randomUUID();
	private final UUID courierB = UUID.randomUUID();

	private final TestHistoryEntry aToA = new TestHistoryEntry(customerA, restaurantA, courierA);
	private final TestHistoryEntry aToB = new TestHistoryEntry(customerA, restaurantB, courierB);
	private final TestHistoryEntry bToA = new TestHistoryEntry(customerB, restaurantA, courierB);
	private final TestHistoryEntry undelivered = new TestHistoryEntry(customerB, restaurantB, null);
	private final List<TestHistoryEntry> ledger = List.of(aToA, aToB, bToA, undelivered);

	@Test
	void customerSeesOnlyTheirOwnOrders() {
		HistorySession session = new HistorySession(customerA, UserRole.CUSTOMER, null);

		assertEquals(List.of(aToA, aToB), new CustomerHistoryScope().filter(ledger, session));
	}

	@Test
	void customerWithNoOrdersSeesNothing() {
		HistorySession session = new HistorySession(UUID.randomUUID(), UserRole.CUSTOMER, null);

		assertTrue(new CustomerHistoryScope().filter(ledger, session).isEmpty());
	}

	@Test
	void restaurantSeesOnlyItsOwnEntries() {
		HistorySession session = new HistorySession(UUID.randomUUID(), UserRole.RESTAURANT_STAFF, restaurantA);

		assertEquals(List.of(aToA, bToA), new RestaurantHistoryScope().filter(ledger, session));
	}

	@Test
	void restaurantStaffWithoutARestaurantSeeNothing() {
		HistorySession session = new HistorySession(UUID.randomUUID(), UserRole.RESTAURANT_STAFF, null);

		assertTrue(new RestaurantHistoryScope().filter(ledger, session).isEmpty());
	}

	@Test
	void courierSeesOnlyDeliveriesTheyCarried() {
		HistorySession session = new HistorySession(courierB, UserRole.COURIER, null);

		assertEquals(List.of(aToB, bToA), new CourierHistoryScope().filter(ledger, session));
	}

	@Test
	void courierDoesNotSeeEntriesWithNoCourier() {
		HistorySession session = new HistorySession(UUID.randomUUID(), UserRole.COURIER, null);

		assertTrue(new CourierHistoryScope().filter(List.of(undelivered), session).isEmpty());
	}

	@Test
	void operatorSeesEveryEntry() {
		HistorySession session = new HistorySession(UUID.randomUUID(), UserRole.PLATFORM_OPERATOR, null);

		assertEquals(ledger, new OperatorHistoryScope().filter(ledger, session));
	}

	@Test
	void scopesDoNotChangeTheLedgerAndReturnUnmodifiableLists() {
		HistorySession operator = new HistorySession(UUID.randomUUID(), UserRole.PLATFORM_OPERATOR, null);
		HistorySession customer = new HistorySession(customerA, UserRole.CUSTOMER, null);

		List<TestHistoryEntry> all = new OperatorHistoryScope().filter(ledger, operator);
		List<TestHistoryEntry> mine = new CustomerHistoryScope().filter(ledger, customer);

		assertThrows(UnsupportedOperationException.class, all::clear);
		assertThrows(UnsupportedOperationException.class, mine::clear);
		assertEquals(4, ledger.size());
	}

	@Test
	void scopesRejectMissingArguments() {
		HistorySession session = new HistorySession(customerA, UserRole.CUSTOMER, null);
		HistoryScope[] scopes = {new CustomerHistoryScope(), new RestaurantHistoryScope(),
				new CourierHistoryScope(), new OperatorHistoryScope()};

		for (HistoryScope scope : scopes) {
			assertThrows(NullPointerException.class, () -> scope.filter(null, session));
			assertThrows(NullPointerException.class, () -> scope.filter(ledger, null));
		}
	}

	@Test
	void sessionRequiresUserIdAndRole() {
		assertThrows(NullPointerException.class, () -> new HistorySession(null, UserRole.CUSTOMER, null));
		assertThrows(NullPointerException.class, () -> new HistorySession(UUID.randomUUID(), null, null));
	}
}
