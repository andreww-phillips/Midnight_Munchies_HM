// @vitest-environment jsdom
import React, { act } from 'react';
import { createRoot } from 'react-dom/client';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { api } from '../../../api';
import { CommandLogProvider } from '../output_console/command_log_provider';
import OutputConsole from '../output_console/output_console';
import MapPage from './map_page';

const google = vi.hoisted(() => ({ status: 'LOADED', fitBounds: null, map: null, mapProps: null }));

vi.mock('../../../api', () => ({ api: { cityMap: vi.fn(), orders: vi.fn(), menu: vi.fn(), operatorDeliveries: vi.fn() } }));

vi.mock('@vis.gl/react-google-maps', () => ({
  APILoadingStatus: { LOADED: 'LOADED', FAILED: 'FAILED', AUTH_FAILURE: 'AUTH_FAILURE' },
  APIProvider: ({ children }) => <div className="mock-provider">{children}</div>,
  Map: (props) => {
    google.mapProps = props;
    return <div className="mock-map">{props.children}</div>;
  },
  AdvancedMarkerAnchorPoint: { CENTER: ['50%', '50%'], BOTTOM_CENTER: ['50%', '100%'] },
  AdvancedMarker: ({ children, title, onClick, onMouseEnter, onMouseLeave, anchorPoint, zIndex, clickable, position }) => (
    <div className="mock-marker" title={title} onClick={onClick} onMouseEnter={onMouseEnter} onMouseLeave={onMouseLeave}
      data-anchor={JSON.stringify(anchorPoint)} data-z={zIndex} data-clickable={String(clickable ?? true)}
      data-position={JSON.stringify(position)}>
      {children}
    </div>
  ),
  useApiLoadingStatus: () => google.status,
  useMap: () => google.map
}));

const restaurant = (overrides) => ({
  id: 'r1', name: 'Bobs Burgers', status: 'OPEN', latitude: 45.5048, longitude: -73.5772, area: 'Downtown – McGill University',
  pendingCount: 0, queuedCount: 4, preparingCount: 0, readyCount: 0, band: 'AMBER', ...overrides
});
const cityMap = {
  bounds: { minLat: 45.5048, maxLat: 45.5225, minLon: -73.5848, maxLon: -73.5772 },
  restaurants: [restaurant(), restaurant({ id: 'r2', name: 'Pizza Palace', latitude: 45.5225, longitude: -73.5848, queuedCount: 0, band: 'GREEN' })],
  couriers: [{ id: 'c1', latitude: 45.51, longitude: -73.58, status: 'IDLE', area: 'Downtown – Concordia, Guy St' }]
};

let container, root;

async function render(props = {}) {
  await act(async () => root.render(
    <CommandLogProvider>
      <MapPage user={{ id: 'courier-1', role: 'COURIER' }} pollMs={0} googleMapsApiKey="test-key" googleMapsMapId="TEST_MAP" {...props} />
      <OutputConsole />
    </CommandLogProvider>
  ));
}

beforeEach(() => {
  vi.resetAllMocks();
  vi.stubGlobal('IS_REACT_ACT_ENVIRONMENT', true);
  google.status = 'LOADED';
  google.fitBounds = vi.fn();
  google.map = { fitBounds: google.fitBounds };
  google.mapProps = null;
  api.cityMap.mockResolvedValue(cityMap);
  api.orders.mockResolvedValue([]);
  api.menu.mockResolvedValue([]);
  api.operatorDeliveries.mockResolvedValue({ active: [], queued: [] });
  container = document.createElement('div');
  document.body.append(container);
  root = createRoot(container);
});

afterEach(async () => {
  await act(async () => root.unmount());
  container.remove();
  vi.unstubAllGlobals();
});

