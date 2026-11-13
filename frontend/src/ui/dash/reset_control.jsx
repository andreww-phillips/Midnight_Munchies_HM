import React, { useState } from 'react';
import { api } from '../../api';
import { useCommandLog } from './output_console/command_log';

export default function ResetControl({ onReset, onSessionExpired, confirm = (message) => window.confirm(message) }) {
  const { record } = useCommandLog();
  const [busy, setBusy] = useState(false);

  const reset = async () => {
    if (!confirm('Restore the initial scenario? All orders and registered accounts will be removed.')) return;
    setBusy(true);
    try {
      await api.resetSimulation();
      record({ action: 'Reset simulation', outcome: 'success', message: 'Initial scenario restored.' });
      onReset?.();
    } catch (resetError) {
      if (resetError.status === 401) {
        onSessionExpired?.(resetError.message);
        return;
      }
      record({ action: 'Reset simulation', outcome: 'error', message: resetError.message, httpStatus: resetError.status });
    } finally {
      setBusy(false);
    }
  };

  return (
    <button className="secondary-button reset-button" type="button" disabled={busy} onClick={reset}>
      {busy ? 'Resetting...' : 'Reset'}
    </button>
  );
}
