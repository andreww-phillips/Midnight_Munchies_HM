import React, { useCallback, useEffect, useRef, useState } from 'react';
import { api } from '../../api';
import { formatPrice } from './menu_page/menu_format';
import { useCommandLog } from './output_console/command_log';
import './courier_dashboard_page.css';

function DeliveryCard({ assignment, onAction, busy }) {
  const { order } = assignment;
  const status = assignment.assignment.status;
  const action = {
    OFFERED: ['Accept offer', 'accept', 'Decline offer', 'decline'],
    ACCEPTED: ['Confirm pickup', 'pickup'],
    PICKED_UP: ['Confirm delivery', 'deliver'],
  }[status];

  return (
    <article className="courier-delivery-card">
      <div className="courier-delivery-heading">
        <h3>Order #{order.id}</h3>
        <span className="status-pill">{status.replaceAll('_', ' ')}</span>
      </div>
      <p className="muted">Restaurant: {order.restaurantId}</p>
      <ul>
        {order.orderLines.map((line, index) => (
          <li key={`${line.menuItemId}-${index}`}>
            {line.quantity} × {line.menuItemName || line.menuItemId} · {formatPrice(line.unitPrice)} each
          </li>
        ))}
      </ul>
      {action && (
        <div className="courier-delivery-actions">
          <button className="primary-button" type="button" disabled={busy}
            onClick={() => onAction(action[1], order.id)}>
            {action[0]}
          </button>
          {action?.[2] && (
        <button className="secondary-button" type="button" disabled={busy}
          onClick={() => onAction(action[3], order.id)}>
          {action[2]}
        </button>
      )}
        </div>
      )}
    </article>
  );
}

export default function CourierDashboard({ onSessionExpired }) {
  const { record } = useCommandLog();
  const [dashboard, setDashboard] = useState(null);
  const [loading, setLoading] = useState(true);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const requestBusy = useRef(false);
  const mounted = useRef(false);

  const refresh = useCallback(async () => {
    if (requestBusy.current) return;
    requestBusy.current = true;
    try {
      const current = await api.courierDashboard();
      if (mounted.current) {
        setDashboard(current);
        setError('');
      }
    } catch (loadError) {
      if (mounted.current) {
        setError(loadError.message);
        if (loadError.status === 401) onSessionExpired?.('Your session expired. Please log in again.');
      }
    } finally {
      requestBusy.current = false;
      if (mounted.current) setLoading(false);
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

  const runAction = async (action, orderId) => {
    if (busy) return;
    setBusy(true);
    setError('');
    const actions = {
      duty: () => api.setCourierDuty(!dashboard.onDuty),
      accept: () => api.acceptDelivery(orderId),
      decline: () => api.declineDelivery(orderId),
      pickup: () => api.pickUpDelivery(orderId),
      deliver: () => api.deliverOrder(orderId)
    };
    const labels = {
      duty: dashboard.onDuty ? 'Go off duty' : 'Go on duty',
      accept: 'Accept delivery offer',
      decline: 'Decline delivery offer',
      pickup: 'Confirm pickup',
      deliver: 'Confirm delivery'
    };
    try {
      await actions[action]();
      record({ action: labels[action], outcome: 'success', orderId, message: 'Delivery status updated.' });
      await refresh();
    } catch (commandError) {
      setError(commandError.message);
      record({ action: labels[action], outcome: 'error', orderId, httpStatus: commandError.status, message: commandError.message });
      if (commandError.status === 401) onSessionExpired?.('Your session expired. Please log in again.');
    } finally {
      if (mounted.current) setBusy(false);
    }
  };

  return (
    <main className="courier-dashboard">
      <div className="section-heading">
        <div>
          <h2>Courier dashboard</h2>
          <p className="muted">Offers are available only while you are on duty and idle.</p>
        </div>
        <button className={dashboard?.onDuty ? 'secondary-button' : 'primary-button'} type="button"
          disabled={!dashboard || busy} onClick={() => runAction('duty')}>
          {dashboard?.onDuty ? 'Go off duty' : 'Go on duty'}
        </button>
      </div>
      {loading && !dashboard && <p role="status">Loading courier status...</p>}
      {error && <p role="alert">{error}</p>}
      {dashboard && (
        <>
          <p className="courier-state" role="status">
            Status: <strong>{dashboard.state.replaceAll('_', ' ')}</strong>
          </p>
          {dashboard.assignment ? (
            <DeliveryCard assignment={dashboard.assignment} onAction={runAction} busy={busy} />
          ) : (
            <section className="courier-empty-state">
              <h3>No active delivery</h3>
              <p>{dashboard.onDuty ? 'You are waiting for an offer.' : 'Go on duty to receive delivery offers.'}</p>
            </section>
          )}
          <button className="secondary-button" type="button" disabled={busy} onClick={refresh}>
            Refresh status
          </button>
        </>
      )}
    </main>
  );
}
