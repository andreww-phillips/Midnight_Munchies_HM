package com.midnightmunchies.domain.prc;

import java.util.List;
import java.util.Objects;

/** The operator sees every entry, including refunds (R-PRC-05). */
public final class OperatorHistoryScope implements HistoryScope {

	@Override
	public <E extends HistoryEntry> List<E> filter(List<E> entries, HistorySession session) {
		Objects.requireNonNull(entries, "entries must not be null");
		Objects.requireNonNull(session, "session must not be null");
		return List.copyOf(entries);
	}
}
