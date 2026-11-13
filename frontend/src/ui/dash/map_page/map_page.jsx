import React, { useCallback, useEffect, useRef, useState } from 'react';
import { api } from '../../../api';
import { useCommandLog } from '../output_console/command_log';
import { MAP_SIZE, paddedBounds, project } from './map_geometry';
import { BAND_STYLES, COURIER_STYLES, bandStyle, courierStyle, markerLabel } from './map_bands';
import RestaurantDetails from './restaurant_details';
import { shortId } from '../operator_format';
import './map_page.css';

const GRID_LINES = [0.25, 0.5, 0.75];

function lastSuccessId(entries) {
  for (let index = entries.length - 1; index >= 0; index -= 1) {
    if (entries[index].outcome === 'success') return entries[index].id;
  }
  return null;
}

function RestaurantMarker({ restaurant, point, showLabels, selected, onSelect }) {
  const style = bandStyle(restaurant.band);
  const labelOnLeft = point.x > MAP_SIZE.width * 0.7;
  const select = () => onSelect(restaurant.id);
  return (
    <g
      className={`map-restaurant band-${String(restaurant.band).toLowerCase()}${selected ? ' is-selected' : ''}`}
      transform={`translate(${point.x} ${point.y})`}
      role="button"
      tabIndex={0}
      aria-label={`${markerLabel(restaurant)}, ${style.label.toLowerCase()} band, ${restaurant.status.toLowerCase()}`}
      onClick={select}
      onKeyDown={(event) => { if (event.key === 'Enter' || event.key === ' ') { event.preventDefault(); select(); } }}
    >
      <title>{markerLabel(restaurant)}</title>
      <circle r={12} fill={style.color} />
      {showLabels && (
        <text className="map-label" x={labelOnLeft ? -18 : 18} dy="0.35em" textAnchor={labelOnLeft ? 'end' : 'start'}>
          {markerLabel(restaurant)}
        </text>
      )}
    </g>
  );
}

function CourierMarker({ courier, point }) {
  const style = courierStyle(courier.status);
  return (
    <g className={`map-courier courier-${String(courier.status).toLowerCase()}`} transform={`translate(${point.x} ${point.y})`}
      aria-label={`Courier ${courier.id}, ${style.label.toLowerCase()}`}>
      <title>{`Courier ${courier.id} · ${style.label}`}</title>
      <polygon points="0,-11 11,0 0,11 -11,0" fill={style.color} />
      <text className="map-badge" textAnchor="middle" dy="0.35em">{style.badge}</text>
    </g>
  );
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

export default function MapPage({ user, onSessionExpired, onOpenRestaurant, pollMs = 5000 }) {
  const { entries, record } = useCommandLog();
  const [cityMap, setCityMap] = useState(null);
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(true);
  const [showLabels, setShowLabels] = useState(true);
  const [selectedId, setSelectedId] = useState(null);
  const failing = useRef(false);
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
      {bounds && !isEmpty && (
        <div className="map-canvas">
          <svg viewBox={`0 0 ${MAP_SIZE.width} ${MAP_SIZE.height}`} role="group" aria-label="City map canvas">
            <rect className="map-background" width={MAP_SIZE.width} height={MAP_SIZE.height} rx={12} />
            {GRID_LINES.map((line) => (
              <g key={line} className="map-grid">
                <line x1={MAP_SIZE.width * line} y1={0} x2={MAP_SIZE.width * line} y2={MAP_SIZE.height} />
                <line x1={0} y1={MAP_SIZE.height * line} x2={MAP_SIZE.width} y2={MAP_SIZE.height * line} />
              </g>
            ))}
            {couriers.map((courier) => (
              <CourierMarker key={courier.id} courier={courier} point={project(courier, bounds)} />
            ))}
            {restaurants.map((restaurant) => (
              <RestaurantMarker
                key={restaurant.id}
                restaurant={restaurant}
                point={project(restaurant, bounds)}
                showLabels={showLabels}
                selected={restaurant.id === selectedId}
                onSelect={setSelectedId}
              />
            ))}
          </svg>
        </div>
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
