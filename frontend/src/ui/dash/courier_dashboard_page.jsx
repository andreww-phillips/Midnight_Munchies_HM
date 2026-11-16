import React, { useCallback, useEffect, useRef, useState } from 'react';
import { api } from '../../api';
import { formatPrice } from './menu_page/menu_format';
import { useCommandLog } from './output_console/command_log';
import './courier_dashboard_page.css';

function availableActions(dashboard) {
  const status = dashboard?.assignment?.assignment.status;
  const orderStatus = dashboard?.assignment?.order.status;
  return {
    duty: Boolean(dashboard) && (dashboard.onDuty ? dashboard.state === 'IDLE' : dashboard.state === 'OFFLINE'),
    accept: dashboard?.onDuty && dashboard.state === 'IDLE' && status === 'OFFERED' && orderStatus === 'READY',
    decline: dashboard?.onDuty && dashboard.state === 'IDLE' && status === 'OFFERED',
    pickup: dashboard?.onDuty && dashboard.state === 'ASSIGNED' && status === 'ACCEPTED' && orderStatus === 'READY',
    deliver: dashboard?.onDuty && dashboard.state === 'CARRYING' && status === 'PICKED_UP' && orderStatus === 'PICKED_UP'
  };
}

function DeliveryCard({ assignment, onAction, busy, allowed }) {
  const { order } = assignment;
  const status = assignment.assignment.status;

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
      <div className="courier-delivery-actions">
        {status === 'OFFERED' && <>
          <button className="primary-button" type="button" disabled={busy || !allowed.accept}
            onClick={() => onAction('accept', order.id)}>Accept offer</button>
          <button className="secondary-button" type="button" disabled={busy || !allowed.decline}
            onClick={() => onAction('decline', order.id)}>Decline offer</button>
        </>}
        <button className="primary-button" type="button" disabled={busy || !allowed.pickup}
          onClick={() => onAction('pickup', order.id)}>Confirm pickup</button>
        <button className="primary-button" type="button" disabled={busy || !allowed.deliver}
          onClick={() => onAction('deliver', order.id)}>Confirm delivery</button>
      </div>
    </article>
  );
}

export default function CourierDashboard({ onSessionExpired }) {
  const { record } = useCommandLog();
  const [dashboard, setDashboard] = useState(null);
  const [loading, setLoading] = useState(true);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const [unavailable, setUnavailable] = useState(false);
  const requestBusy = useRef(false);
  const actionBusy = useRef(false);
  const mounted = useRef(false);

  const refresh = useCallback(async (afterCommand = false) => {
    if (requestBusy.current || (actionBusy.current && !afterCommand)) return;
    requestBusy.current = true;
    setLoading(true);
    try {
      const current = await api.courierDashboard();
      if (mounted.current) {
        setDashboard(current);
        setError('');
        setUnavailable(false);
      }
    } catch (loadError) {
      if (mounted.current) {
        setError(loadError.message);
        setUnavailable(true);
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

  const allowed = availableActions(dashboard);
  const controlsBusy = busy || loading || unavailable;

  const runAction = async (action, orderId) => {
    if (controlsBusy || actionBusy.current || requestBusy.current || !allowed[action]) return;
    if (action !== 'duty' && dashboard.assignment?.order.id !== orderId) return;
    actionBusy.current = true;
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
      if (!mounted.current) return;
      record({ action: labels[action], outcome: 'success', orderId, message: 'Delivery status updated.' });
      await refresh(true);
    } catch (commandError) {
      record({ action: labels[action], outcome: 'error', orderId, httpStatus: commandError.status, message: commandError.message });
      if (!mounted.current) return;
      if ([404, 409].includes(commandError.status)) await refresh(true);
      if (!mounted.current) return;
      setError(commandError.message);
      if (commandError.status === 401) onSessionExpired?.('Your session expired. Please log in again.');
    } finally {
      actionBusy.current = false;
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
          disabled={controlsBusy || !allowed.duty} onClick={() => runAction('duty')}>
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
            <DeliveryCard assignment={dashboard.assignment} onAction={runAction} busy={controlsBusy} allowed={allowed} />
          ) : (
            <section className="courier-empty-state">
              <h3>No active delivery</h3>
              <p>{dashboard.onDuty ? 'You are waiting for an offer.' : 'Go on duty to receive delivery offers.'}</p>
            </section>
          )}
          <button className="secondary-button" type="button" disabled={busy || loading} onClick={() => refresh()}>
            Refresh status
          </button>
        </>
      )}
    </main>
  );
}
