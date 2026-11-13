package com.midnightmunchies.domain.prc;

import java.util.UUID;

record TestHistoryEntry(UUID customerId, UUID restaurantId, UUID courierId) implements HistoryEntry {
}
