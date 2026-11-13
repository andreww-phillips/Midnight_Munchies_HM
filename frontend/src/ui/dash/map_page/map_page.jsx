import React, { useCallback, useEffect, useRef, useState } from 'react';
import { api } from '../../../api';
import { useCommandLog } from '../output_console/command_log';
import { paddedBounds } from './map_geometry';
import { BAND_STYLES, COURIER_STYLES } from './map_bands';
import SvgCityMap from './svg_city_map';
import GoogleCityMap from './google_city_map';
import RestaurantDetails from './restaurant_details';
import { shortId } from '../operator_format';
import './map_page.css';

const GOOGLE_MAPS_API_KEY = import.meta.env.VITE_GOOGLE_MAPS_API_KEY ?? '';
const GOOGLE_MAPS_MAP_ID = import.meta.env.VITE_GOOGLE_MAPS_MAP_ID || 'DEMO_MAP_ID';

function lastSuccessId(entries) {
  for (let index = entries.length - 1; index >= 0; index -= 1) {
    if (entries[index].outcome === 'success') return entries[index].id;
  }
  return null;
}

function MapLegend() {
  return (
    <ul className="map-legend" aria-label="Map legend">
      {Object.entries(BAND_STYLES).map(([band, style]) => (
        <li key={band}><span className="legend-dot" style={{ background: style.color }} />{style.label}: {style.description}</li>
      ))}
      {Object.entries(COURIER_STYLES).map(([status, style]) => (
        <li key={status}><span className="legend-diamond" style={{ background: style.color }} />Courier {style.badge}: {style.label}</li>
      ))}
    </ul>
  );
}

export default function MapPage({
  user, onSessionExpired, onOpenRestaurant, pollMs = 5000,
  googleMapsApiKey = GOOGLE_MAPS_API_KEY, googleMapsMapId = GOOGLE_MAPS_MAP_ID
}) {
  const { entries, record } = useCommandLog();
  const [cityMap, setCityMap] = useState(null);
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(true);
  const [showLabels, setShowLabels] = useState(true);
  const [selectedId, setSelectedId] = useState(null);
  const [googleFailed, setGoogleFailed] = useState(false);
  const failing = useRef(false);
  const googleFallbackLogged = useRef(false);
  const latestRequest = useRef(0);
  const mounted = useRef(true);
  const refreshKey = lastSuccessId(entries);

  useEffect(() => {
    mounted.current = true;
    return () => { mounted.current = false; };
  }, []);

  const load = useCallback(async () => {
    const requestId = ++latestRequest.current;
    try {
      const data = await api.cityMap();
      if (!mounted.current || requestId !== latestRequest.current) return;
      failing.current = false;
      setCityMap(data);
      setError('');
    } catch (loadError) {
      if (!mounted.current || requestId !== latestRequest.current) return;
      if (loadError.status === 401) {
        onSessionExpired?.(loadError.message);
        return;
      }
      setError(loadError.message);
      if (!failing.current) {
        record({ action: 'Load map', outcome: 'error', message: loadError.message, httpStatus: loadError.status });
      }
      failing.current = true;
    } finally {
      if (mounted.current && requestId === latestRequest.current) setLoading(false);
    }
  }, [onSessionExpired, record]);

  useEffect(() => {
    load();
  }, [load, refreshKey]);

  useEffect(() => {
    if (!pollMs) return undefined;
    const timer = setInterval(load, pollMs);
    return () => clearInterval(timer);
  }, [load, pollMs]);

  const restaurants = cityMap?.restaurants ?? [];
  const couriers = cityMap?.couriers ?? [];
  const bounds = paddedBounds(cityMap?.bounds);
  const isEmpty = Boolean(cityMap) && (!bounds || (restaurants.length === 0 && couriers.length === 0));
  const selected = restaurants.find((restaurant) => restaurant.id === selectedId) ?? null;
  const isOperator = user?.role === 'PLATFORM_OPERATOR';
  const alerts = isOperator ? restaurants.filter((restaurant) => restaurant.band === 'RED') : [];
  const waiting = isOperator ? (cityMap?.awaitingCourier ?? []) : [];
  const useGoogle = Boolean(googleMapsApiKey) && !googleFailed;

  const fallBackToSimpleMap = useCallback((reason) => {
    if (!mounted.current || googleFallbackLogged.current) return;
    googleFallbackLogged.current = true;
    record({ action: 'Load Google map', outcome: 'error', message: `${reason}; showing the simple map instead.` });
    setGoogleFailed(true);
  }, [record]);

  return (
    <section className="city-map" aria-label="City map">
      <div className="section-heading">
        <h2>City map</h2>
        <label className="map-toggle">
          <input type="checkbox" checked={showLabels} onChange={(event) => setShowLabels(event.target.checked)} />
          Show labels
        </label>
      </div>
      {loading && !cityMap && <p role="status">Loading map...</p>}
      {error && <p role="alert">Map unavailable: {error}</p>}
      {isEmpty && <p className="map-empty">No restaurants or couriers to display yet.</p>}
      {alerts.length > 0 && (
        <section className="map-alert" aria-label="Operator alerts">
          <strong>Restaurants needing attention</strong>
          <ul>
            {alerts.map((restaurant) => (
              <li key={restaurant.id}>
                {restaurant.name}: {restaurant.status === 'OPEN' ? `${restaurant.queuedCount} queued` : restaurant.status}
              </li>
            ))}
          </ul>
        </section>
      )}
      {waiting.length > 0 && (
        <section className="map-alert map-waiting" aria-label="Orders waiting for a courier">
          <strong>Orders waiting for a courier</strong>
          <ul>
            {waiting.map((order) => (
              <li key={order.orderId}>Order #{shortId(order.orderId)} · {order.restaurantName ?? 'Unknown restaurant'}</li>
            ))}
          </ul>
        </section>
      )}
      {cityMap && useGoogle && (
        <GoogleCityMap
          apiKey={googleMapsApiKey}
          mapId={googleMapsMapId}
          restaurants={restaurants}
          couriers={couriers}
          bounds={isEmpty ? null : bounds}
          showLabels={showLabels}
          selectedId={selectedId}
          onSelect={setSelectedId}
          onUnavailable={fallBackToSimpleMap}
        />
      )}
      {!useGoogle && bounds && !isEmpty && (
        <SvgCityMap
          restaurants={restaurants}
          couriers={couriers}
          bounds={bounds}
          showLabels={showLabels}
          selectedId={selectedId}
          onSelect={setSelectedId}
        />
      )}
      {cityMap && <MapLegend />}
      {selected && (
        <RestaurantDetails
          restaurant={selected}
          user={user}
          onClose={() => setSelectedId(null)}
          onOpenRestaurant={onOpenRestaurant}
          onSessionExpired={onSessionExpired}
        />
      )}
    </section>
  );
}
