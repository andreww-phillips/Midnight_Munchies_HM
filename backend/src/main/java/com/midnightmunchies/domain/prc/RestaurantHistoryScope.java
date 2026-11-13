package com.midnightmunchies.domain.prc;

import java.util.List;
import java.util.Objects;

/** Restaurant staff only see entries for their own restaurant; with no restaurant they see nothing. */
public final class RestaurantHistoryScope implements HistoryScope {

	@Override
	public <E extends HistoryEntry> List<E> filter(List<E> entries, HistorySession session) {
		Objects.requireNonNull(entries, "entries must not be null");
		Objects.requireNonNull(session, "session must not be null");
		if (session.restaurantId() == null) {
			return List.of();
		}
		return entries.stream()
				.filter(entry -> session.restaurantId().equals(entry.restaurantId()))
				.toList();
	}
}
