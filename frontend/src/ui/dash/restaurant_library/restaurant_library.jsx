import React, { useEffect, useRef, useState } from 'react';
import { api } from '../../../api';
import { storageUrl } from '../storage';
import RestaurantMenu from '../menu_page/menu_page.jsx';
import OrderHistory from '../order_history.jsx';
import RestaurantDashboard from '../restaurant_dashboard/restaurant_dashboard.jsx';
import { ReviewSummary } from '../reviews';
import { useCommandLog } from '../output_console/command_log';
import './restaurant_library.css';

export default function RestaurantList({ user, onSessionExpired, initialOpen = null }) {
  const { record } = useCommandLog();
  const [restaurants, setRestaurants] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [selectedRestaurant, setSelectedRestaurant] = useState(null);
  const [selectedView, setSelectedView] = useState('menu');
  const [ordersVersion, setOrdersVersion] = useState(0);
  const [pendingStatus, setPendingStatus] = useState(null);
  const [notice, setNotice] = useState('');
  const [availabilityError, setAvailabilityError] = useState('');
  const statusBusy = useRef(false);
  const mounted = useRef(false);
  const isOperator = user?.role === 'PLATFORM_OPERATOR';

  const [pendingOpen] = useState(initialOpen);

  useEffect(() => {
    mounted.current = true;
    return () => { mounted.current = false; };
  }, []);

  useEffect(() => {
    let active = true;
    api.restaurants()
      .then((items) => {
        if (!active) return;
        setRestaurants(items);
        const match = pendingOpen && items.find((restaurant) => restaurant.id === pendingOpen.restaurantId);
        if (match) {
          setSelectedView(pendingOpen.view ?? 'menu');
          setSelectedRestaurant(match);
        }
      })
      .catch((loadError) => {
        if (!active) return;
        setError(loadError.message);
        record({ action: 'Load restaurants', outcome: 'error', message: loadError.message, httpStatus: loadError.status });
      })
      .finally(() => { if (active) setLoading(false); });
    return () => { active = false; };
  }, [pendingOpen, record]);

  const menuRestaurantId = selectedView === 'menu' ? selectedRestaurant?.id : null;
  useEffect(() => {
    if (user?.role !== 'CUSTOMER' || !menuRestaurantId) return undefined;
    let active = true;
    let inFlight = false;
    let failed = false;
    const refreshAvailability = async () => {
      if (inFlight) return;
      inFlight = true;
      try {
        const items = await api.restaurants();
        if (!active) return;
        const current = items.find((item) => item.id === menuRestaurantId);
        if (!current) throw new Error('This restaurant is no longer available.');
        setRestaurants(items);
        setSelectedRestaurant(current);
        setAvailabilityError('');
        failed = false;
      } catch (loadError) {
        if (!active) return;
        setAvailabilityError(loadError.message);
        if (!failed) record({ action: 'Refresh restaurant availability', outcome: 'error',
          restaurantId: menuRestaurantId, message: loadError.message, httpStatus: loadError.status });
        failed = true;
        if (loadError.status === 401) onSessionExpired?.(loadError.message);
      } finally {
        inFlight = false;
      }
    };
    refreshAvailability();
    const timer = window.setInterval(refreshAvailability, 5000);
    return () => { active = false; window.clearInterval(timer); };
  }, [user?.role, menuRestaurantId, record, onSessionExpired]);

  const openRestaurant = (restaurant, view = 'menu') => {
    setAvailabilityError('');
    setSelectedView(view);
    setSelectedRestaurant(restaurant);
  };

  const changeStatus = async (restaurant, status) => {
    const ownsRestaurant = user?.role === 'RESTAURANT_STAFF' && Boolean(user.id) && restaurant.ownerId === user.id;
    if (statusBusy.current || (!isOperator && !(ownsRestaurant && status === 'PAUSED'))) return;
    statusBusy.current = true;
    setPendingStatus(restaurant.id);
    setError('');
    setNotice('');
    const action = status === 'PAUSED' ? 'Pause restaurant' : 'Reopen restaurant';
    try {
      const updated = await api.changeRestaurantStatus(restaurant.id, status);
      const message = `${updated.name} is now ${updated.status}.`;
      record({ action, outcome: 'success', restaurantId: updated.id, status: updated.status, message });
      if (!mounted.current) return;
      setRestaurants((items) => items.map((item) => item.id === updated.id ? updated : item));
      setSelectedRestaurant((current) => current?.id === updated.id ? updated : current);
      setNotice(message);
    } catch (commandError) {
      record({ action, outcome: 'error', restaurantId: restaurant.id, message: commandError.message, httpStatus: commandError.status });
      if (!mounted.current) return;
      setError(commandError.message);
      if (commandError.status === 401) onSessionExpired?.(commandError.message);
    } finally {
      statusBusy.current = false;
      if (mounted.current) setPendingStatus(null);
    }
  };

  return (
    <>
      <main>
        {selectedRestaurant && selectedView === 'orders' ? (
          <RestaurantDashboard
            restaurant={selectedRestaurant}
            user={user}
            onSessionExpired={onSessionExpired}
            onBack={() => setSelectedRestaurant(null)}
          />
        ) : selectedRestaurant ? (
          <RestaurantMenu
            key={selectedRestaurant.id}
            restaurant={selectedRestaurant}
            user={user}
            onSessionExpired={onSessionExpired}
            availabilityError={availabilityError}
            onBack={() => setSelectedRestaurant(null)}
            onOrderPlaced={() => setOrdersVersion((version) => version + 1)}
          />
        ) : (
          <>
            <div className="section-heading"><h2>Restaurants</h2></div>
            {loading && <p role="status">Loading...</p>}
            {error && <p role="alert">{error}</p>}
            {notice && <p role="status">{notice}</p>}
            {!loading && !error && restaurants.length === 0 && <p>No restaurants yet.</p>}
            <section id="restaurant-list">
              {[...restaurants].sort((a, b) => a.name.localeCompare(b.name)).map((restaurant) => {
                const logo = storageUrl('Restaurant Logos', restaurant.logo);
                const canManage = user?.role === 'RESTAURANT_STAFF' && Boolean(user.id) && restaurant.ownerId === user.id;
                return (
                  <div className="restaurant-card" key={restaurant.id}>
                    {logo && <img src={logo} alt={`${restaurant.name} logo`} width={120} height={120} />}
                    <h4>{restaurant.name}</h4>
                    <p className="status-pill">Status: {restaurant.status}</p>
                    <ReviewSummary target="RESTAURANT" targetId={restaurant.id}
                      onSessionExpired={onSessionExpired} compact />
                    <button className="primary-button" type="button" onClick={() => openRestaurant(restaurant)}>Menu</button>
                    {canManage && <>
                      <button className="secondary-button" type="button" onClick={() => openRestaurant(restaurant)}>Edit menu</button>
                      <button className="secondary-button" type="button" onClick={() => openRestaurant(restaurant, 'orders')}>Manage orders</button>
                    </>}
                    {(canManage || isOperator) && restaurant.status === 'OPEN' && (
                      <button className="secondary-button" type="button" disabled={pendingStatus !== null}
                        aria-label={`Pause ${restaurant.name}`} onClick={() => changeStatus(restaurant, 'PAUSED')}>Pause</button>
                    )}
                    {isOperator && ['PAUSED', 'CLOSED'].includes(restaurant.status) && (
                      <button className="secondary-button" type="button" disabled={pendingStatus !== null}
                        aria-label={`Reopen ${restaurant.name}`} onClick={() => changeStatus(restaurant, 'OPEN')}>Reopen</button>
                    )}
                  </div>
                );
              })}
            </section>
          </>
        )}
        {user?.role !== 'COURIER' && !(selectedRestaurant && selectedView === 'orders') && <OrderHistory
          refreshVersion={ordersVersion} user={user} onSessionExpired={onSessionExpired} restaurants={restaurants} />}
      </main>
      <footer><p>&copy; 2023 Midnight Munchies. All rights reserved.</p></footer>
    </>
  );
}
