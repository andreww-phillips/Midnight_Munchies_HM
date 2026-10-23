import React from 'react';
import { useCommandLog } from './command_log';
import './output_console.css';

export default function OutputConsole() {
  const { entries } = useCommandLog();
  return (
    <details className="output-console" open>
      <summary>Output console ({entries.length})</summary>
      <p className="muted">Temporary command history for this session. Recorded order transitions are shown separately in each order's event history.</p>
      <div role="log" aria-label="Command output" aria-relevant="additions">
        {entries.length === 0 ? <p>No commands yet.</p> : <ol className="console-entries">
          {entries.map((entry) => <li key={entry.id} className={`console-entry console-entry--${entry.outcome}`}>
            <div className="console-entry-heading">
              <strong>{entry.outcome === 'success' ? 'Success' : 'Error'} · {entry.action}</strong>
              <time dateTime={entry.timestamp}>{new Date(entry.timestamp).toLocaleTimeString()}</time>
            </div>
            <p>{entry.message}</p>
            {(entry.orderId || entry.restaurantId || entry.status || entry.httpStatus) && <p className="muted">
              {[entry.orderId && `Order #${entry.orderId}`, entry.restaurantId && `Restaurant #${entry.restaurantId}`,
                entry.status && `Status: ${entry.status}`, entry.httpStatus && `HTTP ${entry.httpStatus}`].filter(Boolean).join(' · ')}
            </p>}
          </li>)}
        </ol>}
      </div>
    </details>
  );
}
