package com.midnightmunchies.domain.prc;

import java.util.UUID;

/** The parts of a ledger entry that decide who may see it in an order history. */
public interface HistoryEntry {

	UUID customerId();

	UUID restaurantId();

	/** The courier who carried the delivery, or null when no courier was involved. */
	UUID courierId();
}
