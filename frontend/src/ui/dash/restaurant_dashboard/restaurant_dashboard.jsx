import React, { useEffect, useState } from 'react';
import { api } from '../../../api';
import { formatPrice } from '../menu_page/menu_format';
import './restaurant_dashboard.css';

function OrderCard({ order }) {
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
    </article>
  );
}

function OrderGroup({ title, orders, emptyMessage, queue = false }) {
  const List = queue ? 'ol' : 'ul';
  return (
    <section className="dashboard-order-group" aria-label={title}>
      <h3>{title} ({orders.length})</h3>
      {orders.length === 0 ? <p className="muted">{emptyMessage}</p> : (
        <List className={queue ? 'dashboard-queue' : 'dashboard-orders'}>
          {orders.map((order) => <li key={order.id}><OrderCard order={order} /></li>)}
        </List>
      )}
    </section>
  );
}

function RestaurantOrders({ restaurant }) {
  const [data, setData] = useState({ orders: [], queue: [] });
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [refresh, setRefresh] = useState(0);

  useEffect(() => {
    let active = true;
    Promise.all([api.orders(), api.preparationQueue(restaurant.id)])
      .then(([orders, queue]) => {
        if (active) setData({
          orders: orders.filter((order) => order.restaurantId === restaurant.id),
          // Keep the server's FIFO order; only waiting orders belong in this list.
          queue: queue.filter((order) => order.restaurantId === restaurant.id && order.status === 'ACCEPTED')
        });
      })
      .catch((loadError) => { if (active) setError(loadError.message); })
      .finally(() => { if (active) setLoading(false); });
    return () => { active = false; };
  }, [restaurant.id, refresh]);

  return (
    <div aria-busy={loading}>
      <div className="section-heading">
        <h2>{restaurant.name} orders</h2>
        <button className="link-button" type="button" disabled={loading}
          onClick={() => {
            setLoading(true);
            setError('');
            setRefresh((version) => version + 1);
          }}>Refresh orders</button>
      </div>
      {loading && <p role="status">Loading restaurant orders and preparation queue...</p>}
      {error && <p role="alert">{error}</p>}
      {!loading && !error && <>
        <OrderGroup title="Incoming orders" orders={data.orders.filter((order) => order.status === 'PLACED')}
          emptyMessage="No incoming orders." />
        <OrderGroup title="Preparation queue" orders={data.queue} queue
          emptyMessage="No accepted orders waiting for preparation." />
        <OrderGroup title="Preparing" orders={data.orders.filter((order) => order.status === 'PREPARING')}
          emptyMessage="No orders currently preparing." />
        <OrderGroup title="Ready" orders={data.orders.filter((order) => order.status === 'READY')}
          emptyMessage="No orders ready yet." />
        <OrderGroup title="Other orders" orders={data.orders.filter((order) =>
          !['PLACED', 'ACCEPTED', 'PREPARING', 'READY'].includes(order.status))}
          emptyMessage="No other orders." />
      </>}
    </div>
  );
}

export default function RestaurantDashboard({ restaurant, user, onBack }) {
  const canManage = user?.role === 'RESTAURANT_STAFF' && Boolean(user.id) && restaurant.ownerId === user.id;
  return (
    <section className="restaurant-dashboard">
      {onBack && <button type="button" onClick={onBack}>Back to restaurants</button>}
      {canManage ? (
        // Remount on identity/restaurant changes so previous data never appears in the new view.
        <RestaurantOrders key={`${user.id}:${restaurant.id}`} restaurant={restaurant} />
      ) : <p role="alert">Only owning restaurant staff can view this dashboard.</p>}
    </section>
  );
}
