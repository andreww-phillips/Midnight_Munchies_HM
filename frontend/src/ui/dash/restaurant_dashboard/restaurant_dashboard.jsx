import React, { useEffect, useRef, useState } from 'react';
import { api } from '../../../api';
import { formatPrice } from '../menu_page/menu_format';
import './restaurant_dashboard.css';

function OrderCard({ order, disabled = false, onCommand }) {
  const [reason, setReason] = useState('');
  const [reasonError, setReasonError] = useState('');
  return (
    <article className="order-card">
      <div className="dashboard-order-heading">
        <h4>Order #{order.id}</h4>
        <span className="status-pill">{order.status}</span>
      </div>
      <ul>{order.orderLines.map((line, index) => (
        <li key={`${line.menuItemId}-${index}`}>
          {line.quantity} × {line.menuItemName || line.menuItemId} · {formatPrice(line.unitPrice)} each
        </li>
      ))}</ul>
      {order.rejectionReason && <p>Rejection reason: {order.rejectionReason}</p>}
      {onCommand && order.status === 'PLACED' && <div className="dashboard-order-actions">
        <button className="primary-button" type="button" disabled={disabled}
          onClick={() => onCommand('accept', order)}>Accept</button>
        <form onSubmit={(event) => {
          event.preventDefault();
          if (disabled) return;
          const trimmedReason = reason.trim();
          if (!trimmedReason) {
            setReasonError('A rejection reason is required.');
            return;
          }
          setReasonError('');
          onCommand('reject', order, trimmedReason);
        }}>
          <label>Rejection reason for order #{order.id}
            <input value={reason} disabled={disabled} onChange={(event) => {
              setReason(event.target.value);
              setReasonError('');
            }} />
          </label>
          {reasonError && <p role="alert">{reasonError}</p>}
          <button className="secondary-button" type="submit" disabled={disabled}>Reject</button>
        </form>
      </div>}
      {onCommand && order.status === 'PREPARING' && <button className="primary-button" type="button"
        disabled={disabled} onClick={() => onCommand('ready', order)}>Mark Ready</button>}
    </article>
  );
}

function OrderGroup({ title, orders, emptyMessage, queue = false, disabled, onCommand, children }) {
  const List = queue ? 'ol' : 'ul';
  return (
    <section className="dashboard-order-group" aria-label={title}>
      <h3>{title} ({orders.length})</h3>
      {children}
      {orders.length === 0 ? <p className="muted">{emptyMessage}</p> : (
        <List className={queue ? 'dashboard-queue' : 'dashboard-orders'}>
          {orders.map((order) => <li key={order.id}><OrderCard order={order} disabled={disabled} onCommand={onCommand} /></li>)}
        </List>
      )}
    </section>
  );
}

async function readDashboard(restaurantId) {
  const [orders, queue] = await Promise.all([api.orders(), api.preparationQueue(restaurantId)]);
  return {
    orders: orders.filter((order) => order.restaurantId === restaurantId),
    // Keep the server's FIFO order; only waiting orders belong in this list.
    queue: queue.filter((order) => order.restaurantId === restaurantId && order.status === 'ACCEPTED')
  };
}

