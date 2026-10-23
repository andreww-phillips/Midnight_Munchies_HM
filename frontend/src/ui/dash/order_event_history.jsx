import React, { useEffect, useRef, useState } from 'react';
import { api } from '../../api';
import { useCommandLog } from './output_console/command_log';
import './output_console/output_console.css';

function EventHistory({ orderId, restaurantId, disabled }) {
  const { record } = useCommandLog();
  const [open, setOpen] = useState(false);
  const [events, setEvents] = useState([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');
  const mounted = useRef(false);
  const busy = useRef(false);
  useEffect(() => {
    mounted.current = true;
    return () => { mounted.current = false; };
  }, []);

  const load = async () => {
    if (busy.current || disabled) return;
    busy.current = true;
    setOpen(true);
    setLoading(true);
    setError('');
    try {
      const loaded = await api.orderEvents(orderId);
      if (!mounted.current) return;
      const relevant = loaded.filter((event) => event.orderId === orderId && (!restaurantId || event.restaurantId === restaurantId));
      setEvents([...new Map(relevant.map((event) => [event.eventId, event])).values()]);
      record({ action: 'Load order events', outcome: 'success', orderId, restaurantId, message: 'Recorded order transitions loaded.' });
    } catch (loadError) {
      if (!mounted.current) return;
      setError(loadError.message);
      record({ action: 'Load order events', outcome: 'error', orderId, restaurantId, httpStatus: loadError.status, message: loadError.message });
    } finally {
      if (mounted.current) { setLoading(false); busy.current = false; }
    }
  };

  return (
    <div className="order-event-history">
      <button className="link-button" type="button" disabled={loading || disabled} onClick={load}>
        {open ? 'Refresh event history' : 'View event history'}
      </button>
      {open && <section aria-label={`Event history for order ${orderId}`} aria-busy={loading}>
        <h5>Recorded transitions for order #{orderId}</h5>
        {loading && <p role="status">Loading event history...</p>}
        {error && <p role="alert">Could not load event history: {error}</p>}
        {!loading && !error && (events.length === 0 ? <p>No recorded transitions yet.</p> : <ol>
          {events.map((event) => <li key={event.eventId}>
            <p><strong>{event.fromStatus} → {event.toStatus}</strong> · {event.actorRole}</p>
            <p>Event ID: {event.eventId}</p>
            <time dateTime={event.occurredAt}>{new Date(event.occurredAt).toLocaleString()}</time>
          </li>)}
        </ol>)}
      </section>}
    </div>
  );
}

export default function OrderEventHistory({ orderId, restaurantId, disabled = false }) {
  return <EventHistory key={`${restaurantId}:${orderId}`} orderId={orderId} restaurantId={restaurantId} disabled={disabled} />;
}
