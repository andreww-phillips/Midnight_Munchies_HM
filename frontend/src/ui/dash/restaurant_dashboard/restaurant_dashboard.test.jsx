// @vitest-environment jsdom
import React, { act, StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { api } from '../../../api';
import RestaurantDashboard from './restaurant_dashboard';
import RegistrationPage from '../registration_page/registration_page';

vi.mock('../../../api', () => ({ api: {
  currentUser: vi.fn(), restaurants: vi.fn(), orders: vi.fn(), preparationQueue: vi.fn(), menu: vi.fn(),
  acceptOrder: vi.fn(), rejectOrder: vi.fn(), startNextPreparation: vi.fn(), markOrderReady: vi.fn()
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
    expect(button('Accept')).toBeDefined();
    expect(button('Reject')).toBeDefined();
    expect(button('Mark Ready')).toBeDefined();
    expect(button('Start next preparation').disabled).toBe(true);
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
      expect(button('Accept')).toBeUndefined();
      expect(button('Reject')).toBeUndefined();
      expect(button('Start next preparation')).toBeUndefined();
      expect(button('Mark Ready')).toBeUndefined();
      for (const method of ['acceptOrder', 'rejectOrder', 'startNextPreparation', 'markOrderReady']) {
        expect(api[method]).not.toHaveBeenCalled();
      }
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

async function enterReason(value) {
  await act(async () => {
    const input = group('Incoming orders').querySelector('input');
    Object.getOwnPropertyDescriptor(HTMLInputElement.prototype, 'value').set.call(input, value);
    input.dispatchEvent(new Event('input', { bubbles: true }));
  });
}

async function submitRejection() {
  await act(async () => group('Incoming orders').querySelector('form')
    .dispatchEvent(new Event('submit', { bubbles: true, cancelable: true })));
}

describe('restaurant lifecycle commands', () => {
  it('accepts an order, immediately displays its returned state, and refreshes both lists', async () => {
    const accepted = order('incoming', 'ACCEPTED');
    const refresh = deferred();
    api.orders.mockResolvedValueOnce([order('incoming')]).mockReturnValueOnce(refresh.promise);
    api.preparationQueue.mockResolvedValueOnce([]).mockResolvedValueOnce([accepted]);
    api.acceptOrder.mockResolvedValue(accepted);
    await renderDashboard();
    await click(button('Accept'));

    expect(api.acceptOrder).toHaveBeenCalledExactlyOnceWith(restaurant.id, 'incoming');
    expect(group('Last updated order').textContent).toContain('ACCEPTED');
    expect(container.querySelector('.dashboard-success').textContent).toBe('Order #incoming is now ACCEPTED.');
    expect(button('Refresh orders').disabled).toBe(true);
    expect(api.orders).toHaveBeenCalledTimes(2);
    expect(api.preparationQueue).toHaveBeenCalledTimes(2);

    await act(async () => refresh.resolve([accepted]));
    expect(ids('Preparation queue')).toEqual(['Order #incoming']);
    expect(group('Incoming orders').querySelector('article')).toBeNull();
    expect(container.querySelector('h2').textContent).toBe('Bobs Burgers orders');
    expect(button('Refresh orders').disabled).toBe(false);
  });

  it('rejects with a trimmed reason and displays the backend rejection details', async () => {
    const rejected = order('incoming', 'REJECTED', { rejectionReason: 'Out of burgers' });
    api.orders.mockResolvedValueOnce([order('incoming')]).mockResolvedValueOnce([rejected]);
    api.rejectOrder.mockResolvedValue(rejected);
    await renderDashboard();
    await enterReason('  Out of burgers  ');
    await submitRejection();

    expect(api.rejectOrder).toHaveBeenCalledExactlyOnceWith(restaurant.id, 'incoming', 'Out of burgers');
    expect(group('Other orders').textContent).toContain('REJECTED');
    expect(group('Other orders').textContent).toContain('Rejection reason: Out of burgers');
    expect(button('Reject')).toBeUndefined();
    expect(api.orders).toHaveBeenCalledTimes(2);
    expect(api.preparationQueue).toHaveBeenCalledTimes(2);
  });

  it.each(['', '   \t  '])('rejects blank reasons locally (%j)', async (reason) => {
    api.orders.mockResolvedValue([order('incoming')]);
    await renderDashboard();
    await enterReason(reason);
    await submitRejection();
    expect(container.querySelector('[role="alert"]').textContent).toBe('A rejection reason is required.');
    expect(api.rejectOrder).not.toHaveBeenCalled();
    expect(container.querySelector('.dashboard-success')).toBeNull();
    expect(button('Accept').disabled).toBe(false);
  });

  it('starts the backend-selected next order even when another order is preparing', async () => {
    const first = order('first', 'ACCEPTED'), second = order('second', 'ACCEPTED');
    const preparing = order('first', 'PREPARING');
    api.orders.mockResolvedValueOnce([first, second, order('already-cooking', 'PREPARING')])
      .mockResolvedValueOnce([preparing, second, order('already-cooking', 'PREPARING')]);
    api.preparationQueue.mockResolvedValueOnce([first, second]).mockResolvedValueOnce([second]);
    api.startNextPreparation.mockResolvedValue(preparing);
    await renderDashboard();
    expect(button('Start next preparation').disabled).toBe(false);
    expect(group('Preparation queue').querySelectorAll(':scope > button')).toHaveLength(1);
    await click(button('Start next preparation'));

    expect(api.startNextPreparation).toHaveBeenCalledExactlyOnceWith(restaurant.id);
    expect(ids('Preparation queue')).toEqual(['Order #second']);
    expect(ids('Preparing')).toEqual(['Order #first', 'Order #already-cooking']);
    expect(button('Start next preparation').disabled).toBe(false);
    expect(api.orders).toHaveBeenCalledTimes(2);
    expect(api.preparationQueue).toHaveBeenCalledTimes(2);
  });

  it('marks a preparing order ready and removes its ready control', async () => {
    const ready = order('cooking', 'READY');
    api.orders.mockResolvedValueOnce([order('cooking', 'PREPARING')]).mockResolvedValueOnce([ready]);
    api.markOrderReady.mockResolvedValue(ready);
    await renderDashboard();
    await click(button('Mark Ready'));

    expect(api.markOrderReady).toHaveBeenCalledExactlyOnceWith(restaurant.id, 'cooking');
    expect(ids('Ready')).toEqual(['Order #cooking']);
    expect(button('Mark Ready')).toBeUndefined();
    expect(container.querySelector('.dashboard-success').textContent).toContain('READY');
    expect(api.orders).toHaveBeenCalledTimes(2);
    expect(api.preparationQueue).toHaveBeenCalledTimes(2);
  });

  it.each([
    ['PLACED', ['Accept', 'Reject']], ['ACCEPTED', []], ['PREPARING', ['Mark Ready']],
    ['READY', []], ['REJECTED', []], ['CANCELLED', []]
  ])('only offers the relevant per-order controls for %s', async (status, controls) => {
    const current = order('current', status);
    api.orders.mockResolvedValue([current]);
    api.preparationQueue.mockResolvedValue(status === 'ACCEPTED' ? [current] : []);
    await renderDashboard();
    expect([...container.querySelector('article').querySelectorAll('button')].map((element) => element.textContent)).toEqual([...controls, 'View event history']);
  });

  it('preserves the rejection draft when the backend refuses the reason', async () => {
    api.orders.mockResolvedValue([order('incoming')]);
    api.rejectOrder.mockRejectedValue(Object.assign(new Error('Invalid rejection reason'), { status: 400 }));
    await renderDashboard();
    await enterReason('Kitchen closed');
    await submitRejection();
    expect(group('Incoming orders').querySelector('input').value).toBe('Kitchen closed');
    expect(container.querySelector('[role="alert"]').textContent).toBe('Invalid rejection reason');
    expect(container.querySelector('.dashboard-success')).toBeNull();
  });

  it('resynchronizes after another session changes an incoming order', async () => {
    api.orders.mockResolvedValueOnce([order('incoming')]).mockResolvedValueOnce([
      order('incoming', 'REJECTED', { rejectionReason: 'Rejected by another session' })
    ]);
    api.acceptOrder.mockRejectedValue(Object.assign(new Error('Cannot accept an order that is REJECTED'), { status: 409 }));
    await renderDashboard();
    await click(button('Accept'));
    expect(container.querySelector('[role="alert"]').textContent).toContain('Cannot accept');
    expect(container.querySelector('.dashboard-success')).toBeNull();
    expect(button('Accept')).toBeUndefined();
    expect(group('Other orders').textContent).toContain('Rejected by another session');
    expect(api.acceptOrder).toHaveBeenCalledTimes(1);
    expect(api.orders).toHaveBeenCalledTimes(2);
  });

  it('handles an emptied queue conflict without claiming success or retrying the command', async () => {
    const waiting = order('waiting', 'ACCEPTED');
    api.orders.mockResolvedValueOnce([waiting]).mockResolvedValueOnce([order('waiting', 'PREPARING')]);
    api.preparationQueue.mockResolvedValueOnce([waiting]).mockResolvedValueOnce([]);
    api.startNextPreparation.mockRejectedValue(Object.assign(new Error('No accepted orders are waiting to be prepared'), { status: 409 }));
    await renderDashboard();
    await click(button('Start next preparation'));
    expect(container.querySelector('[role="alert"]').textContent).toContain('No accepted orders');
    expect(container.querySelector('.dashboard-success')).toBeNull();
    expect(button('Start next preparation').disabled).toBe(true);
    expect(ids('Preparing')).toEqual(['Order #waiting']);
    expect(api.startNextPreparation).toHaveBeenCalledTimes(1);
  });

  it.each(['orders', 'preparationQueue'])('keeps the successful result when the %s refresh fails and recovers through read-only refresh', async (method) => {
    const accepted = order('incoming', 'ACCEPTED');
    api.orders.mockResolvedValue([order('incoming')]);
    api.acceptOrder.mockResolvedValue(accepted);
    await renderDashboard();
    api[method].mockRejectedValueOnce(new Error('Refresh unavailable'));
    await click(button('Accept'));
    expect(container.querySelector('.dashboard-success').textContent).toContain('ACCEPTED');
    expect(container.querySelector('[role="alert"]').textContent).toContain('operation succeeded, but the dashboard could not refresh');
    expect(group('Last updated order').textContent).toContain('ACCEPTED');
    expect(group('Incoming orders')).toBeNull();
    expect(button('Accept')).toBeUndefined();
    expect(api.acceptOrder).toHaveBeenCalledTimes(1);
    api.orders.mockResolvedValue([accepted]);
    api.preparationQueue.mockResolvedValue([accepted]);
    await click(button('Refresh orders'));
    expect(container.querySelector('[role="alert"]')).toBeNull();
    expect(ids('Preparation queue')).toEqual(['Order #incoming']);
    expect(button('Start next preparation').disabled).toBe(false);
    expect(api.acceptOrder).toHaveBeenCalledTimes(1);
  });

  it('shows the returned rejection reason even if the follow-up refresh fails', async () => {
    api.orders.mockResolvedValue([order('incoming')]);
    api.rejectOrder.mockResolvedValue(order('incoming', 'REJECTED', { rejectionReason: 'Kitchen closed' }));
    await renderDashboard();
    api.orders.mockRejectedValueOnce(new Error('Network unavailable'));
    await enterReason('Kitchen closed');
    await submitRejection();
    expect(group('Last updated order').textContent).toContain('Rejection reason: Kitchen closed');
    expect(container.querySelector('.dashboard-success').textContent).toContain('REJECTED');
  });
});

describe('asynchronous safety', () => {
  it.each(['success', 'expired session'])('ignores a late command result (%s) after changing restaurants', async (outcome) => {
    const command = deferred();
    const onSessionExpired = vi.fn();
    api.orders.mockResolvedValueOnce([order('old-order')]).mockResolvedValueOnce([]);
    api.acceptOrder.mockReturnValue(command.promise);
    await act(async () => root.render(<RestaurantDashboard restaurant={restaurant} user={staff} onSessionExpired={onSessionExpired} />));
    await click(button('Accept'));
    await renderDashboard({ ...otherRestaurant, ownerId: staff.id });
    await act(async () => {
      if (outcome === 'success') command.resolve(order('old-order', 'ACCEPTED'));
      else command.reject(Object.assign(new Error('Old session expired'), { status: 401 }));
    });
    expect(container.querySelector('h2').textContent).toBe('Other Diner orders');
    expect(container.textContent).not.toContain('old-order');
    expect(container.querySelector('.dashboard-success')).toBeNull();
    expect(container.querySelector('[role="alert"]')).toBeNull();
    expect(onSessionExpired).not.toHaveBeenCalled();
    expect(api.orders).toHaveBeenCalledTimes(2);
  });

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

  it.each(['initial load', 'manual refresh', 'command', 'refresh after success'])(
    'returns to login for an expired session during %s', async (phase) => {
      api.currentUser.mockResolvedValue(staff);
      api.orders.mockResolvedValue([order('incoming')]);
      await act(async () => root.render(<RegistrationPage />));
      const expired = Object.assign(new Error('Authentication is required'), { status: 401 });
      if (phase === 'initial load') api.preparationQueue.mockRejectedValueOnce(expired);
      await click(button('Manage orders'));
      if (phase === 'manual refresh') {
        api.preparationQueue.mockRejectedValueOnce(expired);
        await click(button('Refresh orders'));
      } else if (phase === 'command') {
        api.acceptOrder.mockRejectedValueOnce(expired);
        await click(button('Accept'));
      } else if (phase === 'refresh after success') {
        api.acceptOrder.mockResolvedValueOnce(order('incoming', 'ACCEPTED'));
        api.preparationQueue.mockRejectedValueOnce(expired);
        await click(button('Accept'));
      }
      expect(container.querySelector('.auth-card form')).not.toBeNull();
      expect(container.querySelector('h1').textContent).toBe('Welcome back');
      expect(container.querySelector('[role="alert"]').textContent).toContain('Your session expired. Please log in again.');
      expect(container.querySelector('input[type="password"]').value).toBe('');
      expect(container.querySelector('.restaurant-dashboard')).toBeNull();
      expect(container.querySelector('.dashboard-success')).toBeNull();
      if (phase === 'refresh after success') {
        expect(container.querySelector('[role="alert"]').textContent).toContain('Order #incoming is now ACCEPTED.');
        expect(container.querySelector('[role="alert"]').textContent).toContain('operation succeeded, but the dashboard could not refresh');
      } else expect(container.querySelector('[role="alert"]').textContent).not.toContain('is now');
      expect(api.acceptOrder).toHaveBeenCalledTimes(phase === 'command' || phase === 'refresh after success' ? 1 : 0);
    }
  );

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
