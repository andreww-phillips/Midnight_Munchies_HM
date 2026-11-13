import React, { useState } from 'react';
import MapPage from '../map_page/map_page.jsx';
import RestaurantList from '../restaurant_library/restaurant_library.jsx';
import OrderHistory from '../order_history.jsx';
import TabBar from './tab_bar.jsx';
import InfoArea from './info_area.jsx';
import './dashboard.css';

export default function Dashboard({ user, onSessionExpired }) {
  const [tab, setTab] = useState('orders');
  const [showLabels, setShowLabels] = useState(true);
  const [selectedId, setSelectedId] = useState(null);
  const [mapData, setMapData] = useState(null);
  const [openRequest, setOpenRequest] = useState(null);
  const [version, setVersion] = useState(0);
  const selected = mapData?.restaurants?.find((restaurant) => restaurant.id === selectedId) ?? null;
  const key = `${user.id}:${user.role}:${version}`;

  const changeTab = (next) => {
    setOpenRequest(null);
    setTab(next);
  };

  const handleReset = () => {
    setSelectedId(null);
    setMapData(null);
    setOpenRequest(null);
    setVersion((current) => current + 1);
  };

  return (
    <>
      <TabBar active={tab} onChange={changeTab} />
      <InfoArea
        user={user}
        restaurant={selected}
        showLabels={showLabels}
        onToggleLabels={setShowLabels}
        onReset={handleReset}
        onSessionExpired={onSessionExpired}
      />
      <div id="dashboard-panel" role="tabpanel" aria-labelledby={`tab-${tab}`}>
        {tab === 'map' && (
          <MapPage
            key={key}
            user={user}
            onSessionExpired={onSessionExpired}
            showLabels={showLabels}
            selectedId={selectedId}
            onSelect={setSelectedId}
            onLoaded={setMapData}
            onOpenRestaurant={(restaurant, view) => {
              setOpenRequest({ restaurantId: restaurant.id, view });
              setTab('orders');
            }}
          />
        )}
        {tab === 'orders' && (
          <RestaurantList key={key} user={user} onSessionExpired={onSessionExpired} initialOpen={openRequest} />
        )}
        {tab === 'delivery' && (
          <section className="module-placeholder">
            <h2>Delivery</h2>
            <p className="muted">The delivery module is not available yet.</p>
          </section>
        )}
        {tab === 'history' && (
          <section className="module-placeholder">
            <h2>Pricing &amp; History</h2>
            <p className="muted">Pricing is not available yet.</p>
            {user.role !== 'COURIER' && <OrderHistory key={key} refreshVersion={version} />}
          </section>
        )}
      </div>
    </>
  );
}
