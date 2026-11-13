import React from 'react';
import { MAP_SIZE, project } from './map_geometry';
import { bandStyle, courierStyle, markerLabel } from './map_bands';

const GRID_LINES = [0.25, 0.5, 0.75];

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
      <title>{`Courier ${courier.id} · ${style.label}${courier.area ? ` · ${courier.area}` : ''}`}</title>
      <polygon points="0,-11 11,0 0,11 -11,0" fill={style.color} />
      <text className="map-badge" textAnchor="middle" dy="0.35em">{style.badge}</text>
    </g>
  );
}

export default function SvgCityMap({ restaurants, couriers, bounds, showLabels, selectedId, onSelect }) {
  return (
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
            onSelect={onSelect}
          />
        ))}
      </svg>
    </div>
  );
}
