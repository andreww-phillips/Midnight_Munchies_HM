// @vitest-environment jsdom
import React, { act } from 'react';
import { createRoot } from 'react-dom/client';
import { afterEach, beforeEach, expect, it, vi } from 'vitest';
import { api } from '../../../api';
import RestaurantList from './restaurant_library';
import { CommandLogProvider } from '../output_console/command_log_provider';
import OutputConsole from '../output_console/output_console';

vi.mock('../../../api', () => ({ api: {
  restaurants: vi.fn(), changeRestaurantStatus: vi.fn(), orderHistory: vi.fn(),
  orders: vi.fn(), preparationQueue: vi.fn(), menu: vi.fn(), restaurantReviews: vi.fn()
} }));

const owner = { id: 'staff-1', role: 'RESTAURANT_STAFF' };
const operator = { id: 'operator-1', role: 'PLATFORM_OPERATOR' };
const diner = { id: 'r1', ownerId: owner.id, name: 'Diner', status: 'OPEN' };
const other = { id: 'r2', ownerId: 'staff-2', name: 'Other diner', status: 'OPEN' };
let container, root;
const button = (label) => [...container.querySelectorAll('button')]
  .find((element) => (element.getAttribute('aria-label') || element.textContent) === label);
const cards = () => [...container.querySelectorAll('.restaurant-card')];
const logs = () => [...container.querySelectorAll('.console-entry')];

async function render(user = owner, props = {}) {
  await act(async () => root.render(
    <CommandLogProvider><RestaurantList user={user} {...props} /><OutputConsole /></CommandLogProvider>
  ));
}
async function click(element) {
  expect(element).toBeDefined();
  await act(async () => element.click());
}

beforeEach(() => {
  vi.resetAllMocks();
  vi.stubGlobal('IS_REACT_ACT_ENVIRONMENT', true);
  api.restaurants.mockResolvedValue([diner, other]);
  api.orderHistory.mockResolvedValue([]);
  api.orders.mockResolvedValue([]);
  api.preparationQueue.mockResolvedValue([]);
  api.menu.mockResolvedValue([]);
  api.restaurantReviews.mockResolvedValue({ averageRating: null, reviewCount: 0, reviews: [] });
  container = document.createElement('div');
  document.body.append(container);
  root = createRoot(container);
});

afterEach(async () => {
  await act(async () => root.unmount());
  container.remove();
  vi.unstubAllGlobals();
});

it('allows staff to pause only their own restaurant, logs success, and keeps preparation navigation', async () => {
  api.changeRestaurantStatus.mockResolvedValue({ ...diner, status: 'PAUSED' });
  await render();
  expect(button('Pause Diner')).toBeDefined();
  expect(button('Pause Other diner')).toBeUndefined();
  await click(button('Pause Diner'));

  expect(api.changeRestaurantStatus).toHaveBeenCalledExactlyOnceWith(diner.id, 'PAUSED');
  expect(cards()[0].textContent).toContain('Status: PAUSED');
  expect(button('Pause Diner')).toBeUndefined();
  expect(button('Reopen Diner')).toBeUndefined();
  expect(logs()).toHaveLength(1);
  expect(logs()[0].textContent).toContain('Success · Pause restaurant');
  await click(button('Manage orders'));
  expect(container.querySelector('.restaurant-dashboard')).not.toBeNull();
  expect(api.preparationQueue).toHaveBeenCalledWith(diner.id);
});

it('shows each restaurant rating and review count in the dashboard list', async () => {
  api.restaurantReviews.mockImplementation(async (id) => ({
    targetId: id, averageRating: id === diner.id ? 3.5 : null,
    reviewCount: id === diner.id ? 2 : 0, reviews: []
  }));
  await render();

  expect(cards()[0].textContent).toContain('Restaurant reviews');
  expect(cards()[0].textContent).toContain('3.5 / 5');
  expect(cards()[0].textContent).toContain('2 reviews');
  expect(cards()[0].querySelector('.review-list')).toBeNull();
});

it.each(['CUSTOMER', 'COURIER'])('shows no status commands to %s', async (role) => {
  await render({ id: owner.id, role });
  expect(button('Pause Diner')).toBeUndefined();
  expect(button('Pause Other diner')).toBeUndefined();
  expect(button('Reopen Diner')).toBeUndefined();
});

it('does not offer reopening to staff when their restaurant is paused', async () => {
  api.restaurants.mockResolvedValue([{ ...diner, status: 'PAUSED' }, other]);
  await render();
  expect(cards()[0].textContent).toContain('Status: PAUSED');
  expect(button('Pause Diner')).toBeUndefined();
  expect(button('Reopen Diner')).toBeUndefined();
});

it('lets an operator pause and reopen a restaurant without owning it', async () => {
  api.changeRestaurantStatus.mockResolvedValueOnce({ ...diner, status: 'PAUSED' }).mockResolvedValueOnce(diner);
  await render(operator);
  expect(button('Pause Other diner')).toBeDefined();
  await click(button('Pause Diner'));
  await click(button('Reopen Diner'));

  expect(api.changeRestaurantStatus.mock.calls).toEqual([[diner.id, 'PAUSED'], [diner.id, 'OPEN']]);
  expect(cards()[0].textContent).toContain('Status: OPEN');
  expect(logs()).toHaveLength(2);
  expect(logs()[1].textContent).toContain('Success · Reopen restaurant');
});

it('allows an operator to reopen a closed restaurant', async () => {
  api.restaurants.mockResolvedValue([{ ...diner, status: 'CLOSED' }]);
  api.changeRestaurantStatus.mockResolvedValue(diner);
  await render(operator);
  await click(button('Reopen Diner'));
  expect(api.changeRestaurantStatus).toHaveBeenCalledWith(diner.id, 'OPEN');
});

it('blocks duplicate commands and other status changes while a command is pending', async () => {
  let resolve;
  api.changeRestaurantStatus.mockReturnValue(new Promise((finish) => { resolve = finish; }));
  await render(operator);
  const pause = button('Pause Diner');
  await act(async () => { pause.click(); pause.click(); });
  expect(api.changeRestaurantStatus).toHaveBeenCalledTimes(1);
  expect(pause.disabled).toBe(true);
  expect(button('Pause Other diner').disabled).toBe(true);
  await act(async () => resolve({ ...diner, status: 'PAUSED' }));
  expect(button('Reopen Diner').disabled).toBe(false);
});

it('keeps the previous status and records the actual backend rejection', async () => {
  api.changeRestaurantStatus.mockRejectedValue(Object.assign(new Error('Staff can only pause their own restaurant'), { status: 403 }));
  await render();
  await click(button('Pause Diner'));
  expect(cards()[0].textContent).toContain('Status: OPEN');
  expect(container.querySelector('[role="alert"]').textContent).toContain('Staff can only pause');
  expect(button('Pause Diner').disabled).toBe(false);
  expect(logs()).toHaveLength(1);
  expect(logs()[0].textContent).toContain('Error · Pause restaurant');
  expect(logs()[0].textContent).toContain('HTTP 403');
});

it('reports an expired session after a rejected status command', async () => {
  const onSessionExpired = vi.fn();
  api.changeRestaurantStatus.mockRejectedValue(Object.assign(new Error('Authentication is required'), { status: 401 }));
  await render(owner, { onSessionExpired });
  await click(button('Pause Diner'));
  expect(onSessionExpired).toHaveBeenCalledWith('Authentication is required');
  expect(logs()[0].textContent).toContain('HTTP 401');
});
