// @vitest-environment jsdom
import React, { act, StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
import { afterEach, beforeEach, expect, it, vi } from 'vitest';
import { api } from '../../../api';
import RegistrationPage from './registration_page';

vi.mock('../../../api', () => ({ api: {
  currentUser: vi.fn(), resetSimulator: vi.fn(), logout: vi.fn(),
  restaurants: vi.fn(), orders: vi.fn(), orderHistory: vi.fn(), orderEvents: vi.fn(), menu: vi.fn(),
  courierDashboard: vi.fn(), operatorDeliveries: vi.fn(), reassignDelivery: vi.fn(), cityMap: vi.fn()
} }));

const operator = { id: 'seed-operator', role: 'PLATFORM_OPERATOR', username: 'operator@example.com' };
const restaurant = { id: 'r1', name: 'Diner', status: 'PAUSED', latitude: 45.5, longitude: -73.5,
  queuedCount: 1, readyCount: 1, pendingCount: 0, preparingCount: 0, band: 'RED' };
const restored = { status: 'RESET', revision: 1, message: 'Initial simulator state restored.' };
const oldOrder = { orderId: 'old-order-1', restaurantId: 'r1', status: 'READY', orderLines: [] };
const cityMap = (marker = restaurant) => ({
  bounds: { minLat: 45.5, maxLat: 45.5, minLon: -73.5, maxLon: -73.5 },
  restaurants: [marker], couriers: [], awaitingCourier: []
});

let root, container;
const button = (label) => [...container.querySelectorAll('button')].find((node) => node.textContent === label);
const output = () => container.querySelector('[role="log"]');
const entries = () => [...container.querySelectorAll('.console-entry')];
const failure = (status, message = 'Reset unavailable') => Object.assign(new Error(message), { status });
function deferred() {
  let resolve, reject;
  const promise = new Promise((done, fail) => { resolve = done; reject = fail; });
  return { promise, resolve, reject };
}
async function click(node) { await act(async () => node.click()); }
async function render() { await act(async () => root.render(<StrictMode><RegistrationPage /></StrictMode>)); }
async function reset() {
  await click(button('Reset simulator'));
  await click(button('Confirm reset'));
}

beforeEach(() => {
  vi.resetAllMocks();
  vi.stubGlobal('IS_REACT_ACT_ENVIRONMENT', true);
  api.currentUser.mockResolvedValue(operator);
  api.resetSimulator.mockResolvedValue(restored);
  api.restaurants.mockResolvedValue([restaurant]);
  api.orders.mockResolvedValue([]);
  api.orderHistory.mockResolvedValue([]);
  api.orderEvents.mockResolvedValue([]);
  api.menu.mockResolvedValue([]);
  api.operatorDeliveries.mockResolvedValue({ active: [], queued: ['old-order-1'] });
  api.courierDashboard.mockResolvedValue({ onDuty: false, state: 'OFFLINE', assignment: null });
  api.cityMap.mockResolvedValue(cityMap());
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

it.each(['CUSTOMER', 'RESTAURANT_STAFF', 'COURIER'])('hides reset for %s', async (role) => {
  api.currentUser.mockResolvedValue({ ...operator, role });
  await render();
  expect(button('Reset simulator')).toBeUndefined();
  expect(api.resetSimulator).not.toHaveBeenCalled();
});

it('hides reset when logged out', async () => {
  api.currentUser.mockRejectedValue(failure(401));
  await render();
  expect(button('Reset simulator')).toBeUndefined();
});

it('requires confirmation and cancellation leaves state and console unchanged', async () => {
  await render();
  const reads = api.restaurants.mock.calls.length;
  await click(button('Reset simulator'));
  expect(container.textContent).toContain('remove accounts created since startup');
  expect(api.resetSimulator).not.toHaveBeenCalled();
  await click(button('Cancel'));
  expect(button('Reset simulator')).toBeDefined();
  expect(api.restaurants).toHaveBeenCalledTimes(reads);
  expect(entries()).toHaveLength(0);
});

it('refreshes restaurants and delivery management, closes menus and replaces old console history', async () => {
  await render();
  await click(container.querySelector('.operator-dashboard .section-heading button'));
  expect(entries()).toHaveLength(1);
  await click(button('Menu'));
  expect(container.querySelector('.restaurant-menu')).not.toBeNull();
  api.restaurants.mockResolvedValue([{ ...restaurant, status: 'OPEN' }]);
  api.operatorDeliveries.mockResolvedValue({ active: [], queued: [] });
  await reset();
  expect(api.resetSimulator).toHaveBeenCalledExactlyOnceWith();
  expect(container.querySelector('.restaurant-menu')).toBeNull();
  expect(container.querySelector('.restaurant-card').textContent).toContain('Status: OPEN');
  expect(container.querySelector('.operator-dashboard').textContent).toContain('No orders waiting for a courier.');
  expect(container.textContent).not.toContain('old-order-1');
  expect(entries()).toHaveLength(1);
  expect(output().textContent).toContain('Success');
  expect(output().textContent).toContain('Reset simulator');
  expect(output().textContent).not.toContain('Refresh delivery management');
  expect(button('Log out').disabled).toBe(false);
});

it('refreshes map markers and closes details, ignoring a late detail response', async () => {
  const oldMenu = deferred();
  api.menu.mockReturnValue(oldMenu.promise);
  await render();
  await click(button('Map'));
  await act(async () => container.querySelector('.map-restaurant').dispatchEvent(new MouseEvent('click', { bubbles: true })));
  expect(container.querySelector('.restaurant-details')).not.toBeNull();
  api.cityMap.mockResolvedValue(cityMap({ ...restaurant, status: 'OPEN', band: 'GREEN', queuedCount: 0, readyCount: 0 }));
  await reset();
  await act(async () => oldMenu.resolve([{ id: 'old-item', name: 'Stale dish', price: 99 }]));
  expect(button('Map').getAttribute('aria-pressed')).toBe('true');
  expect(container.querySelector('.map-restaurant').classList.contains('band-green')).toBe(true);
  expect(container.querySelector('.restaurant-details')).toBeNull();
  expect(container.textContent).not.toContain('Stale dish');
  expect(container.querySelector('.map-alert')).toBeNull();
});

it('reloads history and removes cleared orders and their details', async () => {
  api.orderHistory.mockResolvedValue([oldOrder]);
  await render();
  await click(button('Pricing & History'));
  expect(container.querySelector('.order-card')).not.toBeNull();
  api.orderHistory.mockResolvedValue([]);
  await reset();
  expect(button('Pricing & History').getAttribute('aria-pressed')).toBe('true');
  expect(container.querySelector('.order-card')).toBeNull();
  expect(container.querySelector('.order-history').textContent).toContain('No orders yet.');
});

it('blocks duplicate resets, navigation, logout and dashboard polling while pending', async () => {
  vi.useFakeTimers();
  const pending = deferred();
  api.resetSimulator.mockReturnValue(pending.promise);
  await render();
  await click(button('Reset simulator'));
  const confirm = button('Confirm reset');
  await act(async () => { confirm.click(); confirm.click(); });
  expect(api.resetSimulator).toHaveBeenCalledTimes(1);
  expect(button('Log out').disabled).toBe(true);
  expect(button('Map').disabled).toBe(true);
  expect(button('Resetting simulator...').disabled).toBe(true);
  expect(container.querySelector('.operator-dashboard')).toBeNull();
  const reads = api.operatorDeliveries.mock.calls.length;
  await act(async () => vi.advanceTimersByTimeAsync(15000));
  expect(api.operatorDeliveries).toHaveBeenCalledTimes(reads);
  await act(async () => pending.resolve(restored));
  expect(button('Map').disabled).toBe(false);
  expect(button('Reset simulator').disabled).toBe(false);
  expect(entries()).toHaveLength(1);
});

it.each([403, 409, 500, undefined])('logs reset failure HTTP %s without a false success and allows retry', async (status) => {
  await render();
  await click(container.querySelector('.operator-dashboard .section-heading button'));
  api.resetSimulator.mockRejectedValueOnce(failure(status));
  await reset();
  expect(container.querySelector('[role="alert"]').textContent).toBe('Reset unavailable');
  expect(output().textContent).toContain('Refresh delivery management');
  expect(container.querySelectorAll('.console-entry--error')).toHaveLength(1);
  expect(container.querySelector('.console-entry--error').textContent).toContain('Reset simulator');
  expect(container.querySelector('.restaurant-card').textContent).toContain('Status: PAUSED');
  await reset();
  expect(api.resetSimulator).toHaveBeenCalledTimes(2);
  expect(entries()).toHaveLength(1);
  expect(output().textContent).toContain('Success');
});

it('returns to login and preserves the reset error when the command is unauthorized', async () => {
  await render();
  api.resetSimulator.mockRejectedValue(failure(401, 'Authentication is required'));
  await reset();
  expect(container.querySelector('.auth-card')).not.toBeNull();
  expect(button('Reset simulator')).toBeUndefined();
  expect(output().textContent).toContain('Reset simulator');
  expect(output().textContent).toContain('HTTP 401');
  expect(container.querySelector('.console-entry--success')).toBeNull();
});

it.each([401, 503])('keeps reset success separate from a subsequent session HTTP %s failure', async (status) => {
  await render();
  api.currentUser.mockRejectedValue(failure(status, 'Session unavailable'));
  await reset();
  expect(container.querySelector('.auth-card')).not.toBeNull();
  expect(container.textContent).toContain('Reset succeeded');
  expect(entries()).toHaveLength(2);
  expect(entries()[0].textContent).toContain('Reset simulator');
  expect(entries()[0].className).toContain('--success');
  expect(entries()[1].textContent).toContain('Refresh session after reset');
  expect(entries()[1].textContent).toContain(`HTTP ${status}`);
  expect(api.resetSimulator).toHaveBeenCalledTimes(1);
});

it('logs a failed dashboard refresh separately from the successful reset', async () => {
  await render();
  api.restaurants.mockRejectedValue(failure(503, 'Catalog unavailable'));
  await reset();
  expect(output().textContent).toContain('Reset simulator');
  expect(output().textContent).toContain('Load restaurants');
  expect(container.querySelectorAll('.console-entry--success')).toHaveLength(1);
  expect(container.querySelectorAll('.console-entry--error')).toHaveLength(1);
  expect(api.resetSimulator).toHaveBeenCalledTimes(1);
});

it('ignores late commands and session expiry callbacks from the dashboard before reset', async () => {
  const command = deferred();
  api.operatorDeliveries.mockResolvedValue({ active: [{ orderId: 'old-order-1', courierId: 'courier-1',
    status: 'ACCEPTED', waitingSeconds: 10, reassignable: true }], queued: [] });
  api.reassignDelivery.mockReturnValue(command.promise);
  await render();
  await click(button('Reassign'));
  api.operatorDeliveries.mockResolvedValue({ active: [], queued: [] });
  await reset();
  await act(async () => command.reject(failure(401, 'Old session failure')));
  expect(container.querySelector('.auth-card')).toBeNull();
  expect(container.textContent).toContain('Welcome, operator@example.com');
  expect(entries()).toHaveLength(1);
  expect(output().textContent).not.toContain('Old session failure');
});
