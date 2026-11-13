import { describe, expect, it } from 'vitest';
import { MAP_SIZE, paddedBounds, project } from './map_geometry';

const size = { width: 100, height: 100, pad: 10 };
const bounds = { minLat: 45.0, maxLat: 46.0, minLon: -74.0, maxLon: -73.0 };

describe('project', () => {
  it('maps the corners of the bounds to the padded corners of the canvas', () => {
    expect(project({ latitude: 46.0, longitude: -74.0 }, bounds, size)).toEqual({ x: 10, y: 10 });
    expect(project({ latitude: 45.0, longitude: -73.0 }, bounds, size)).toEqual({ x: 90, y: 90 });
  });

  it('puts north at the top and east on the right', () => {
    const north = project({ latitude: 45.9, longitude: -73.5 }, bounds, size);
    const south = project({ latitude: 45.1, longitude: -73.5 }, bounds, size);
    const east = project({ latitude: 45.5, longitude: -73.1 }, bounds, size);
    const west = project({ latitude: 45.5, longitude: -73.9 }, bounds, size);
    expect(north.y).toBeLessThan(south.y);
    expect(east.x).toBeGreaterThan(west.x);
  });

  it('centres points when the bounds have no span', () => {
    const flat = { minLat: 45.5, maxLat: 45.5, minLon: -73.5, maxLon: -73.5 };
    expect(project({ latitude: 45.5, longitude: -73.5 }, flat, size)).toEqual({ x: 50, y: 50 });
  });

  it('uses the default canvas size', () => {
    expect(project({ latitude: 46.0, longitude: -74.0 }, bounds)).toEqual({ x: MAP_SIZE.pad, y: MAP_SIZE.pad });
  });
});

describe('paddedBounds', () => {
  it('returns null when there are no bounds', () => {
    expect(paddedBounds(null)).toBeNull();
  });

  it('adds padding on every side', () => {
    const padded = paddedBounds(bounds, 0.1);
    expect(padded.minLat).toBeCloseTo(44.9);
    expect(padded.maxLat).toBeCloseTo(46.1);
    expect(padded.minLon).toBeCloseTo(-74.1);
    expect(padded.maxLon).toBeCloseTo(-72.9);
  });

  it('widens a single point so it lands in the centre without NaN', () => {
    const padded = paddedBounds({ minLat: 45.5, maxLat: 45.5, minLon: -73.5, maxLon: -73.5 });
    expect(padded.maxLat).toBeGreaterThan(padded.minLat);
    expect(padded.maxLon).toBeGreaterThan(padded.minLon);
    const point = project({ latitude: 45.5, longitude: -73.5 }, padded, size);
    expect(point.x).toBeCloseTo(50);
    expect(point.y).toBeCloseTo(50);
  });
});
