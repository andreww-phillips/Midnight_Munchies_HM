// @vitest-environment jsdom
import React, { act } from 'react';
import { createRoot } from 'react-dom/client';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { api } from '../../../api';
import { CommandLogContext } from '../output_console/command_log';
import RestaurantDetails from './restaurant_details';

vi.mock('../../../api', () => ({ api: { orders: vi.fn(), menu: vi.fn(), pauseRestaurant: vi.fn(), reopenRestaurant: vi.fn() } }));

const restaurant = {
  id: 'r1', ownerId: 'staff-1', name: 'Bobs Burgers', status: 'OPEN', latitude: 45.5, longitude: -73.5,
  pendingCount: 1, queuedCount: 2, preparingCount: 1, readyCount: 1, band: 'GREEN'
};
const users = {
  customer: { id: 'customer-1', role: 'CUSTOMER' },
  owner: { id: 'staff-1', role: 'RESTAURANT_STAFF' },
  otherStaff: { id: 'staff-2', role: 'RESTAURANT_STAFF' },
  courier: { id: 'courier-1', role: 'COURIER' },
  operator: { id: 'operator-1', role: 'PLATFORM_OPERATOR' }
};

let container, root;
const buttons = () => [...container.querySelectorAll('button')].map((element) => element.textContent);
const queueItems = () => [...container.querySelectorAll('.details-queue li')].map((element) => element.textContent);
const button = (text) => [...container.querySelectorAll('button')].find((element) => element.textContent === text);
async function renderLogged(user, record, overrides = {}, props = {}) {
  await act(async () => root.render(
    <CommandLogContext.Provider value={{ entries: [], record, changeIdentity: () => {} }}>
      <RestaurantDetails restaurant={{ ...restaurant, ...overrides }} user={user} onClose={() => {}} {...props} />
    </CommandLogContext.Provider>
  ));
}
async function render(user, overrides = {}, props = {}) {
  await act(async () => root.render(
    <RestaurantDetails restaurant={{ ...restaurant, ...overrides }} user={user} onClose={() => {}} onOpenRestaurant={() => {}} {...props} />
  ));
}

beforeEach(() => {
  vi.resetAllMocks();
  vi.stubGlobal('IS_REACT_ACT_ENVIRONMENT', true);
  api.orders.mockResolvedValue([
    { id: 'ready-1', restaurantId: 'r1', status: 'READY', createdAt: '2026-11-12T23:00:00Z' },
    { id: 'queued-2', restaurantId: 'r1', status: 'ACCEPTED', acceptedAt: '2026-11-12T23:05:00Z' },
    { id: 'queued-1', restaurantId: 'r1', status: 'ACCEPTED', acceptedAt: '2026-11-12T23:01:00Z' },
    { id: 'placed-1', restaurantId: 'r1', status: 'PLACED', createdAt: '2026-11-12T23:06:00Z' },
    { id: 'done-1', restaurantId: 'r1', status: 'CANCELLED', createdAt: '2026-11-12T22:00:00Z' },
    { id: 'other-1', restaurantId: 'r2', status: 'PLACED', createdAt: '2026-11-12T23:00:00Z' }
  ]);
  api.menu.mockResolvedValue([
    { id: 'item-1', restaurantId: 'r1', name: 'Midnight Burger', price: 12.99, status: 'AVAILABLE' },
    { id: 'item-2', restaurantId: 'r1', name: 'Loaded Fries', price: 5.49, status: 'SOLD_OUT' }
  ]);
  container = document.createElement('div');
  document.body.append(container);
  root = createRoot(container);
});

afterEach(async () => {
  await act(async () => root.unmount());
  container.remove();
  vi.unstubAllGlobals();
});