describe('Google city map', () => {
  it('draws restaurants by queue band and on-duty couriers by state on the Google map', async () => {
    await render();

    expect(container.querySelector('.mock-map')).not.toBeNull();
    expect(container.querySelector('.map-canvas svg')).toBeNull();
    expect(google.mapProps.mapId).toBe('TEST_MAP');
    const dots = [...container.querySelectorAll('.gmap-dot')].map((dot) => dot.style.background);
    expect(dots).toEqual(['rgb(224, 161, 0)', 'rgb(46, 158, 91)']);
    expect([...container.querySelectorAll('.gmap-label')].map((label) => label.textContent))
      .toEqual(['Bobs Burgers · 4 queued', 'Pizza Palace · 0 queued']);
    expect(container.querySelector('.gmap-courier .gmap-badge').textContent).toBe('I');
    expect(container.querySelector('.gmap-courier').parentElement.title).toBe('Courier c1 · Idle · Downtown – Concordia, Guy St');
  });

  it('fits the map to the padded dataset bounds once per extent', async () => {
    await render();

    expect(google.fitBounds).toHaveBeenCalledTimes(1);
    const [box, padding] = google.fitBounds.mock.calls[0];
    expect(padding).toBe(48);
    expect(box.south).toBeLessThan(45.5048);
    expect(box.north).toBeGreaterThan(45.5225);
    expect(box.west).toBeLessThan(-73.5848);
    expect(box.east).toBeGreaterThan(-73.5772);

    await render();
    expect(google.fitBounds).toHaveBeenCalledTimes(1);
  });

  it('lets users zoom and pan within Montreal with only the zoom buttons shown', async () => {
    await render();

    expect(google.mapProps).toMatchObject({
      gestureHandling: 'cooperative', disableDefaultUI: true, zoomControl: true, minZoom: 10, maxZoom: 18
    });
    expect(google.mapProps.scrollwheel).toBeUndefined();
    expect(google.mapProps.disableDoubleClickZoom).toBeUndefined();
    const limits = google.mapProps.restriction.latLngBounds;
    expect(limits.south).toBeLessThanOrEqual(45.40);
    expect(limits.north).toBeGreaterThanOrEqual(45.71);
    expect(limits.west).toBeLessThanOrEqual(-73.98);
    expect(limits.east).toBeGreaterThanOrEqual(-73.47);
    expect(google.mapProps.restriction.strictBounds).toBe(false);
  });

  it('keeps fitting to all markers on resize until the user pans the map', async () => {
    await render();
    await act(async () => google.mapProps.onIdle());

    await act(async () => window.dispatchEvent(new Event('resize')));
    expect(google.fitBounds).toHaveBeenCalledTimes(2);
    expect(google.fitBounds.mock.calls[1]).toEqual(google.fitBounds.mock.calls[0]);

    await act(async () => google.mapProps.onIdle());
    await act(async () => google.mapProps.onDragstart());
    await act(async () => window.dispatchEvent(new Event('resize')));
    expect(google.fitBounds).toHaveBeenCalledTimes(2);
    expect(container.querySelector('.gmap-fit').getAttribute('aria-pressed')).toBe('false');
  });

  it('ignores zoom changes caused by its own fitting but stops after a user zoom', async () => {
    await render();

    await act(async () => google.mapProps.onZoomChanged());
    await act(async () => window.dispatchEvent(new Event('resize')));
    expect(google.fitBounds).toHaveBeenCalledTimes(2);

    await act(async () => google.mapProps.onIdle());
    await act(async () => google.mapProps.onZoomChanged());
    await act(async () => window.dispatchEvent(new Event('resize')));
    expect(google.fitBounds).toHaveBeenCalledTimes(2);
  });

  it('fits all markers again and resumes auto-fit from the Fit all button', async () => {
    await render();
    await act(async () => google.mapProps.onIdle());
    await act(async () => google.mapProps.onDragstart());

    await act(async () => container.querySelector('.gmap-fit').click());
    expect(google.fitBounds).toHaveBeenCalledTimes(2);
    expect(container.querySelector('.gmap-fit').getAttribute('aria-pressed')).toBe('true');

    await act(async () => google.mapProps.onIdle());
    await act(async () => window.dispatchEvent(new Event('resize')));
    expect(google.fitBounds).toHaveBeenCalledTimes(3);
  });

  it('stops refitting on resize after the map is closed', async () => {
    await render();
    await act(async () => root.render(<div />));

    window.dispatchEvent(new Event('resize'));
    expect(google.fitBounds).toHaveBeenCalledTimes(1);
  });

  it('anchors every marker at its centre so the dot sits exactly on its coordinates', async () => {
    await render();

    const markers = [...container.querySelectorAll('.mock-marker')];
    expect(markers).toHaveLength(3);
    for (const marker of markers) {
      expect(marker.dataset.anchor).toBe(JSON.stringify(['50%', '50%']));
    }
    const restaurantMarker = container.querySelector('.gmap-restaurant').parentElement;
    expect(JSON.parse(restaurantMarker.dataset.position)).toEqual({ lat: 45.5048, lng: -73.5772 });
  });

  it('keeps restaurant markers in place when labels are toggled', async () => {
    await render();
    const before = [...container.querySelectorAll('.gmap-restaurant')]
      .map((pin) => [pin.parentElement.dataset.position, pin.parentElement.dataset.anchor]);

    await act(async () => container.querySelector('.map-toggle input').click());
    const after = [...container.querySelectorAll('.gmap-restaurant')]
      .map((pin) => [pin.parentElement.dataset.position, pin.parentElement.dataset.anchor]);

    expect(container.querySelector('.gmap-label')).toBeNull();
    expect(after).toEqual(before);
  });

  it('draws couriers above restaurants without blocking clicks on them', async () => {
    await render();

    const courier = container.querySelector('.gmap-courier').parentElement;
    const restaurants = [...container.querySelectorAll('.gmap-restaurant')].map((pin) => pin.parentElement);
    expect(courier.dataset.clickable).toBe('false');
    for (const marker of restaurants) {
      expect(Number(courier.dataset.z)).toBeGreaterThan(Number(marker.dataset.z));
    }
  });

  it('brings a hovered or selected restaurant and its label to the front', async () => {
    await render();
    const [first, second] = [...container.querySelectorAll('.gmap-restaurant')].map((pin) => pin.parentElement);
    const courierZ = Number(container.querySelector('.gmap-courier').parentElement.dataset.z);
    expect(first.dataset.z).toBe(second.dataset.z);

    await act(async () => second.dispatchEvent(new MouseEvent('mouseover', { bubbles: true })));
    expect(Number(second.dataset.z)).toBeGreaterThan(courierZ);
    expect(Number(second.dataset.z)).toBeGreaterThan(Number(first.dataset.z));

    await act(async () => second.dispatchEvent(new MouseEvent('mouseout', { bubbles: true })));
    expect(second.dataset.z).toBe(first.dataset.z);

    await act(async () => container.querySelector('.gmap-restaurant').click());
    const selected = container.querySelector('.gmap-restaurant.is-selected').parentElement;
    expect(Number(selected.dataset.z)).toBeGreaterThan(courierZ);
  });

  it('hides and shows labels with the toggle', async () => {
    await render();
    await act(async () => container.querySelector('.map-toggle input').click());

    expect(container.querySelector('.gmap-label')).toBeNull();
    expect(container.querySelectorAll('.gmap-dot')).toHaveLength(2);
  });

  it('opens the details panel when a restaurant marker is clicked', async () => {
    await render();
    await act(async () => container.querySelector('.gmap-restaurant').click());

    expect(container.querySelector('.restaurant-details h3').textContent).toBe('Bobs Burgers');
    expect(container.querySelector('.gmap-restaurant').classList.contains('is-selected')).toBe(true);
  });

  it('shows Montreal without fitting when the city is empty', async () => {
    api.cityMap.mockResolvedValue({ bounds: null, restaurants: [], couriers: [] });
    await render();

    expect(container.querySelector('.mock-map')).not.toBeNull();
    expect(google.mapProps.defaultCenter).toEqual({ lat: 45.5017, lng: -73.5673 });
    expect(container.querySelector('.gmap-fit')).toBeNull();
    expect(container.querySelector('.map-empty').textContent).toBe('No restaurants or couriers to display yet.');
    expect(google.fitBounds).not.toHaveBeenCalled();
  });

  it('falls back to the simple map and logs once when Google rejects the key', async () => {
    google.status = 'AUTH_FAILURE';
    await render();

    expect(container.querySelector('.mock-map')).toBeNull();
    expect(container.querySelectorAll('.map-canvas .map-restaurant')).toHaveLength(2);
    const logged = [...container.querySelectorAll('.output-console li, .output-console [role="listitem"]')]
      .map((entry) => entry.textContent)
      .filter((text) => text.includes('Load Google map'));
    expect(logged).toHaveLength(1);
    expect(logged[0]).toContain('Google Maps rejected the API key; showing the simple map instead.');
  });

  it('uses the simple map when no API key is configured', async () => {
    await render({ googleMapsApiKey: '' });

    expect(container.querySelector('.mock-map')).toBeNull();
    expect(container.querySelectorAll('.map-canvas .map-restaurant')).toHaveLength(2);
  });
});
