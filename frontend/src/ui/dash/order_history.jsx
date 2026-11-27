import React, { useCallback, useEffect, useRef, useState } from 'react';
import { api } from '../../api';
import { formatPrice } from './menu_page/menu_format';
import OrderEventHistory from './order_event_history';
import OrderSummaryPage from './order_summary_page';
import { useCommandLog } from './output_console/command_log';
import { OrderReviewPanel, ReviewSummary } from './reviews';

import './order_history.css';

const titles = {
  CUSTOMER: 'Your orders',
  RESTAURANT_STAFF: 'Restaurant order history',
  COURIER: 'Your delivery history',
  PLATFORM_OPERATOR: 'All orders and billing'
};

function History({ refreshVersion, user, onSessionExpired, restaurants = [] }) {
  const { record } = useCommandLog();
  const [orders, setOrders] = useState([]);
  const [error, setError] = useState('');
  const [notice, setNotice] = useState('');
  const [loading, setLoading] = useState(true);
  const [revision, setRevision] = useState(0);
  const [busyOrder, setBusyOrder] = useState(null);
  const mounted = useRef(false);
  const requestVersion = useRef(0);
  const refundBusy = useRef(false);
  const cancelBusy = useRef(false);
  const canViewSummary = ['CUSTOMER', 'PLATFORM_OPERATOR'].includes(user.role);
  const operator = user.role === 'PLATFORM_OPERATOR';
  const reviewedRestaurantIds = user.role === 'RESTAURANT_STAFF'
    ? [...new Set(orders.filter((order) => ['DELIVERED', 'REFUNDED'].includes(order.status))
      .map((order) => order.restaurantId))]
    : [];
  useEffect(() => {
    mounted.current = true;
    return () => { mounted.current = false; requestVersion.current += 1; };
  }, []);

  const load = useCallback(async (logSuccess = true) => {
    const version = ++requestVersion.current;
    setLoading(true);
    setError('');
    try {
      const items = await api.orderHistory();
      if (!mounted.current || version !== requestVersion.current) return false;
      setOrders(items);
      setRevision((value) => value + 1);
      if (logSuccess) record({ action: 'Refresh orders', outcome: 'success', message: 'Order history refreshed.' });
      return true;
    } catch (loadError) {
      if (!mounted.current || version !== requestVersion.current) return false;
      setOrders([]);
      setError(loadError.message);
      record({ action: 'Refresh orders', outcome: 'error', message: loadError.message, httpStatus: loadError.status });
      if (loadError.status === 401) onSessionExpired?.('Your session expired. Please log in again.');
      return false;
    } finally {
      if (mounted.current && version === requestVersion.current) setLoading(false);
    }
  }, [record, onSessionExpired]);

  useEffect(() => {
    let active = true;
    queueMicrotask(() => { if (active) load(false); });
    return () => { active = false; };
  }, [refreshVersion, load]);

  const runOrderCommand = async ({ order, action, busyRef, call, isExpected, mismatchMessage, successMessage }) => {
    busyRef.current = true;
    setBusyOrder(order.orderId);
    setError('');
    setNotice('');
    try {
      const result = await call(order.orderId);
      if (!mounted.current) return;
      if (!isExpected(result)) throw new Error(mismatchMessage);
      const message = successMessage(result);
      setNotice(message);
      record({ action, outcome: 'success', orderId: order.orderId,
        restaurantId: order.restaurantId, status: result.status, message });
      await load();
    } catch (commandError) {
      if (!mounted.current) return;
      record({ action, outcome: 'error', orderId: order.orderId,
        restaurantId: order.restaurantId, message: commandError.message, httpStatus: commandError.status });
      if (commandError.status === 401) {
        setOrders([]);
        onSessionExpired?.('Your session expired. Please log in again.');
      } else if ([404, 409].includes(commandError.status) || commandError.status == null) {
        // Reconcile stale state or an uncertain network outcome before another command.
        await load();
      }
      if (mounted.current) setError(commandError.message);
    } finally {
      busyRef.current = false;
      if (mounted.current) setBusyOrder(null);
    }
  };

  const cancel = async (order) => {
    if (user.role !== 'CUSTOMER' || order.customerId !== user.id || order.status !== 'PLACED'
        || loading || cancelBusy.current) return;
    await runOrderCommand({
      order,
      action: 'Cancel order',
      busyRef: cancelBusy,
      call: (orderId) => api.cancelOrder(orderId),
      isExpected: (result) => result?.id === order.orderId && result.status === 'CANCELLED',
      mismatchMessage: 'The cancellation response does not match this order.',
      successMessage: () => `Order #${order.orderId.slice(0, 8)} cancelled.`
    });
  };

  const refund = async (order) => {
    if (!operator || loading || refundBusy.current) return;
    await runOrderCommand({
      order,
      action: 'Refund order',
      busyRef: refundBusy,
      call: (orderId) => api.refundOrder(orderId),
      isExpected: (result) => result?.orderId === order.orderId && result.status === 'REFUNDED'
        && result.refund?.orderId === order.orderId,
      mismatchMessage: 'The refund response does not match this order.',
      successMessage: (result) => `Order #${order.orderId.slice(0, 8)} refunded ${formatPrice(result.refund.amount)}.`
    });
  };

  return (
    <section className="order-history" aria-label="Order history" aria-busy={loading}>
      <div className="section-heading">
        <h2>{titles[user.role]}</h2>
        <button className="link-button" type="button" disabled={loading || Boolean(busyOrder)}
          onClick={() => { setNotice(''); load(); }}>Refresh orders</button>
      </div>
      {loading && <p role="status">Loading order history...</p>}
      {notice && <p role="status">{notice}</p>}
      {error && <p role="alert">{error}</p>}
      {!loading && !error && orders.length === 0 && <p>No orders yet.</p>}
      {orders.map((order) => {
        const billing = canViewSummary ? order.billing : null;
        const refunds = billing?.refunds ?? [];
        const canRefund = operator && order.status === 'DELIVERED'
          && billing?.totalCharge != null && !billing.pricingPending && refunds.length === 0;
        const canCancel = user.role === 'CUSTOMER' && order.customerId === user.id && order.status === 'PLACED';
        return <article className="order-card" key={order.orderId}>
          <h4>Order #{order.orderId.slice(0, 8)} · {order.status}</h4>
          {order.status === 'REJECTED' && order.rejectionReason && <p>Rejection reason: {order.rejectionReason}</p>}
          <p className="muted">Restaurant: {order.restaurantId}</p>
          <ul>{order.orderLines.map((line, index) => (
            <li key={`${line.menuItemId}-${index}`}>
              {line.quantity} × {line.menuItemName || 'Menu item'}
              {user.role !== 'COURIER' && line.unitPrice != null && <> · {formatPrice(line.unitPrice)} each</>}
            </li>
          ))}</ul>
          <dl className="history-timestamps" aria-label="Order timestamps">
            {Object.entries(order.timestamps ?? {}).sort(([, a], [, b]) => Date.parse(a) - Date.parse(b))
              .map(([state, time]) => <div key={state}>
                <dt>{state.replaceAll('_', ' ')}</dt>
                <dd><time dateTime={time}>{new Date(time).toLocaleString()}</time></dd>
              </div>)}
          </dl>
          {user.role !== 'COURIER' && <OrderEventHistory orderId={order.orderId}
            restaurantId={order.restaurantId} disabled={loading || Boolean(busyOrder)} />}
          {canViewSummary && ['DELIVERED', 'REFUNDED'].includes(order.status) && (
            <>
              <OrderSummaryPage orderId={order.orderId} refreshVersion={revision} />
              {user.role === 'CUSTOMER' && order.customerId === user.id && (
                <OrderReviewPanel orderId={order.orderId} onSessionExpired={onSessionExpired} />
              )}
            </>
          )}
          {refunds.length > 0 && <section className="history-refunds" aria-label={`Refund ledger for order ${order.orderId}`}>
            <h5>Refund ledger</h5>
            {refunds.map((entry) => <div key={entry.refundId}>
              <p><strong>Refund amount: {formatPrice(entry.amount)}</strong></p>
              <p>Refund ID: {entry.refundId}</p>
              <p>Refunded: <time dateTime={entry.refundedAt}>{new Date(entry.refundedAt).toLocaleString()}</time></p>
              {operator && <p>Operator: {entry.operatorId}</p>}
            </div>)}
          </section>}
          {canRefund && <button className="secondary-button history-refund-button" type="button"
            disabled={loading || Boolean(busyOrder)} onClick={() => refund(order)}>
            {busyOrder === order.orderId ? 'Refunding...' : 'Refund order'}
          </button>}
          {canCancel && <button className="secondary-button history-cancel-button" type="button"
            disabled={loading || Boolean(busyOrder)} onClick={() => cancel(order)}>
            {busyOrder === order.orderId ? 'Cancelling...' : 'Cancel order'}
          </button>}
        </article>;
      })}
      {reviewedRestaurantIds.length > 0 && (
        <section className="restaurant-order-reviews" aria-label="Reviews for your restaurants">
          <h3>Restaurant reviews</h3>
          {reviewedRestaurantIds.map((restaurantId) => {
            const name = restaurants.find((restaurant) => restaurant.id === restaurantId)?.name;
            return <ReviewSummary key={restaurantId} target="RESTAURANT" targetId={restaurantId}
              heading={name ? `${name} reviews` : `Restaurant ${restaurantId.slice(0, 8)} reviews`}
              onSessionExpired={onSessionExpired} />;
          })}
        </section>
      )}
    </section>
  );
}

export default function OrderHistory({ user, ...props }) {
  return user?.id && titles[user.role]
    ? <History key={`${user.id}:${user.role}`} user={user} {...props} />
    : <p>Log in to view order history.</p>;
}
