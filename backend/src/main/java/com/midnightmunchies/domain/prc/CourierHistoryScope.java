package com.midnightmunchies.domain.prc;

import java.util.List;
import java.util.Objects;

/** A courier only sees the deliveries they carried. */
public final class CourierHistoryScope implements HistoryScope {

	@Override
	public <E extends HistoryEntry> List<E> filter(List<E> entries, HistorySession session) {
		Objects.requireNonNull(entries, "entries must not be null");
		Objects.requireNonNull(session, "session must not be null");
		return entries.stream()
				.filter(entry -> session.userId().equals(entry.courierId()))
				.toList();
	}
}