function RestaurantOrders({ restaurant, onSessionExpired }) {
  const [data, setData] = useState({ orders: [], queue: [] });
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [refresh, setRefresh] = useState(0);
  const [pending, setPending] = useState(null);
  const [notice, setNotice] = useState('');
  const [confirmedOrder, setConfirmedOrder] = useState(null);
  const [loadFailed, setLoadFailed] = useState(false);
  const requestBusy = useRef(true);
  const mounted = useRef(false);

  useEffect(() => {
    mounted.current = true;
    return () => { mounted.current = false; };
  }, []);

  useEffect(() => {
    let active = true;
    readDashboard(restaurant.id)
      .then((snapshot) => {
        if (active) { setData(snapshot); setLoadFailed(false); }
      })
      .catch((loadError) => {
        if (!active) return;
        setError(loadError.message);
        setLoadFailed(true);
        if (loadError.status === 401) onSessionExpired?.('Your session expired. Please log in again.');
      })
      .finally(() => { if (active) { setLoading(false); requestBusy.current = false; } });
    return () => { active = false; };
  }, [restaurant.id, refresh, onSessionExpired]);

  const runCommand = async (action, order, reason) => {
    if (requestBusy.current || loadFailed) return;
    requestBusy.current = true;
    const operations = {
      accept: ['Accepting order...', () => api.acceptOrder(restaurant.id, order.id)],
      reject: ['Rejecting order...', () => api.rejectOrder(restaurant.id, order.id, reason)],
      next: ['Starting next preparation...', () => api.startNextPreparation(restaurant.id)],
      ready: ['Marking order ready...', () => api.markOrderReady(restaurant.id, order.id)]
    };
    const [message, operation] = operations[action];
    setPending(message);
    setError('');
    setNotice('');
    setConfirmedOrder(null);
    let successMessage = '';
    try {
      let updatedOrder;
      try {
        updatedOrder = await operation();
      } catch (commandError) {
        if (!mounted.current) return;
        setError(commandError.message);
        if (commandError.status === 401) {
          onSessionExpired?.('Your session expired. Please log in again.');
          return;
        }
        // A concurrent command may have changed this order or emptied the queue.
        if (commandError.status !== 409 && commandError.status !== 404) return;
      }
      if (!mounted.current) return;
      if (updatedOrder) {
        successMessage = `Order #${updatedOrder.id} is now ${updatedOrder.status}.`;
        setNotice(successMessage);
        setConfirmedOrder(updatedOrder);
      }
      setLoading(true);
      try {
        const snapshot = await readDashboard(restaurant.id);
        if (mounted.current) { setData(snapshot); setLoadFailed(false); }
      } catch (refreshError) {
        if (!mounted.current) return;
        setLoadFailed(true);
        setError((previous) => successMessage
          ? `The operation succeeded, but the dashboard could not refresh: ${refreshError.message} Refresh orders before another command.`
          : `${previous} Dashboard could not refresh: ${refreshError.message}`);
        if (refreshError.status === 401) onSessionExpired?.(
          `${successMessage ? successMessage + ' The operation succeeded, but the dashboard could not refresh. ' : ''}Your session expired. Please log in again.`
        );
      }
    } finally {
      if (mounted.current) {
        setLoading(false);
        setPending(null);
        requestBusy.current = false;
      }
    }
  };

  const busy = loading || pending !== null;

  return (
    <div aria-busy={busy}>
      <div className="section-heading">
        <h2>{restaurant.name} orders</h2>
        <button className="link-button" type="button" disabled={busy}
          onClick={() => {
            if (requestBusy.current) return;
            requestBusy.current = true;
            setLoading(true);
            setError('');
            setNotice('');
            setConfirmedOrder(null);
            setRefresh((version) => version + 1);
          }}>Refresh orders</button>
      </div>
      {loading && <p role="status">Loading restaurant orders and preparation queue...</p>}
      {pending && <p role="status">{pending}</p>}
      {notice && <p className="dashboard-success" role="status">{notice}</p>}
      {error && <p role="alert">{error}</p>}
      {confirmedOrder && (loading || loadFailed) && <section aria-label="Last updated order">
        <h3>Last updated order</h3><OrderCard order={confirmedOrder} />
      </section>}
      {!loading && !loadFailed && <>
        <OrderGroup title="Incoming orders" orders={data.orders.filter((order) => order.status === 'PLACED')}
          emptyMessage="No incoming orders." disabled={busy} onCommand={runCommand} />
        <OrderGroup title="Preparation queue" orders={data.queue} queue
          emptyMessage="No accepted orders waiting for preparation.">
          <button className="primary-button" type="button" disabled={busy || data.queue.length === 0}
            onClick={() => runCommand('next')}>Start next preparation</button>
        </OrderGroup>
        <OrderGroup title="Preparing" orders={data.orders.filter((order) => order.status === 'PREPARING')}
          emptyMessage="No orders currently preparing." disabled={busy} onCommand={runCommand} />
        <OrderGroup title="Ready" orders={data.orders.filter((order) => order.status === 'READY')}
          emptyMessage="No orders ready yet." />
        <OrderGroup title="Other orders" orders={data.orders.filter((order) =>
          !['PLACED', 'ACCEPTED', 'PREPARING', 'READY'].includes(order.status))}
          emptyMessage="No other orders." />
      </>}
    </div>
  );
}

export default function RestaurantDashboard({ restaurant, user, onBack, onSessionExpired }) {
  const canManage = user?.role === 'RESTAURANT_STAFF' && Boolean(user.id) && restaurant.ownerId === user.id;
  return (
    <section className="restaurant-dashboard">
      {onBack && <button type="button" onClick={onBack}>Back to restaurants</button>}
      {canManage ? (
        // Remount on identity/restaurant changes so previous data never appears in the new view.
        <RestaurantOrders key={`${user.id}:${restaurant.id}`} restaurant={restaurant} onSessionExpired={onSessionExpired} />
      ) : <p role="alert">Only owning restaurant staff can view this dashboard.</p>}
    </section>
  );
}
