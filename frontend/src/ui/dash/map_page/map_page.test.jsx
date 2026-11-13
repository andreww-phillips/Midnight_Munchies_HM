// @vitest-environment jsdom
import React, { act } from 'react';
import { createRoot } from 'react-dom/client';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { api } from '../../../api';
import { CommandLogProvider } from '../output_console/command_log_provider';
import { useCommandLog } from '../output_console/command_log';
import OutputConsole from '../output_console/output_console';
import MapPage from './map_page';

vi.mock('../../../api', () => ({ api: { cityMap: vi.fn(), orders: vi.fn(), menu: vi.fn(), operatorDeliveries: vi.fn() } }));

const courier = { id: 'courier-1', role: 'COURIER', username: 'courier@example.com' };
const marker = (overrides) => ({
  id: 'r1', name: 'Bobs Burgers', status: 'OPEN', latitude: 45.5048, longitude: -73.5772,
  pendingCount: 0, queuedCount: 2, preparingCount: 0, readyCount: 0, band: 'GREEN', ...overrides
});
const cityMap = (restaurants, couriers = []) => ({
  bounds: restaurants.length || couriers.length
    ? { minLat: 45.5048, maxLat: 45.5225, minLon: -73.5848, maxLon: -73.5772 } : null,
  restaurants, couriers
});

let container, root;
const logger = { record: () => {} };
function Logger() {
  const { record } = useCommandLog();
  React.useEffect(() => { logger.record = record; }, [record]);
  return null;
}
async function render(props = {}) {
  await act(async () => root.render(
    <CommandLogProvider>
      <Logger />
      <MapPage user={courier} pollMs={0} {...props} />
      <OutputConsole />
    </CommandLogProvider>
  ));
}
const restaurantMarkers = () => [...container.querySelectorAll('.map-restaurant')];
const labels = () => [...container.querySelectorAll('.map-label')].map((element) => element.textContent);

beforeEach(() => {
  vi.resetAllMocks();
  vi.stubGlobal('IS_REACT_ACT_ENVIRONMENT', true);
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
  vi.useRealTimers();
  vi.unstubAllGlobals();
});

