import React, { useEffect } from 'react';
import {
  APILoadingStatus, APIProvider, AdvancedMarker, Map, useApiLoadingStatus, useMap
} from '@vis.gl/react-google-maps';
import { bandStyle, courierStyle, markerLabel } from './map_bands';

const MONTREAL_CENTER = { lat: 45.5017, lng: -73.5673 };
const DEFAULT_ZOOM = 12;
const FIT_PADDING = 48;

function FitToBounds({ bounds }) {
  const map = useMap();
  const { minLat, maxLat, minLon, maxLon } = bounds ?? {};

  useEffect(() => {
    if (!map || minLat === undefined) return undefined;
    const fit = () => map.fitBounds({ south: minLat, north: maxLat, west: minLon, east: maxLon }, FIT_PADDING);
    fit();
    window.addEventListener('resize', fit);
    return () => window.removeEventListener('resize', fit);
  }, [map, minLat, maxLat, minLon, maxLon]);

  return null;
}

function LoadWatcher({ onUnavailable }) {
  const status = useApiLoadingStatus();

  useEffect(() => {
    if (status === APILoadingStatus.FAILED) onUnavailable?.('Google Maps could not be loaded');
    if (status === APILoadingStatus.AUTH_FAILURE) onUnavailable?.('Google Maps rejected the API key');
  }, [status, onUnavailable]);

  return null;
}

function RestaurantPin({ restaurant, showLabels, selected, onSelect }) {
  const style = bandStyle(restaurant.band);
  return (
    <AdvancedMarker
      position={{ lat: restaurant.latitude, lng: restaurant.longitude }}
      title={markerLabel(restaurant)}
      zIndex={selected ? 3 : 2}
      onClick={() => onSelect(restaurant.id)}
    >
      <div className={`gmap-restaurant band-${String(restaurant.band).toLowerCase()}${selected ? ' is-selected' : ''}`}>
        <span className="gmap-dot" style={{ background: style.color }} />
        {showLabels && <span className="gmap-label">{markerLabel(restaurant)}</span>}
      </div>
    </AdvancedMarker>
  );
}

function CourierPin({ courier }) {
  const style = courierStyle(courier.status);
  return (
    <AdvancedMarker
      position={{ lat: courier.latitude, lng: courier.longitude }}
      title={`Courier ${courier.id} · ${style.label}${courier.area ? ` · ${courier.area}` : ''}`}
      zIndex={1}
    >
      <div className={`gmap-courier courier-${String(courier.status).toLowerCase()}`} style={{ background: style.color }}>
        <span className="gmap-badge">{style.badge}</span>
      </div>
    </AdvancedMarker>
  );
}

export default function GoogleCityMap({
  apiKey, mapId, restaurants, couriers, bounds, showLabels, selectedId, onSelect, onUnavailable
}) {
  return (
    <APIProvider apiKey={apiKey} onError={() => onUnavailable?.('Google Maps could not be loaded')}>
      <LoadWatcher onUnavailable={onUnavailable} />
      <div className="gmap-canvas" role="group" aria-label="City map canvas">
        <Map
          mapId={mapId}
          defaultCenter={MONTREAL_CENTER}
          defaultZoom={DEFAULT_ZOOM}
          clickableIcons={false}
          disableDefaultUI
          gestureHandling="none"
          keyboardShortcuts={false}
          disableDoubleClickZoom
          scrollwheel={false}
        >
          <FitToBounds bounds={bounds} />
          {couriers.map((courier) => <CourierPin key={courier.id} courier={courier} />)}
          {restaurants.map((restaurant) => (
            <RestaurantPin
              key={restaurant.id}
              restaurant={restaurant}
              showLabels={showLabels}
              selected={restaurant.id === selectedId}
              onSelect={onSelect}
            />
          ))}
        </Map>
      </div>
    </APIProvider>
  );
}
