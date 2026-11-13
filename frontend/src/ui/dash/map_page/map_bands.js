export const BAND_STYLES = {
  GREEN: { label: 'Short (green)', color: '#2e9e5b', description: 'fewer than 3 queued' },
  AMBER: { label: 'Building (amber)', color: '#e0a100', description: '3 to 5 queued' },
  RED: { label: 'Long (red)', color: '#d64545', description: '6 or more queued, or paused/closed' }
};

export const COURIER_STYLES = {
  IDLE: { badge: 'I', label: 'Idle', color: '#2f6fdf' },
  ASSIGNED: { badge: 'A', label: 'Assigned', color: '#8a3ffc' },
  CARRYING: { badge: 'C', label: 'Carrying', color: '#3d3d4e' }
};

const UNKNOWN_BAND = { label: 'Unknown', color: '#8c8c96', description: 'Unknown queue band' };

export function bandStyle(band) {
  return BAND_STYLES[band] ?? UNKNOWN_BAND;
}

export function courierStyle(status) {
  return COURIER_STYLES[status] ?? { badge: '?', label: status, color: '#8c8c96' };
}

export function markerLabel(restaurant) {
  return `${restaurant.name} · ${restaurant.queuedCount} queued`;
}
