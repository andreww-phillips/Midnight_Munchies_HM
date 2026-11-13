export const MAP_SIZE = { width: 800, height: 500, pad: 40 };

export function paddedBounds(bounds, ratio = 0.1, minSpan = 0.005) {
  if (!bounds) return null;
  const widen = (min, max) => {
    const center = (min + max) / 2;
    const half = (Math.max(max - min, minSpan) * (1 + 2 * ratio)) / 2;
    return [center - half, center + half];
  };
  const [minLat, maxLat] = widen(bounds.minLat, bounds.maxLat);
  const [minLon, maxLon] = widen(bounds.minLon, bounds.maxLon);
  return { minLat, maxLat, minLon, maxLon };
}

const share = (value, min, max) => (max === min ? 0.5 : (value - min) / (max - min));

export function project({ latitude, longitude }, bounds, size = MAP_SIZE) {
  const innerWidth = size.width - 2 * size.pad;
  const innerHeight = size.height - 2 * size.pad;
  return {
    x: size.pad + share(longitude, bounds.minLon, bounds.maxLon) * innerWidth,
    y: size.pad + (1 - share(latitude, bounds.minLat, bounds.maxLat)) * innerHeight
  };
}
