// @vitest-environment jsdom
import React, { act } from 'react';
import { createRoot } from 'react-dom/client';
import { afterEach, beforeEach, expect, it, vi } from 'vitest';
import { api } from '../../../api';
import RestaurantList from '../restaurant_library/restaurant_library';

vi.mock('../../../api', () => ({ api: {
  restaurants: vi.fn(), menu: vi.fn(), orderHistory: vi.fn(), placeOrder: vi.fn(), restaurantReviews: vi.fn()
} }));

const restaurant = { id: 'r1', name: 'Diner', status: 'OPEN' };
const item = { id: 'i1', restaurantId: 'r1', name: 'Meal', status: 'AVAILABLE', price: '10.00' };
let root, container;
const orderButton = () => container.querySelector('.order-button');
async function click(label) {
  const button = [...container.querySelectorAll('button')]
    .find((element) => (element.getAttribute('aria-label') || element.textContent) === label);
  expect(button).toBeDefined();
  await act(async () => button.click());
}
async function openMenu() {
  await act(async () => root.render(<RestaurantList user={{ id: 'customer', role: 'CUSTOMER' }} />));
  await click('Menu');
  await click('Add one Meal');
  expect(orderButton().disabled).toBe(false);
}
async function poll() { await act(async () => vi.advanceTimersByTimeAsync(5000)); }

beforeEach(() => {
  vi.resetAllMocks();
  vi.useFakeTimers();
  vi.stubGlobal('IS_REACT_ACT_ENVIRONMENT', true);
  api.restaurants.mockResolvedValue([restaurant]);
  api.menu.mockResolvedValue([item]);
  api.orderHistory.mockResolvedValue([]);
  api.restaurantReviews.mockResolvedValue({ averageRating: null, reviewCount: 0, reviews: [] });
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

it.each(['PAUSED', 'CLOSED'])('updates checkout when another session changes the restaurant to %s, preserving the cart', async (status) => {
  await openMenu();
  api.restaurants.mockResolvedValue([{ ...restaurant, status }]);
  await poll();
  expect(orderButton().disabled).toBe(true);
  await act(async () => orderButton().click());
  expect(api.placeOrder).not.toHaveBeenCalled();
  api.restaurants.mockResolvedValue([restaurant]);
  await poll();
  expect(orderButton().disabled).toBe(false);
  expect(orderButton().textContent).toContain('$10.00');
});

it('removes sold-out selections on the next poll and requires a new selection when available again', async () => {
  await openMenu();
  api.menu.mockResolvedValue([{ ...item, status: 'SOLD_OUT' }]);
  await poll();
  expect(orderButton().disabled).toBe(true);
  expect(orderButton().textContent).toContain('$0.00');
  api.menu.mockResolvedValue([item]);
  await poll();
  expect(orderButton().disabled).toBe(true);
  await click('Add one Meal');
  expect(orderButton().disabled).toBe(false);
});

it.each(['restaurant', 'menu'])('blocks checkout after a %s availability failure until a successful refresh', async (source) => {
  await openMenu();
  const endpoint = source === 'restaurant' ? api.restaurants : api.menu;
  endpoint.mockRejectedValue(new Error('Availability unavailable'));
  await poll();
  expect(orderButton().disabled).toBe(true);
  expect(container.querySelector('[role="alert"]').textContent).toContain('Availability unavailable');
  endpoint.mockResolvedValue(source === 'restaurant' ? [restaurant] : [item]);
  await poll();
  expect(orderButton().disabled).toBe(false);
});

it('stops availability polling after leaving the menu', async () => {
  await openMenu();
  await click('Back to restaurants');
  const counts = [api.restaurants.mock.calls.length, api.menu.mock.calls.length];
  await poll();
  expect([api.restaurants.mock.calls.length, api.menu.mock.calls.length]).toEqual(counts);
});

it('ignores an old menu poll that resolves after an explicit refresh', async () => {
  await openMenu();
  let resolve;
  api.menu.mockReturnValueOnce(new Promise((finish) => { resolve = finish; }));
  await poll();
  api.menu.mockResolvedValue([{ ...item, status: 'SOLD_OUT' }]);
  await click('Refresh menu');
  await act(async () => resolve([item]));
  expect(orderButton().disabled).toBe(true);
  expect(container.textContent).toContain('Sold out');
});
