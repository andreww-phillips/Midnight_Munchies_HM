import React, { useEffect, useState } from 'react';
import { api } from '../../../api';
import { storageUrl } from '../storage';
import RestaurantMenu from '../menu_page/menu_page.jsx';
import OrderHistory from '../order_history.jsx';
import './restaurant_library.css';

export default function RestaurantList({ user }) {
  const [restaurants, setRestaurants] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [selectedRestaurant, setSelectedRestaurant] = useState(null);
  const [ordersVersion, setOrdersVersion] = useState(0);

  useEffect(() => {
    let active = true;
    api.restaurants()
      .then((items) => { if (active) setRestaurants(items); })
      .catch((loadError) => { if (active) setError(loadError.message); })
      .finally(() => { if (active) setLoading(false); });
    return () => { active = false; };
  }, []);

  return (
    <>
      <main>
        {selectedRestaurant ? (
          <RestaurantMenu
            key={selectedRestaurant.id}
            restaurant={selectedRestaurant}
            user={user}
            onBack={() => setSelectedRestaurant(null)}
            onOrderPlaced={() => setOrdersVersion((version) => version + 1)}
          />
        ) : (
          <>
            <div className="section-heading"><h2>Restaurants</h2></div>
            {loading && <p role="status">Loading...</p>}
            {error && <p role="alert">{error}</p>}
            {!loading && !error && restaurants.length === 0 && <p>No restaurants yet.</p>}
            <section id="restaurant-list">
              {[...restaurants].sort((a, b) => a.name.localeCompare(b.name)).map((restaurant) => {
                const logo = storageUrl('Restaurant Logos', restaurant.logo);
                const canManage = user?.role === 'RESTAURANT_STAFF' && Boolean(user.id) && restaurant.ownerId === user.id;
                return (
                  <div className="restaurant-card" key={restaurant.id}>
                    {logo && <img src={logo} alt={`${restaurant.name} logo`} width={120} height={120} />}
                    <h4>{restaurant.name}</h4>
                    <button className="primary-button" type="button" onClick={() => setSelectedRestaurant(restaurant)}>Menu</button>
                    {canManage && <button className="secondary-button" type="button" onClick={() => setSelectedRestaurant(restaurant)}>Edit menu</button>}
                  </div>
                );
              })}
            </section>
          </>
        )}
        {user?.role !== 'COURIER' && <OrderHistory refreshVersion={ordersVersion} />}
      </main>
      <footer><p>&copy; 2023 Midnight Munchies. All rights reserved.</p></footer>
    </>
  );
}
