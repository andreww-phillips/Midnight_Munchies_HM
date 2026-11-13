import React, { useCallback, useEffect, useRef, useState } from 'react';
import { api } from '../../api';
import { useCommandLog } from './output_console/command_log';
import { formatWaiting, shortId } from './operator_format';
import './operator_dashboard_page.css';

export default function OperatorDashboard({ onSessionExpired }) {
  const { record } = useCommandLog();
  const [deliveries, setDeliveries] = useState(null);
  const [busyOrder, setBusyOrder] = useState(null);
  const [error, setError] = useState('');
  const [notice, setNotice] = useState('');
  const requestBusy = useRef(false);
  const mounted = useRef(false);

  const refresh = useCallback(async () => {
    if (requestBusy.current) return;
    requestBusy.current = true;
    try {
      const current = await api.operatorDeliveries();
      if (mounted.current) {
        setDeliveries(current);
        setError('');
      }
    } catch (loadError) {
      if (mounted.current) {
        setError(loadError.message);
        if (loadError.status === 401) onSessionExpired?.('Your session expired. Please log in again.');
      }
    } finally {
      requestBusy.current = false;
    }
  }, [onSessionExpired]);

  useEffect(() => {
    mounted.current = true;
    refresh();
    const timer = window.setInterval(refresh, 4000);
    return () => {
      mounted.current = false;
      window.clearInterval(timer);
    };
  }, [refresh]);

  const reassign = async (orderId) => {
    if (busyOrder) return;
    setBusyOrder(orderId);
    setError('');
    setNotice('');
    try {
      const result = await api.reassignDelivery(orderId);
      const message = result.queued
        ? `No idle courier available; order #${shortId(orderId)} is waiting in the dispatch queue.`
        : `Order #${shortId(orderId)} offered to courier ${shortId(result.replacement.courierId)}.`;
      if (mounted.current) setNotice(message);
      record({ action: 'Reassign stalled order', outcome: 'success', orderId, status: result.queued ? 'QUEUED' : 'OFFERED', message });
      await refresh();
    } catch (commandError) {
      if (mounted.current) setError(commandError.message);
      record({ action: 'Reassign stalled order', outcome: 'error', orderId, httpStatus: commandError.status, message: commandError.message });
      if (commandError.status === 401) onSessionExpired?.('Your session expired. Please log in again.');
    } finally {
      if (mounted.current) setBusyOrder(null);
    }
  };

  return (
    <section className="operator-dashboard" aria-label="Delivery management">
      <div className="section-heading">
        <div>
          <h2>Delivery management</h2>
          <p className="muted">Reassign an order whose courier stopped responding. The next courier is chosen by the dispatch rule.</p>
        </div>
        <button className="secondary-button" type="button" disabled={Boolean(busyOrder)} onClick={refresh}>
          Refresh
        </button>
      </div>
      {error && <p role="alert">{error}</p>}
      {notice && <p role="status">{notice}</p>}
      {!deliveries && !error && <p role="status">Loading deliveries...</p>}
      {deliveries && (
        <>
          {deliveries.active.length === 0 ? (
            <p className="operator-empty">No active deliveries.</p>
          ) : (
            <div className="operator-table-wrap">
              <table className="operator-table">
                <thead>
                  <tr><th>Order</th><th>Courier</th><th>Status</th><th>Waiting</th><th aria-label="Actions" /></tr>
                </thead>
                <tbody>
                  {deliveries.active.map((delivery) => (
                    <tr key={delivery.orderId}>
                      <td title={delivery.orderId}>#{shortId(delivery.orderId)}</td>
                      <td title={delivery.courierId}>{shortId(delivery.courierId)}</td>
                      <td><span className="status-pill">{delivery.status.replaceAll('_', ' ')}</span></td>
                      <td>{formatWaiting(delivery.waitingSeconds)}</td>
                      <td>
                        <button className="primary-button" type="button"
                          disabled={!delivery.reassignable || Boolean(busyOrder)}
                          title={delivery.reassignable ? undefined : 'Picked-up orders cannot be reassigned'}
                          onClick={() => reassign(delivery.orderId)}>
                          {busyOrder === delivery.orderId ? 'Reassigning...' : 'Reassign'}
                        </button>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
          <h3>Dispatch queue</h3>
          {deliveries.queued.length === 0 ? (
            <p className="operator-empty">No orders waiting for a courier.</p>
          ) : (
            <ol className="operator-queue">
              {deliveries.queued.map((orderId) => <li key={orderId} title={orderId}>Order #{shortId(orderId)}</li>)}
            </ol>
          )}
        </>
      )}
    </section>
  );
}
