import React, { useCallback, useEffect, useRef, useState } from 'react';
import {
  APILoadingStatus, APIProvider, AdvancedMarker, AdvancedMarkerAnchorPoint, Map, useApiLoadingStatus, useMap
} from '@vis.gl/react-google-maps';
import { bandStyle, courierStyle, markerLabel } from './map_bands';

const MONTREAL_CENTER = { lat: 45.5017, lng: -73.5673 };
const DEFAULT_ZOOM = 12;
const FIT_PADDING = 48;
const Z_RESTAURANT = 10;
const Z_COURIER = 20;
const Z_FRONT = 30;
const MIN_ZOOM = 10;
const MAX_ZOOM = 18;
const MONTREAL_LIMITS = { north: 45.74, south: 45.37, west: -74.01, east: -73.44 };

function FitToBounds({ bounds, enabled, fitVersion, fittingRef }) {
  const map = useMap();
  const { minLat, maxLat, minLon, maxLon } = bounds ?? {};

  useEffect(() => {
    if (!map || !enabled || minLat === undefined) return undefined;
    const fit = () => {
      fittingRef.current = true;
      map.fitBounds({ south: minLat, north: maxLat, west: minLon, east: maxLon }, FIT_PADDING);
    };
    fit();
    window.addEventListener('resize', fit);
    return () => window.removeEventListener('resize', fit);
  }, [map, enabled, fitVersion, fittingRef, minLat, maxLat, minLon, maxLon]);

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

function RestaurantPin({ restaurant, showLabels, selected, front, onSelect, onHover }) {
  const style = bandStyle(restaurant.band);
  return (
    <AdvancedMarker
      position={{ lat: restaurant.latitude, lng: restaurant.longitude }}
      anchorPoint={AdvancedMarkerAnchorPoint.CENTER}
      title={markerLabel(restaurant)}
      zIndex={front ? Z_FRONT : Z_RESTAURANT}
      onClick={() => onSelect(restaurant.id)}
      onMouseEnter={() => onHover(restaurant.id)}
      onMouseLeave={() => onHover(null)}
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
      anchorPoint={AdvancedMarkerAnchorPoint.CENTER}
      title={`Courier ${courier.id} · ${style.label}${courier.area ? ` · ${courier.area}` : ''}`}
      zIndex={Z_COURIER}
      clickable={false}
    >
      <div className={`gmap-courier courier-${String(courier.status).toLowerCase()}`}>
        <span className="gmap-diamond" style={{ background: style.color }} />
        <span className="gmap-badge">{style.badge}</span>
      </div>
    </AdvancedMarker>
  );
}

export default function GoogleCityMap({
  apiKey, mapId, restaurants, couriers, bounds, showLabels, selectedId, onSelect, onUnavailable
}) {
  const [hoveredId, setHoveredId] = useState(null);
  const [autoFit, setAutoFit] = useState(true);
  const [fitVersion, setFitVersion] = useState(0);
  const fittingRef = useRef(true);

  const stopAutoFit = useCallback(() => setAutoFit(false), []);
  const onZoomChanged = useCallback(() => {
    if (!fittingRef.current) setAutoFit(false);
  }, []);
  const onIdle = useCallback(() => { fittingRef.current = false; }, []);
  const fitAll = () => {
    setAutoFit(true);
    setFitVersion((value) => value + 1);
  };

  return (
    <APIProvider apiKey={apiKey} onError={() => onUnavailable?.('Google Maps could not be loaded')}>
      <LoadWatcher onUnavailable={onUnavailable} />
      <div className="gmap-canvas" role="group" aria-label="City map canvas">
        {bounds && (
          <button className="secondary-button gmap-fit" type="button" aria-pressed={autoFit} onClick={fitAll}>
            Fit all
          </button>
        )}
        <Map
          mapId={mapId}
          defaultCenter={MONTREAL_CENTER}
          defaultZoom={DEFAULT_ZOOM}
          clickableIcons={false}
          disableDefaultUI
          zoomControl
          gestureHandling="cooperative"
          minZoom={MIN_ZOOM}
          maxZoom={MAX_ZOOM}
          restriction={{ latLngBounds: MONTREAL_LIMITS, strictBounds: false }}
          onDragstart={stopAutoFit}
          onZoomChanged={onZoomChanged}
          onIdle={onIdle}
        >
          <FitToBounds bounds={bounds} enabled={autoFit} fitVersion={fitVersion} fittingRef={fittingRef} />
          {couriers.map((courier) => <CourierPin key={courier.id} courier={courier} />)}
          {restaurants.map((restaurant) => (
            <RestaurantPin
              key={restaurant.id}
              restaurant={restaurant}
              showLabels={showLabels}
              selected={restaurant.id === selectedId}
              front={restaurant.id === selectedId || restaurant.id === hoveredId}
              onSelect={onSelect}
              onHover={setHoveredId}
            />
          ))}
        </Map>
      </div>
    </APIProvider>
  );
}