describe('city map', () => {
  it('draws restaurants coloured by band with name and queued count labels', async () => {
    api.cityMap.mockResolvedValue(cityMap([
      marker({ id: 'r1', name: 'Bobs Burgers', queuedCount: 2, band: 'GREEN' }),
      marker({ id: 'r2', name: 'Pizza Palace', queuedCount: 4, band: 'AMBER', latitude: 45.5225, longitude: -73.5848 }),
      marker({ id: 'r3', name: 'Paused Diner', queuedCount: 0, band: 'RED', status: 'PAUSED', latitude: 45.51, longitude: -73.58 })
    ]));
    await render();

    expect(restaurantMarkers().map((element) => element.getAttribute('class'))).toEqual([
      'map-restaurant band-green', 'map-restaurant band-amber', 'map-restaurant band-red'
    ]);
    expect(labels()).toEqual(['Bobs Burgers · 2 queued', 'Pizza Palace · 4 queued', 'Paused Diner · 0 queued']);
    expect(restaurantMarkers()[0].querySelector('circle').getAttribute('fill')).toBe('#2e9e5b');
    expect(restaurantMarkers()[1].querySelector('circle').getAttribute('fill')).toBe('#e0a100');
    expect(restaurantMarkers()[2].querySelector('circle').getAttribute('fill')).toBe('#d64545');
    const legend = container.querySelector('.map-legend').textContent;
    expect(legend).toContain('Short (green): fewer than 3 queued');
    expect(legend).toContain('Building (amber): 3 to 5 queued');
    expect(legend).toContain('Long (red): 6 or more queued, or paused/closed');
  });

  it('alerts the operator about restaurants in the red band', async () => {
    api.cityMap.mockResolvedValue(cityMap([
      marker({ id: 'r1', name: 'Bobs Burgers', queuedCount: 2, band: 'GREEN' }),
      marker({ id: 'r2', name: 'Pizza Palace', queuedCount: 6, band: 'RED' }),
      marker({ id: 'r3', name: 'Paused Diner', queuedCount: 0, band: 'RED', status: 'PAUSED' })
    ]));
    await render({ user: { id: 'operator-1', role: 'PLATFORM_OPERATOR' } });

    const alerts = [...container.querySelectorAll('.map-alert li')].map((element) => element.textContent);
    expect(alerts).toEqual(['Pizza Palace: 6 queued', 'Paused Diner: PAUSED']);
  });

  it('lists orders waiting for a courier to the operator', async () => {
    api.cityMap.mockResolvedValue({ ...cityMap([marker()]), awaitingCourier: [
      { orderId: 'aaaaaaaa-0000', restaurantId: 'r1', restaurantName: 'Bobs Burgers' },
      { orderId: 'bbbbbbbb-0000', restaurantId: 'r9', restaurantName: null }
    ] });
    await render({ user: { id: 'operator-1', role: 'PLATFORM_OPERATOR' } });

    const waiting = [...container.querySelectorAll('.map-waiting li')].map((element) => element.textContent);
    expect(waiting).toEqual(['Order #aaaaaaaa · Bobs Burgers', 'Order #bbbbbbbb · Unknown restaurant']);
  });

  it('does not list waiting orders to other roles', async () => {
    api.cityMap.mockResolvedValue({ ...cityMap([marker()]), awaitingCourier: [
      { orderId: 'aaaaaaaa-0000', restaurantId: 'r1', restaurantName: 'Bobs Burgers' }
    ] });
    await render();

    expect(container.querySelector('.map-waiting')).toBeNull();
  });

  it('does not alert the operator when no restaurant is red', async () => {
    api.cityMap.mockResolvedValue(cityMap([marker({ band: 'AMBER', queuedCount: 4 })]));
    await render({ user: { id: 'operator-1', role: 'PLATFORM_OPERATOR' } });

    expect(container.querySelector('.map-alert')).toBeNull();
  });

  it('does not show operator alerts to other roles', async () => {
    api.cityMap.mockResolvedValue(cityMap([marker({ band: 'RED', queuedCount: 7 })]));
    await render();

    expect(container.querySelector('.map-alert')).toBeNull();
  });

  it('hides and shows labels with the toggle', async () => {
    api.cityMap.mockResolvedValue(cityMap([marker()]));
    await render();

    const toggle = container.querySelector('.map-toggle input');
    await act(async () => toggle.click());
    expect(labels()).toEqual([]);
    await act(async () => toggle.click());
    expect(labels()).toEqual(['Bobs Burgers · 2 queued']);
  });

  it('draws couriers with a badge for their state', async () => {
    api.cityMap.mockResolvedValue(cityMap([marker()], [
      { id: 'c1', latitude: 45.51, longitude: -73.58, status: 'IDLE' },
      { id: 'c2', latitude: 45.52, longitude: -73.579, status: 'CARRYING' }
    ]));
    await render();

    const couriers = [...container.querySelectorAll('.map-courier')];
    expect(couriers.map((element) => element.querySelector('.map-badge').textContent)).toEqual(['I', 'C']);
    expect(couriers[1].getAttribute('aria-label')).toBe('Courier c2, carrying');
  });

  it('shows a clear message when there is nothing to draw', async () => {
    api.cityMap.mockResolvedValue(cityMap([]));
    await render();

    expect(container.querySelector('.map-empty').textContent).toBe('No restaurants or couriers to display yet.');
    expect(container.querySelector('svg')).toBeNull();
  });

  it('shows and logs a failed load once without crashing', async () => {
    api.cityMap.mockRejectedValue(Object.assign(new Error('Server down'), { status: 500 }));
    await render({ pollMs: 1000 });
    vi.useFakeTimers();
    await act(async () => { await vi.advanceTimersByTimeAsync(3000); });

    expect(container.querySelector('[role="alert"]').textContent).toBe('Map unavailable: Server down');
    const errors = [...container.querySelectorAll('.console-entry--error')];
    expect(errors).toHaveLength(1);
    expect(errors[0].textContent).toContain('Load map');
    expect(errors[0].textContent).toContain('HTTP 500');
  });

  it('reports an expired session instead of logging an error', async () => {
    const onSessionExpired = vi.fn();
    api.cityMap.mockRejectedValue(Object.assign(new Error('Please log in to continue.'), { status: 401 }));
    await render({ onSessionExpired });

    expect(onSessionExpired).toHaveBeenCalledWith('Please log in to continue.');
    expect(container.querySelectorAll('.console-entry--error')).toHaveLength(0);
  });

  it('opens the details panel when a restaurant is clicked', async () => {
    api.cityMap.mockResolvedValue(cityMap([marker()]));
    await render();

    await act(async () => restaurantMarkers()[0].dispatchEvent(new MouseEvent('click', { bubbles: true })));
    expect(container.querySelector('.restaurant-details h3').textContent).toBe('Bobs Burgers');
  });
});

describe('map refresh', () => {
  it('reloads after a successful command but not after a failed one', async () => {
    api.cityMap.mockResolvedValue(cityMap([marker()]));
    await render();
    expect(api.cityMap).toHaveBeenCalledTimes(1);

    api.cityMap.mockResolvedValue(cityMap([marker({ queuedCount: 3, band: 'AMBER' })]));
    await act(async () => logger.record({ action: 'Accept order', outcome: 'success', message: 'Accepted.' }));
    expect(api.cityMap).toHaveBeenCalledTimes(2);
    expect(labels()).toEqual(['Bobs Burgers · 3 queued']);

    await act(async () => logger.record({ action: 'Accept order', outcome: 'error', message: 'Nope.' }));
    expect(api.cityMap).toHaveBeenCalledTimes(2);
  });

  it('polls on an interval and stops when removed', async () => {
    api.cityMap.mockResolvedValue(cityMap([marker()]));
    vi.useFakeTimers();
    await render({ pollMs: 5000 });
    expect(api.cityMap).toHaveBeenCalledTimes(1);

    await act(async () => { await vi.advanceTimersByTimeAsync(5000); });
    expect(api.cityMap).toHaveBeenCalledTimes(2);

    await act(async () => root.render(<CommandLogProvider />));
    await act(async () => { await vi.advanceTimersByTimeAsync(15000); });
    expect(api.cityMap).toHaveBeenCalledTimes(2);
  });
});
