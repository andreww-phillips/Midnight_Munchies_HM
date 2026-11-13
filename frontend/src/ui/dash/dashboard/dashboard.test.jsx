// @vitest-environment jsdom
import React, { act } from 'react';
import { createRoot } from 'react-dom/client';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { api } from '../../../api';
import { CommandLogProvider } from '../output_console/command_log_provider';
import OutputConsole from '../output_console/output_console';
import Dashboard from './dashboard';

vi.mock('../../../api', () => ({ api: {
  cityMap: vi.fn(), restaurants: vi.fn(), orders: vi.fn(), menu: vi.fn(), resetSimulation: vi.fn()
} }));

const operator = { id: 'operator-1', username: 'operator@example.com', role: 'PLATFORM_OPERATOR' };
const courier = { id: 'courier-1', username: 'courier@example.com', role: 'COURIER' };
const cityMap = {
  bounds: { minLat: 45.5048, maxLat: 45.5225, minLon: -73.5848, maxLon: -73.5772 },
  restaurants: [{
    id: 'r1', ownerId: 'staff-1', name: 'Bobs Burgers', status: 'OPEN', latitude: 45.5048, longitude: -73.5772,
    pendingCount: 1, queuedCount: 4, preparingCount: 2, readyCount: 1, band: 'AMBER'
  }],
  couriers: []
};

let container, root;
const tab = (text) => [...container.querySelectorAll('[role="tab"]')].find((element) => element.textContent === text);
const button = (text) => [...container.querySelectorAll('button')].find((element) => element.textContent === text);
async function click(element) { await act(async () => element.click()); }
async function render(user) {
  await act(async () => root.render(
    <CommandLogProvider>
      <Dashboard user={user} />
      <OutputConsole />
    </CommandLogProvider>
  ));
}

beforeEach(() => {
  vi.resetAllMocks();
  vi.stubGlobal('IS_REACT_ACT_ENVIRONMENT', true);
  api.cityMap.mockResolvedValue(cityMap);
  api.restaurants.mockResolvedValue([]);
  api.orders.mockResolvedValue([]);
  api.menu.mockResolvedValue([]);
  container = document.createElement('div');
  document.body.append(container);
  root = createRoot(container);
});

afterEach(async () => {
  await act(async () => root.unmount());
  container.remove();
  vi.unstubAllGlobals();
});

describe('dashboard', () => {
  it('shows a horizontal tab bar for every module', async () => {
    await render(courier);

    expect(container.querySelector('.tab-bar').getAttribute('role')).toBe('tablist');
    await click(tab('Delivery'));
    expect(container.querySelector('#dashboard-panel').textContent).toContain('The delivery module is not available yet.');
    await click(tab('Pricing & History'));
    expect(container.querySelector('#dashboard-panel').textContent).toContain('Pricing is not available yet.');
    expect(tab('Pricing & History').getAttribute('aria-selected')).toBe('true');
  });

  it('shows the user, role and selected restaurant stats in the information area', async () => {
    await render(courier);
    const info = () => container.querySelector('.info-area').textContent;

    expect(info()).toContain('courier@example.com');
    expect(info()).toContain('Role: COURIER');
    expect(info()).toContain('Click a restaurant on the map.');

    await click(tab('Map'));
    await act(async () => container.querySelector('.map-restaurant').dispatchEvent(new MouseEvent('click', { bubbles: true })));
    const stats = [...container.querySelectorAll('.info-stats div')].map((element) => element.textContent);
    expect(container.querySelector('.info-restaurant-name').textContent).toBe('Bobs Burgers');
    expect(stats).toEqual(['Queued4', 'In preparation2', 'Ready1']);
  });

  it('toggles map labels from the information area', async () => {
    await render(courier);
    await click(tab('Map'));
    const labels = () => [...container.querySelectorAll('.map-label')].map((element) => element.textContent);

    expect(container.querySelector('.city-map .map-toggle')).toBeNull();
    expect(labels()).toEqual(['Bobs Burgers · 4 queued']);
    await click(container.querySelector('.info-area .map-toggle input'));
    expect(labels()).toEqual([]);
  });

  it('offers Reset only to the operator', async () => {
    await render(courier);
    expect(button('Reset')).toBeUndefined();
    await render(operator);
    expect(button('Reset')).toBeDefined();
  });

  it('resets after confirmation, logs it and reloads the map', async () => {
    vi.stubGlobal('confirm', vi.fn(() => true));
    api.resetSimulation.mockResolvedValue(null);
    await render(operator);
    await click(tab('Map'));
    await act(async () => container.querySelector('.map-restaurant').dispatchEvent(new MouseEvent('click', { bubbles: true })));
    const loadsBefore = api.cityMap.mock.calls.length;

    await click(button('Reset'));

    expect(api.resetSimulation).toHaveBeenCalledTimes(1);
    expect(api.cityMap.mock.calls.length).toBeGreaterThan(loadsBefore);
    expect(container.querySelector('.console-entry--success').textContent).toContain('Initial scenario restored.');
    expect(container.querySelector('.info-area').textContent).toContain('Click a restaurant on the map.');
  });

  it('does nothing when the reset is cancelled', async () => {
    vi.stubGlobal('confirm', vi.fn(() => false));
    await render(operator);

    await click(button('Reset'));

    expect(api.resetSimulation).not.toHaveBeenCalled();
  });

  it('logs a failed reset', async () => {
    vi.stubGlobal('confirm', vi.fn(() => true));
    api.resetSimulation.mockRejectedValue(Object.assign(new Error('Reset is only available with the in-memory simulator data'), { status: 409 }));
    await render(operator);

    await click(button('Reset'));

    const error = container.querySelector('.console-entry--error').textContent;
    expect(error).toContain('Reset simulation');
    expect(error).toContain('Reset is only available with the in-memory simulator data');
    expect(button('Reset').disabled).toBe(false);
  });
});
