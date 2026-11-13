import React, { useEffect, useState } from 'react';
import { api } from '../../../api';
import { useCommandLog } from '../output_console/command_log';
import { shortId } from '../operator_format';

export default function RestaurantDeliveries({ orderIds, refreshKey, onSessionExpired }) {
  const { record } = useCommandLog();
  const [active, setActive] = useState([]);
  const [error, setError] = useState('');
  const [busyOrder, setBusyOrder] = useState(null);
  const [version, setVersion] = useState(0);
  const idsKey = orderIds.join(',');

  useEffect(() => {
    if (!idsKey) return undefined;
    let current = true;
    api.operatorDeliveries()
      .then((view) => {
        if (!current) return;
        setActive(view?.active ?? []);
        setError('');
      })
      .catch((loadError) => {
        if (!current) return;
        if (loadError.status === 401) {
          onSessionExpired?.(loadError.message);
          return;
        }
        setError(loadError.message);
      });
    return () => { current = false; };
  }, [idsKey, refreshKey, version, onSessionExpired]);

  const reassign = async (orderId) => {
    if (busyOrder) return;
    setBusyOrder(orderId);
    setError('');
    try {
      const result = await api.reassignDelivery(orderId);
      const message = result.queued
        ? `No idle courier available; order #${shortId(orderId)} is waiting in the dispatch queue.`
        : `Order #${shortId(orderId)} offered to courier ${shortId(result.replacement.courierId)}.`;
      record({ action: 'Reassign stalled order', outcome: 'success', orderId, status: result.queued ? 'QUEUED' : 'OFFERED', message });
      setVersion((value) => value + 1);
    } catch (commandError) {
      setError(commandError.message);
      record({ action: 'Reassign stalled order', outcome: 'error', orderId, httpStatus: commandError.status, message: commandError.message });
      if (commandError.status === 401) onSessionExpired?.(commandError.message);
    } finally {
      setBusyOrder(null);
    }
  };

  const visible = idsKey ? active.filter((delivery) => orderIds.includes(delivery.orderId)) : [];
  if (visible.length === 0 && !error) return null;

  return (
    <div>
      <h4>Deliveries</h4>
      {error && <p role="alert">{error}</p>}
      {visible.length > 0 && (
        <ul className="details-deliveries" aria-label="Active deliveries">
          {visible.map((delivery) => (
            <li key={delivery.orderId}>
              <span>
                Order #{shortId(delivery.orderId)} · {delivery.status.replaceAll('_', ' ')} · courier {shortId(delivery.courierId)}
              </span>
              <button className="secondary-button" type="button"
                disabled={!delivery.reassignable || Boolean(busyOrder)}
                title={delivery.reassignable ? undefined : 'Picked-up orders cannot be reassigned'}
                onClick={() => reassign(delivery.orderId)}>
                {busyOrder === delivery.orderId ? 'Reassigning...' : 'Reassign'}
              </button>
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}
