// @vitest-environment jsdom
import React, { act } from 'react';
import { createRoot } from 'react-dom/client';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { api } from '../../../api';
import { CommandLogContext } from '../output_console/command_log';
import RestaurantDetails from './restaurant_details';

vi.mock('../../../api', () => ({ api: { orders: vi.fn(), menu: vi.fn() } }));

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
    await render(users.owner);
    expect(buttons()).toEqual(['Close', 'Manage orders']);
    await render(users.otherStaff);
    expect(buttons()).toEqual(['Close']);
    await render(users.courier);
    expect(buttons()).toEqual(['Close']);
    await render(users.operator);
    expect(buttons()).toEqual(['Close']);
    await render(users.customer);
    expect(buttons()).toEqual(['Close', 'Order from here']);
    await render(users.customer, { status: 'PAUSED' });
    expect(buttons()).toEqual(['Close']);
  });

  it('opens the restaurant in the chosen view', async () => {
    const onOpenRestaurant = vi.fn();
    await render(users.owner, {}, { onOpenRestaurant });

    const manage = [...container.querySelectorAll('button')].find((element) => element.textContent === 'Manage orders');
    await act(async () => manage.click());
    expect(onOpenRestaurant).toHaveBeenCalledWith(expect.objectContaining({ id: 'r1' }), 'orders');
  });
});
