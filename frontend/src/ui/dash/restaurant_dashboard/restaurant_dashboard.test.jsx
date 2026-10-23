// @vitest-environment jsdom
import React, { act, StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { api } from '../../../api';
import RestaurantDashboard from './restaurant_dashboard';
import RegistrationPage from '../registration_page/registration_page';

vi.mock('../../../api', () => ({ api: {
  currentUser: vi.fn(), restaurants: vi.fn(), orders: vi.fn(), preparationQueue: vi.fn(), menu: vi.fn()
} }));

const restaurant = { id: 'restaurant-1', ownerId: 'staff-1', name: 'Bobs Burgers' };
const otherRestaurant = { id: 'restaurant-2', ownerId: 'staff-2', name: 'Other Diner' };
const staff = { id: 'staff-1', role: 'RESTAURANT_STAFF', username: 'staff@example.com' };
const order = (id, status = 'PLACED', extra = {}) => ({
  id, restaurantId: restaurant.id, status,
  orderLines: [{ menuItemId: 'item-1', menuItemName: 'Burger', quantity: 2, unitPrice: '12.99' }],
  ...extra
});
function deferred() {
  let resolve, reject;
  const promise = new Promise((res, rej) => { resolve = res; reject = rej; });
  return { promise, resolve, reject };
}
let container, root;
const button = (text) => [...container.querySelectorAll('button')].find((element) => element.textContent === text);
const group = (title) => container.querySelector(`section[aria-label="${title}"]`);
const ids = (title) => [...group(title).querySelectorAll('h4')].map((element) => element.textContent);
async function click(element) { await act(async () => element.click()); }
async function renderDashboard(selected = restaurant, user = staff) {
  await act(async () => root.render(<RestaurantDashboard restaurant={selected} user={user} />));
}

beforeEach(() => {
  vi.resetAllMocks();
  vi.stubGlobal('IS_REACT_ACT_ENVIRONMENT', true);
  api.orders.mockResolvedValue([]);
  api.preparationQueue.mockResolvedValue([]);
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

describe('restaurant order viewing', () => {
  it('groups restaurant orders by status and displays snapshots and rejection reasons', async () => {
    api.orders.mockResolvedValue([
      order('incoming'), order('cooking', 'PREPARING'), order('pickup', 'READY'),
      order('rejected', 'REJECTED', { rejectionReason: 'Out of burgers' }),
      order('cancelled', 'CANCELLED'), order('foreign', 'PLACED', { restaurantId: otherRestaurant.id })
    ]);
    await renderDashboard();

    expect(container.querySelector('h2').textContent).toBe('Bobs Burgers orders');
    expect(ids('Incoming orders')).toEqual(['Order #incoming']);
    expect(ids('Preparing')).toEqual(['Order #cooking']);
    expect(ids('Ready')).toEqual(['Order #pickup']);
    expect(ids('Other orders')).toEqual(['Order #rejected', 'Order #cancelled']);
    expect(group('Incoming orders').textContent).toContain('2 × Burger · $12.99 each');
    expect(group('Incoming orders').textContent).toContain('PLACED');
    expect(group('Other orders').textContent).toContain('Rejection reason: Out of burgers');
    expect(container.textContent).not.toContain('foreign');
    expect(api.orders).toHaveBeenCalledTimes(1);
    expect(api.preparationQueue).toHaveBeenCalledWith(restaurant.id);
    expect([...container.querySelectorAll('button')].map((element) => element.textContent)).toEqual(['Refresh orders']);
  });

  it('preserves server FIFO order rather than placement time, IDs, or the orders response order', async () => {
    const first = order('z-first', 'ACCEPTED', { createdAt: '2026-10-23T02:00:00Z' });
    const second = order('a-second', 'ACCEPTED', { createdAt: '2026-10-23T01:00:00Z' });
    api.orders.mockResolvedValue([second, first, order('cooking', 'PREPARING'), order('pickup', 'READY')]);
    api.preparationQueue.mockResolvedValue([first, second]);
    await renderDashboard();

    expect(ids('Preparation queue')).toEqual(['Order #z-first', 'Order #a-second']);
    expect(group('Preparation queue').querySelector('ol')).not.toBeNull();
    expect(group('Preparation queue').textContent).not.toContain('cooking');
    expect(group('Preparation queue').textContent).not.toContain('pickup');
  });

  it('only displays ACCEPTED orders for this restaurant in the waiting queue', async () => {
    api.preparationQueue.mockResolvedValue([
      order('waiting', 'ACCEPTED'), order('cooking', 'PREPARING'), order('pickup', 'READY'),
      order('foreign', 'ACCEPTED', { restaurantId: otherRestaurant.id })
    ]);
    await renderDashboard();
    expect(ids('Preparation queue')).toEqual(['Order #waiting']);
  });

  it('shows loading until both requests finish, without premature empty messages', async () => {
    const queue = deferred();
    api.preparationQueue.mockReturnValue(queue.promise);
    await renderDashboard();
    expect(container.querySelector('[role="status"]').textContent).toContain('Loading');
    expect(container.querySelector('[aria-busy]').getAttribute('aria-busy')).toBe('true');
    expect(button('Refresh orders').disabled).toBe(true);
    expect(group('Incoming orders')).toBeNull();
    await act(async () => queue.resolve([]));
    expect(container.querySelector('[role="status"]')).toBeNull();
    expect(button('Refresh orders').disabled).toBe(false);
    expect(group('Incoming orders').textContent).toContain('No incoming orders.');
    expect(group('Preparation queue').textContent).toContain('No accepted orders waiting for preparation.');
    expect(group('Preparing').textContent).toContain('No orders currently preparing.');
    expect(group('Ready').textContent).toContain('No orders ready yet.');
    expect(group('Other orders').textContent).toContain('No other orders.');
  });

  it.each(['orders', 'preparationQueue'])('displays a failed %s request and can retry both reads', async (method) => {
    api[method].mockRejectedValueOnce(Object.assign(new Error('Access denied by backend'), { status: 403 }));
    await renderDashboard();
    expect(container.querySelector('[role="alert"]').textContent).toBe('Access denied by backend');
    expect(group('Incoming orders')).toBeNull();
    expect(button('Refresh orders').disabled).toBe(false);
    await click(button('Refresh orders'));
    expect(container.querySelector('[role="alert"]')).toBeNull();
    expect(group('Incoming orders')).not.toBeNull();
    expect(api.orders).toHaveBeenCalledTimes(2);
    expect(api.preparationQueue).toHaveBeenCalledTimes(2);
  });

  it('refreshes orders and queue together and replaces the previous statuses', async () => {
    const accepted = order('same-order', 'ACCEPTED');
    api.orders.mockResolvedValueOnce([order('same-order')]).mockResolvedValueOnce([accepted]);
    api.preparationQueue.mockResolvedValueOnce([]).mockResolvedValueOnce([accepted]);
    await renderDashboard();
    expect(ids('Incoming orders')).toEqual(['Order #same-order']);
    await click(button('Refresh orders'));
    expect(group('Incoming orders').querySelector('article')).toBeNull();
    expect(ids('Preparation queue')).toEqual(['Order #same-order']);
    expect(api.orders).toHaveBeenCalledTimes(2);
    expect(api.preparationQueue).toHaveBeenCalledTimes(2);
  });

  it('hides old data while refreshing and if refresh fails', async () => {
    const refresh = deferred();
    api.orders.mockResolvedValueOnce([order('old-order')]).mockReturnValueOnce(refresh.promise);
    await renderDashboard();
    await click(button('Refresh orders'));
    expect(container.textContent).not.toContain('old-order');
    await act(async () => refresh.reject(new Error('Session expired')));
    expect(container.querySelector('[role="alert"]').textContent).toBe('Session expired');
    expect(container.textContent).not.toContain('old-order');
  });

  it.each([null, { id: 'staff-2', role: 'RESTAURANT_STAFF' }, { ...staff, role: 'CUSTOMER' }])(
    'denies a non-owning or unauthenticated viewer without fetching', async (user) => {
      await renderDashboard(restaurant, user);
      expect(container.querySelector('[role="alert"]').textContent).toContain('Only owning restaurant staff');
      expect(api.orders).not.toHaveBeenCalled();
      expect(api.preparationQueue).not.toHaveBeenCalled();
    }
  );

  it('removes data when the viewer loses permission', async () => {
    api.orders.mockResolvedValue([order('private-order')]);
    await renderDashboard();
    await renderDashboard(restaurant, { ...staff, role: 'CUSTOMER' });
    expect(container.textContent).not.toContain('private-order');
    expect(api.orders).toHaveBeenCalledTimes(1);
  });
});

describe('asynchronous safety', () => {
  it('ignores late data from the previous restaurant', async () => {
    const oldOrders = deferred(), oldQueue = deferred();
    const selected = { ...otherRestaurant, ownerId: staff.id };
    api.orders.mockReturnValueOnce(oldOrders.promise).mockResolvedValueOnce([order('new-order', 'PLACED', { restaurantId: selected.id })]);
    api.preparationQueue.mockReturnValueOnce(oldQueue.promise).mockResolvedValueOnce([]);
    await renderDashboard();
    await renderDashboard(selected);
    await act(async () => {
      oldOrders.resolve([order('old-order')]);
      oldQueue.resolve([order('old-waiting', 'ACCEPTED')]);
    });
    expect(ids('Incoming orders')).toEqual(['Order #new-order']);
    expect(container.textContent).not.toContain('old-order');
    expect(container.textContent).not.toContain('old-waiting');
    expect(api.preparationQueue).toHaveBeenLastCalledWith(selected.id);
  });

  it('clears the previous restaurant immediately while the next one loads and ignores late errors', async () => {
    const oldQueue = deferred(), newOrders = deferred();
    const selected = { ...otherRestaurant, ownerId: staff.id };
    api.orders.mockResolvedValueOnce([order('old-order')]).mockReturnValueOnce(newOrders.promise);
    api.preparationQueue.mockReturnValueOnce(oldQueue.promise).mockResolvedValueOnce([]);
    await renderDashboard();
    await renderDashboard(selected);
    await act(async () => oldQueue.reject(new Error('Old restaurant error')));
    expect(container.querySelector('h2').textContent).toBe('Other Diner orders');
    expect(container.textContent).not.toContain('old-order');
    expect(container.querySelector('[role="alert"]')).toBeNull();
    expect(container.querySelector('[role="status"]')).not.toBeNull();
    await act(async () => newOrders.resolve([]));
  });

  it('does not display already loaded data after switching restaurants', async () => {
    api.orders.mockResolvedValueOnce([order('private-old-order')]);
    await renderDashboard();
    const next = deferred();
    api.orders.mockReturnValueOnce(next.promise);
    await renderDashboard({ ...otherRestaurant, ownerId: staff.id });
    expect(container.textContent).not.toContain('private-old-order');
    expect(container.querySelector('[role="status"]')).not.toBeNull();
    await act(async () => next.resolve([]));
  });

  it('ignores an earlier StrictMode request after a newer refresh completes', async () => {
    const oldOrders = deferred(), oldQueue = deferred();
    api.orders.mockReturnValueOnce(oldOrders.promise).mockResolvedValueOnce([]).mockResolvedValueOnce([order('latest')]);
    api.preparationQueue.mockReturnValueOnce(oldQueue.promise).mockResolvedValue([]);
    await act(async () => root.render(<StrictMode><RestaurantDashboard restaurant={restaurant} user={staff} /></StrictMode>));
    await click(button('Refresh orders'));
    await act(async () => { oldOrders.resolve([order('stale')]); oldQueue.resolve([]); });
    expect(ids('Incoming orders')).toEqual(['Order #latest']);
    expect(container.textContent).not.toContain('stale');
  });
});

describe('application navigation', () => {
  beforeEach(() => api.restaurants.mockResolvedValue([restaurant, otherRestaurant, { id: 'unowned', name: 'Unowned Diner' }]));

  it.each(['CUSTOMER', 'COURIER', 'PLATFORM_OPERATOR'])('hides Manage orders from %s', async (role) => {
    api.currentUser.mockResolvedValue({ ...staff, role });
    await act(async () => root.render(<RegistrationPage />));
    expect(button('Manage orders')).toBeUndefined();
    expect(api.preparationQueue).not.toHaveBeenCalled();
  });

  it('only shows management navigation for owned restaurants and opens the selected dashboard', async () => {
    api.currentUser.mockResolvedValue(staff);
    await act(async () => root.render(<RegistrationPage />));
    const cards = [...container.querySelectorAll('.restaurant-card')];
    expect(cards.filter((card) => card.textContent.includes('Manage orders'))).toHaveLength(1);
    expect(cards.find((card) => card.textContent.includes('Bobs Burgers')).textContent).toContain('Manage orders');
    await click(button('Manage orders'));
    expect(container.querySelector('.restaurant-dashboard')).not.toBeNull();
    expect(container.querySelector('.order-history')).toBeNull();
    expect(api.preparationQueue).toHaveBeenCalledWith(restaurant.id);
    await click(button('Back to restaurants'));
    expect(container.querySelectorAll('.restaurant-card')).toHaveLength(3);
    expect(container.querySelector('.order-history')).not.toBeNull();
    await click(button('Edit menu'));
    expect(container.querySelector('.restaurant-menu')).not.toBeNull();
    expect(container.querySelector('.restaurant-dashboard')).toBeNull();
  });

  it('keeps unauthenticated users on the login screen', async () => {
    api.currentUser.mockRejectedValue(Object.assign(new Error('Authentication required'), { status: 401 }));
    await act(async () => root.render(<RegistrationPage />));
    expect(container.querySelector('form')).not.toBeNull();
    expect(button('Manage orders')).toBeUndefined();
    expect(api.orders).not.toHaveBeenCalled();
    expect(api.preparationQueue).not.toHaveBeenCalled();
  });
});