describe('restaurant details panel', () => {
  it('shows the quick stats to everyone', async () => {
    await render(users.courier);

    const stats = [...container.querySelectorAll('.details-stats div')].map((element) => element.textContent);
    expect(stats).toEqual(['Pending1', 'Queued2', 'In preparation1', 'Ready1']);
    expect(container.textContent).toContain('Status: OPEN · Band: Short (green)');
  });

  it('shows menu items and marks sold out ones for every role', async () => {
    for (const user of Object.values(users)) {
      await render(user);
      const items = [...container.querySelectorAll('.details-menu li')].map((element) => element.textContent);
      expect(items).toEqual(['Midnight Burger · $12.99', 'Loaded Fries · $5.49Sold out']);
    }
    expect(api.menu).toHaveBeenCalledWith('r1');
  });

  it('shows and logs a failed menu load', async () => {
    const record = vi.fn();
    api.menu.mockRejectedValue(Object.assign(new Error('Menu unavailable'), { status: 500 }));
    await act(async () => root.render(
      <CommandLogContext.Provider value={{ entries: [], record, changeIdentity: () => {} }}>
        <RestaurantDetails restaurant={restaurant} user={users.customer} onClose={() => {}} />
      </CommandLogContext.Provider>
    ));

    expect(container.querySelector('[role="alert"]').textContent).toBe('Menu unavailable');
    expect(record).toHaveBeenCalledWith(expect.objectContaining({
      action: 'Load restaurant menu', outcome: 'error', restaurantId: 'r1', httpStatus: 500
    }));
  });

  it('shows the active queue in stage and FIFO order to the owner', async () => {
    await render(users.owner);

    expect(queueItems()).toEqual([
      'Order #placed-1 · PLACED', 'Order #queued-1 · ACCEPTED', 'Order #queued-2 · ACCEPTED', 'Order #ready-1 · READY'
    ]);
  });

  it('shows the queue to the operator', async () => {
    await render(users.operator);
    expect(queueItems()).toHaveLength(4);
  });

  it('hides the queue from customers, couriers and other staff', async () => {
    for (const user of [users.customer, users.courier, users.otherStaff]) {
      await render(user);
      expect(container.querySelector('.details-queue')).toBeNull();
    }
    expect(api.orders).not.toHaveBeenCalled();
  });

  it('offers only the actions allowed for each role', async () => {
    await render(users.customer);
    expect(buttons()).toEqual(['Close', 'Browse menu']);
    await render(users.owner);
    expect(buttons()).toEqual(['Close', 'Manage orders', 'Edit menu', 'Pause restaurant']);
    await render(users.otherStaff);
    expect(buttons()).toEqual(['Close']);
    await render(users.courier);
    expect(buttons()).toEqual(['Close']);
    await render(users.operator);
    expect(buttons()).toEqual(['Close', 'Pause restaurant']);
  });

  it('offers reopen when the restaurant is paused or closed', async () => {
    await render(users.owner, { status: 'PAUSED', band: 'RED' });
    expect(buttons()).toContain('Reopen restaurant');
    await render(users.operator, { status: 'CLOSED', band: 'RED' });
    expect(buttons()).toEqual(['Close', 'Reopen restaurant']);
  });

  it('tells customers when a restaurant is not accepting orders', async () => {
    await render(users.customer, { status: 'PAUSED', band: 'RED' });
    expect(container.querySelector('.details-note').textContent).toBe('This restaurant is not accepting orders right now.');
    await render(users.customer);
    expect(container.querySelector('.details-note')).toBeNull();
    await render(users.owner, { status: 'PAUSED', band: 'RED' });
    expect(container.querySelector('.details-note')).toBeNull();
  });

  it('pauses and reopens the restaurant and logs the result', async () => {
    const record = vi.fn();
    api.pauseRestaurant.mockResolvedValue({ ...restaurant, status: 'PAUSED' });
    api.reopenRestaurant.mockResolvedValue(restaurant);
    await renderLogged(users.owner, record);
    await act(async () => button('Pause restaurant').click());
    expect(api.pauseRestaurant).toHaveBeenCalledWith('r1');
    expect(record).toHaveBeenCalledWith(expect.objectContaining({
      action: 'Pause restaurant', outcome: 'success', message: 'Bobs Burgers paused.', restaurantId: 'r1'
    }));

    await renderLogged(users.operator, record, { status: 'PAUSED', band: 'RED' });
    await act(async () => button('Reopen restaurant').click());
    expect(api.reopenRestaurant).toHaveBeenCalledWith('r1');
    expect(record).toHaveBeenCalledWith(expect.objectContaining({ action: 'Reopen restaurant', outcome: 'success' }));
  });

  it('logs a failed pause and reports an expired session', async () => {
    const record = vi.fn();
    const onSessionExpired = vi.fn();
    api.pauseRestaurant.mockRejectedValueOnce(Object.assign(new Error('Only an open restaurant can be paused'), { status: 409 }));
    await renderLogged(users.operator, record, {}, { onSessionExpired });
    await act(async () => button('Pause restaurant').click());
    expect(record).toHaveBeenCalledWith(expect.objectContaining({
      action: 'Pause restaurant', outcome: 'error', message: 'Only an open restaurant can be paused', httpStatus: 409
    }));
    expect(button('Pause restaurant').disabled).toBe(false);

    api.pauseRestaurant.mockRejectedValueOnce(Object.assign(new Error('Please log in to continue.'), { status: 401 }));
    await act(async () => button('Pause restaurant').click());
    expect(onSessionExpired).toHaveBeenCalledWith('Please log in to continue.');
  });

  it('opens the restaurant in the chosen view', async () => {
    const onOpenRestaurant = vi.fn();
    await render(users.owner, {}, { onOpenRestaurant });

    const manage = [...container.querySelectorAll('button')].find((element) => element.textContent === 'Manage orders');
    await act(async () => manage.click());
    expect(onOpenRestaurant).toHaveBeenCalledWith(expect.objectContaining({ id: 'r1' }), 'orders');
  });
});
