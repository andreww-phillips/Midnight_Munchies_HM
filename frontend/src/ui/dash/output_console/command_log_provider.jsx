import React, { useCallback, useMemo, useRef, useState } from 'react';
import { CommandLogContext } from './command_log';

export function CommandLogProvider({ children }) {
  const scope = useRef({ identity: null, generation: 0 });
  const sequence = useRef(0);
  const [history, setHistory] = useState({ generation: 0, entries: [] });

  const makeEntry = useCallback(({ action, outcome, message, restaurantId, orderId, status, httpStatus }) => ({
    // Only explicitly selected display fields are kept; never store request/error objects.
    id: ++sequence.current, timestamp: new Date().toISOString(),
    action, outcome, message, restaurantId, orderId, status, httpStatus
  }), []);

  const record = useCallback((details) => {
    // Async handlers retain their original account scope, even after logout/login.
    if (scope.current.generation !== history.generation) return;
    const entry = makeEntry(details);
    setHistory((current) => current.generation === history.generation
      ? { ...current, entries: [...current.entries, entry].slice(-100) } : current);
  }, [history.generation, makeEntry]);

  const changeIdentity = useCallback((identity, entry) => {
    if (scope.current.identity === identity && !entry) return;
    const generation = scope.current.generation + 1;
    scope.current = { identity, generation };
    setHistory({ generation, entries: entry ? [makeEntry(entry)] : [] });
  }, [makeEntry]);

  const value = useMemo(() => ({ entries: history.entries, record, changeIdentity }), [history.entries, record, changeIdentity]);
  return <CommandLogContext.Provider value={value}>{children}</CommandLogContext.Provider>;
}

