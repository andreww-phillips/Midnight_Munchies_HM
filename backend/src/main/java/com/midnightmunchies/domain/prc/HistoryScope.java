package com.midnightmunchies.domain.prc;

import java.util.List;

/**
 * Strategy for R-PRC-04: each role sees a different part of the same ledger.
 * Every implementation holds one role's visibility rule.
 */
public interface HistoryScope {

	<E extends HistoryEntry> List<E> filter(List<E> entries, HistorySession session);
}
