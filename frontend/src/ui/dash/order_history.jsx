import React, { useEffect, useState } from 'react';
import { api } from '../../api';
import { formatPrice } from './menu_page/menu_format';

export default function OrderHistory({ refreshVersion }) {
  const [orders, setOrders] = useState([]);
  const [error, setError] = useState('');
  const [refresh, setRefresh] = useState(0);
  useEffect(() => {
    let active = true;
    api.orders().then((items) => {
      if (active) { setOrders(items); setError(''); }
    }).catch((loadError) => { if (active) setError(loadError.message); });
    return () => { active = false; };
  }, [refreshVersion, refresh]);

  return (
    <section className="order-history">
      <div className="section-heading">
        <h2>Your orders</h2>
        <button className="link-button" type="button" onClick={() => setRefresh((version) => version + 1)}>Refresh orders</button>
      </div>
      {error && <p role="alert">{error}</p>}
      {!error && orders.length === 0 && <p>No orders yet.</p>}
      {orders.map((order) => (
        <article className="order-card" key={order.id}>
          <h4>Order #{order.id.slice(0, 8)} · {order.status}</h4>
          <ul>{order.orderLines.map((line, index) => (
            <li key={`${line.menuItemId}-${index}`}>
              {line.quantity} × {line.menuItemName} · {formatPrice(line.unitPrice)} each
            </li>
          ))}</ul>
        </article>
      ))}
    </section>
  );
}
