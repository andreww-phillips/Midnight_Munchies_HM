import React, { useEffect, useState } from 'react';
import { api } from '../../../api';
import { useCommandLog } from '../output_console/command_log';
import { bandStyle } from './map_bands';
import RestaurantDeliveries from './restaurant_deliveries';

const STAGES = ['PLACED', 'ACCEPTED', 'PREPARING', 'READY'];

const timeOf = (order) => order.acceptedAt ?? order.createdAt ?? '';

function activeQueue(orders, restaurantId) {
  return orders
    .filter((order) => order.restaurantId === restaurantId && STAGES.includes(order.status))
    .sort((a, b) => STAGES.indexOf(a.status) - STAGES.indexOf(b.status) || String(timeOf(a)).localeCompare(String(timeOf(b))));
}

function canSeeQueue(user, restaurant) {
  if (user?.role === 'PLATFORM_OPERATOR') return true;
  return user?.role === 'RESTAURANT_STAFF' && restaurant.ownedByViewer === true;
}

function quickActions(user, restaurant) {
  if (user?.role === 'RESTAURANT_STAFF' && canSeeQueue(user, restaurant)) {
    return [{ label: 'Manage orders', view: 'orders' }];
  }
  if (user?.role === 'CUSTOMER' && restaurant.status === 'OPEN') {
    return [{ label: 'Order from here', view: 'menu' }];
  }
  return [];
}

export default function RestaurantDetails({ restaurant, user, onClose, onOpenRestaurant, onSessionExpired }) {
  const { record } = useCommandLog();
  const [queue, setQueue] = useState([]);
  const [queueError, setQueueError] = useState('');
  const [menu, setMenu] = useState([]);
  const [menuError, setMenuError] = useState('');
  const showQueue = canSeeQueue(user, restaurant);
  const actions = quickActions(user, restaurant);
  const style = bandStyle(restaurant.band);
  const countsKey = [restaurant.pendingCount, restaurant.queuedCount, restaurant.preparingCount, restaurant.readyCount].join(':');

  useEffect(() => {
    if (!showQueue) return undefined;
    let active = true;
    api.orders()
      .then((orders) => {
        if (!active) return;
        setQueue(activeQueue(orders, restaurant.id));
        setQueueError('');
      })
      .catch((loadError) => {
        if (!active) return;
        if (loadError.status === 401) {
          onSessionExpired?.(loadError.message);
          return;
        }
        setQueueError(loadError.message);
        record({ action: 'Load restaurant queue', outcome: 'error', message: loadError.message,
          restaurantId: restaurant.id, httpStatus: loadError.status });
      });
    return () => { active = false; };
  }, [showQueue, restaurant.id, countsKey, record, onSessionExpired]);

  useEffect(() => {
    let active = true;
    api.menu(restaurant.id)
      .then((items) => {
        if (!active) return;
        setMenu(items ?? []);
        setMenuError('');
      })
      .catch((loadError) => {
        if (!active) return;
        if (loadError.status === 401) {
          onSessionExpired?.(loadError.message);
          return;
        }
        setMenuError(loadError.message);
        record({ action: 'Load restaurant menu', outcome: 'error', message: loadError.message,
          restaurantId: restaurant.id, httpStatus: loadError.status });
      });
    return () => { active = false; };
  }, [restaurant.id, countsKey, record, onSessionExpired]);

  return (
    <section className="restaurant-details" aria-label={`${restaurant.name} details`}>
      <div className="section-heading">
        <h3>{restaurant.name}</h3>
        <button className="secondary-button" type="button" onClick={onClose}>Close</button>
      </div>
      <p className="muted">Status: {restaurant.status} · Band: {style.label}</p>
      <dl className="details-stats">
        <div><dt>Pending</dt><dd>{restaurant.pendingCount}</dd></div>
        <div><dt>Queued</dt><dd>{restaurant.queuedCount}</dd></div>
        <div><dt>In preparation</dt><dd>{restaurant.preparingCount}</dd></div>
        <div><dt>Ready</dt><dd>{restaurant.readyCount}</dd></div>
      </dl>
      <div>
        <h4>Menu</h4>
        {menuError && <p role="alert">{menuError}</p>}
        {!menuError && menu.length === 0 && <p className="muted">No menu items yet.</p>}
        {menu.length > 0 && (
          <ul className="details-menu" aria-label="Menu items">
            {menu.map((item) => (
              <li key={item.id}>
                <span>{item.name} · ${Number(item.price).toFixed(2)}</span>
                {item.status === 'SOLD_OUT' && <span className="sold-out-tag">Sold out</span>}
              </li>
            ))}
          </ul>
        )}
      </div>
      {showQueue && (
        <div>
          <h4>Active orders</h4>
          {queueError && <p role="alert">{queueError}</p>}
          {!queueError && queue.length === 0 && <p className="muted">No active orders.</p>}
          {queue.length > 0 && (
            <ol className="details-queue" aria-label="Active orders">
              {queue.map((order) => <li key={order.id}>Order #{order.id} · {order.status}</li>)}
            </ol>
          )}
        </div>
      )}
      {user?.role === 'PLATFORM_OPERATOR' && (
        <RestaurantDeliveries
          orderIds={queue.filter((order) => order.status === 'READY').map((order) => order.id)}
          refreshKey={countsKey}
          onSessionExpired={onSessionExpired}
        />
      )}
      {actions.length > 0 && onOpenRestaurant && (
        <div className="details-actions">
          {actions.map((action) => (
            <button key={action.view} className="primary-button" type="button" onClick={() => onOpenRestaurant(restaurant, action.view)}>
              {action.label}
            </button>
          ))}
        </div>
      )}
    </section>
  );
}
