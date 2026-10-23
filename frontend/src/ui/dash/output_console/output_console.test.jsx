// @vitest-environment jsdom
import React, { act, StrictMode, useEffect } from 'react';
import { createRoot } from 'react-dom/client';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { api } from '../../../api';
import { CommandLogProvider } from './command_log_provider';
import { useCommandLog } from './command_log';
import OutputConsole from './output_console';
import RegistrationPage from '../registration_page/registration_page';
import RestaurantDashboard from '../restaurant_dashboard/restaurant_dashboard';
import RestaurantMenu from '../menu_page/menu_page';
import OrderEventHistory from '../order_event_history';

vi.mock('../../../api', () => ({ api: {
  currentUser: vi.fn(), login: vi.fn(), register: vi.fn(), logout: vi.fn(),
  restaurants: vi.fn(), orders: vi.fn(), preparationQueue: vi.fn(), menu: vi.fn(),
  acceptOrder: vi.fn(), rejectOrder: vi.fn(), startNextPreparation: vi.fn(), markOrderReady: vi.fn(),
  placeOrder: vi.fn(), addMenuItem: vi.fn(), editMenuItem: vi.fn(), removeMenuItem: vi.fn(),
  changeMenuItemAvailability: vi.fn(), orderEvents: vi.fn()
} }));

const staff = { id: 'staff-1', role: 'RESTAURANT_STAFF', username: 'staff@example.com' };
const restaurant = { id: 'restaurant-1', ownerId: staff.id, name: 'Diner' };
const item = { id: 'item-1', name: 'Fries', price: '5.49', status: 'AVAILABLE' };
const order = (status = 'PLACED') => ({ id: 'order-1', restaurantId: restaurant.id, status,
  orderLines: [{ menuItemId: item.id, menuItemName: item.name, quantity: 1, unitPrice: item.price }] });
const event = { eventId: 'backend-event-1', orderId: 'order-1', restaurantId: restaurant.id,
  fromStatus: 'PLACED', toStatus: 'ACCEPTED', occurredAt: '2026-10-23T22:00:00Z', actorRole: 'RESTAURANT_STAFF', actorId: 'private-actor-id' };
let container, root, log;
function Probe() {
  const current = useCommandLog();
  useEffect(() => { log = current; }, [current]);
  return null;
}
async function renderLogged(child = null) {
  await act(async () => root.render(<StrictMode><CommandLogProvider><Probe />{child}<OutputConsole /></CommandLogProvider></StrictMode>));
}
const entries = () => [...container.querySelectorAll('.console-entry')];
const output = () => container.querySelector('[role="log"]');
function button(label) {
  return [...container.querySelectorAll('button')].find((element) => (element.getAttribute('aria-label') || element.textContent.trim()) === label);
}
async function click(element) { expect(element).toBeDefined(); await act(async () => element.click()); }
async function change(input, value) {
  await act(async () => {
    Object.getOwnPropertyDescriptor(HTMLInputElement.prototype, 'value').set.call(input, value);
    input.dispatchEvent(new Event('input', { bubbles: true }));
  });
}
async function submit(form = container.querySelector('form')) {
  await act(async () => form.dispatchEvent(new Event('submit', { bubbles: true, cancelable: true })));
}
function deferred() {
  let resolve;
  const promise = new Promise((res) => { resolve = res; });
  return { promise, resolve };
}

beforeEach(() => {
  vi.resetAllMocks();
  vi.stubGlobal('IS_REACT_ACT_ENVIRONMENT', true);
  api.currentUser.mockRejectedValue(Object.assign(new Error('Authentication required'), { status: 401 }));
  api.restaurants.mockResolvedValue([restaurant]);
  api.orders.mockResolvedValue([]);
  api.preparationQueue.mockResolvedValue([]);
  api.menu.mockResolvedValue([item]);
  api.orderEvents.mockResolvedValue([]);
  container = document.createElement('div');
  document.body.append(container);
  root = createRoot(container);
});
afterEach(async () => {
  await act(async () => root.unmount());
  container.remove();
  vi.unstubAllGlobals();
});

describe('shared command console', () => {
  it('displays timestamps, outcomes, context, and only explicitly allowed fields without persistence', async () => {
    const persist = vi.spyOn(Storage.prototype, 'setItem');
    await renderLogged();
    expect(output().textContent).toContain('No commands yet.');
    await act(async () => log.record({ action: 'Accept order', outcome: 'success', message: 'Accepted.', orderId: 'order-1',
      restaurantId: restaurant.id, status: 'ACCEPTED', password: 'secret-password', cookies: 'session-cookie',
      token: 'session-token', headers: { Authorization: 'Bearer secret' }, body: { password: 'body-secret' } }));
    await act(async () => log.record({ action: 'Reject order', outcome: 'error', message: 'Backend says no.', httpStatus: 409 }));
    expect(entries()).toHaveLength(2);
    expect(entries()[0].className).toContain('--success');
    expect(entries()[0].textContent).toContain('Status: ACCEPTED');
    expect(entries()[0].textContent).toContain('Restaurant #restaurant-1');
    expect(entries()[0].textContent).toContain('Order #order-1');
    expect(Number.isNaN(Date.parse(entries()[0].querySelector('time').dateTime))).toBe(false);
    expect(entries()[1].className).toContain('--error');
    expect(entries()[1].textContent).toContain('Backend says no.');
    expect(entries()[1].textContent).toContain('HTTP 409');
    expect(JSON.stringify(log.entries)).not.toMatch(/secret|session-cookie|session-token|Authorization|password|headers|body/);
    expect(persist).not.toHaveBeenCalled();
    persist.mockRestore();
  });

  it('clears on identity changes and drops callbacks captured by previous accounts', async () => {
    await renderLogged();
    await act(async () => log.changeIdentity('account-a'));
    const oldRecord = log.record;
    await act(async () => oldRecord({ action: 'Place order', outcome: 'success', message: 'Private account A order.' }));
    expect(entries()).toHaveLength(1);
    await act(async () => log.changeIdentity('account-b'));
    await act(async () => oldRecord({ action: 'Place order', outcome: 'success', message: 'Late private account A result.' }));
    expect(entries()).toHaveLength(0);
    await act(async () => log.record({ action: 'Place order', outcome: 'success', message: 'Account B order.' }));
    expect(entries()).toHaveLength(1);
    expect(output().textContent).not.toContain('account A');
    await act(async () => log.changeIdentity(null));
    expect(entries()).toHaveLength(0);
  });

  it('logs login once under StrictMode, clears private history at logout, and starts a fresh account history', async () => {
    api.login.mockResolvedValueOnce(staff).mockResolvedValueOnce({ id: 'customer-2', role: 'CUSTOMER', username: 'other@example.com' });
    api.logout.mockResolvedValue(null);
    await act(async () => root.render(<StrictMode><RegistrationPage /></StrictMode>));
    await change(container.querySelector('[name="username"]'), staff.username);
    await change(container.querySelector('[name="password"]'), 'never-log-this-password');
    const form = container.querySelector('form');
    await act(async () => {
      form.dispatchEvent(new Event('submit', { bubbles: true, cancelable: true }));
      form.dispatchEvent(new Event('submit', { bubbles: true, cancelable: true }));
    });
    expect(api.login).toHaveBeenCalledTimes(1);
    expect(entries()).toHaveLength(1);
    expect(entries()[0].textContent).toContain('Success · Log in');
    expect(output().textContent).not.toContain('never-log-this-password');
    await click(button('Manage orders'));
    await click(button('Refresh orders'));
    expect(entries()).toHaveLength(2);
    expect(output().textContent).toContain('restaurant-1');
    await click(button('Log out'));
    expect(entries()).toHaveLength(1);
    expect(output().textContent).toContain('Success · Log out');
    expect(output().textContent).not.toContain('restaurant-1');
    await change(container.querySelector('[name="username"]'), 'other@example.com');
    await change(container.querySelector('[name="password"]'), 'another-private-password');
    await submit();
    expect(entries()).toHaveLength(1);
    expect(output().textContent).toContain('Success · Log in');
    expect(output().textContent).not.toContain('Log out');
    expect(output().textContent).not.toMatch(/private-password|restaurant-1/);
  });

  it('logs failed login without false success or credentials', async () => {
    api.login.mockRejectedValue(Object.assign(new Error('Username or password is incorrect'), { status: 401 }));
    await act(async () => root.render(<RegistrationPage />));
    await change(container.querySelector('[name="username"]'), 'private-username');
    await change(container.querySelector('[name="password"]'), 'private-password');
    await submit();
    expect(entries()).toHaveLength(1);
    expect(entries()[0].className).toContain('--error');
    expect(output().textContent).toContain('Invalid username or password.');
    expect(output().textContent).not.toMatch(/private-username|private-password|Success/);
  });

  it('does not log restored sessions twice', async () => {
    api.currentUser.mockResolvedValue(staff);
    await act(async () => root.render(<StrictMode><RegistrationPage /></StrictMode>));
    expect(entries()).toHaveLength(0);
    expect(container.querySelector('.app-shell')).not.toBeNull();
  });

  it('ignores a late session restoration after a different account logs in', async () => {
    const restore = deferred();
    api.currentUser.mockReturnValue(restore.promise);
    api.login.mockResolvedValue({ id: 'customer-2', role: 'CUSTOMER', username: 'new-account' });
    await act(async () => root.render(<RegistrationPage />));
    await change(container.querySelector('[name="username"]'), 'new-account');
    await change(container.querySelector('[name="password"]'), 'private-password');
    await submit();
    await act(async () => restore.resolve(staff));
    expect(container.querySelector('h1').textContent).toContain('new-account');
    expect(button('Manage orders')).toBeUndefined();
    expect(entries()).toHaveLength(1);
    expect(output().textContent).toContain('Success · Log in');
  });

  it('drops a pending restaurant result after logout and login to a different account', async () => {
    const command = deferred();
    api.currentUser.mockResolvedValue(staff);
    api.orders.mockResolvedValue([order()]);
    api.acceptOrder.mockReturnValue(command.promise);
    api.logout.mockResolvedValue(null);
    api.login.mockResolvedValue({ id: 'customer-2', role: 'CUSTOMER', username: 'new-account' });
    await act(async () => root.render(<RegistrationPage />));
    await click(button('Manage orders'));
    await click(button('Accept'));
    await click(button('Log out'));
    await change(container.querySelector('[name="username"]'), 'new-account');
    await change(container.querySelector('[name="password"]'), 'private-password');
    api.orders.mockResolvedValue([]);
    await submit();
    await act(async () => command.resolve(order('ACCEPTED')));
    expect(entries()).toHaveLength(1);
    expect(output().textContent).toContain('Success · Log in');
    expect(output().textContent).not.toMatch(/order-1|restaurant-1|ACCEPTED/);
  });

  it('clears private command history when a dashboard request expires the session', async () => {
    api.currentUser.mockResolvedValue(staff);
    api.orders.mockResolvedValue([order()]);
    api.acceptOrder.mockResolvedValue(order('ACCEPTED'));
    await act(async () => root.render(<RegistrationPage />));
    await click(button('Manage orders'));
    api.orders.mockResolvedValue([order('ACCEPTED')]);
    api.preparationQueue.mockResolvedValue([order('ACCEPTED')]);
    await click(button('Accept'));
    expect(output().textContent).toContain('order-1');
    api.orders.mockRejectedValueOnce(Object.assign(new Error('Authentication required'), { status: 401 }));
    await click(button('Refresh orders'));
    expect(container.querySelector('.auth-card')).not.toBeNull();
    expect(entries()).toHaveLength(1);
    expect(output().textContent).toContain('Session expired');
    expect(output().textContent).not.toMatch(/order-1|restaurant-1|ACCEPTED/);
  });

  it('logs failed logout without clearing the still-authenticated account history', async () => {
    api.login.mockResolvedValue(staff);
    api.logout.mockRejectedValue(new Error('Logout unavailable'));
    await act(async () => root.render(<RegistrationPage />));
    await change(container.querySelector('[name="username"]'), staff.username);
    await change(container.querySelector('[name="password"]'), 'private-password');
    await submit();
    await click(button('Log out'));
    expect(container.querySelector('.app-shell')).not.toBeNull();
    expect(entries()).toHaveLength(2);
    expect(entries()[1].textContent).toContain('Error · Log out');
    expect(output().textContent).not.toContain('Logged out successfully');
  });
});

describe('command logging integration', () => {
  it.each([
    ['Accept', 'acceptOrder', 'ACCEPTED', 'Accept order'],
    ['Reject', 'rejectOrder', 'REJECTED', 'Reject order'],
    ['Start next preparation', 'startNextPreparation', 'PREPARING', 'Start preparation'],
    ['Mark Ready', 'markOrderReady', 'READY', 'Mark order ready']
  ])('logs %s once and blocks duplicates through the command and refresh', async (label, method, status, action) => {
    const request = deferred(), refresh = deferred();
    const incoming = order(), cooking = { ...order('PREPARING'), id: 'cooking' };
    const waiting = { ...order('ACCEPTED'), id: 'waiting' };
    api.orders.mockResolvedValue([incoming, cooking, waiting]);
    api.preparationQueue.mockResolvedValue([waiting]);
    api[method].mockReturnValue(request.promise);
    await renderLogged(<RestaurantDashboard restaurant={restaurant} user={staff} />);
    api.orders.mockReturnValueOnce(refresh.promise);
    if (label === 'Reject') await change(container.querySelector('input'), 'Kitchen closed');
    const control = button(label);
    const rejectionForm = container.querySelector('form');
    await act(async () => {
      if (label === 'Reject') {
        rejectionForm.dispatchEvent(new Event('submit', { bubbles: true, cancelable: true }));
        rejectionForm.dispatchEvent(new Event('submit', { bubbles: true, cancelable: true }));
      } else { control.click(); control.click(); }
    });
    expect(entries()).toHaveLength(0);
    expect(api[method]).toHaveBeenCalledTimes(1);
    for (const element of container.querySelectorAll('button, input')) expect(element.disabled).toBe(true);
    expect(container.querySelector('[aria-busy]').getAttribute('aria-busy')).toBe('true');
    expect([...container.querySelectorAll('[role="status"]')].some((element) => element.textContent.endsWith('...'))).toBe(true);
    const updated = { ...order(status), id: method === 'startNextPreparation' ? waiting.id : method === 'markOrderReady' ? cooking.id : incoming.id };
    api.preparationQueue.mockResolvedValue(status === 'ACCEPTED' ? [updated] : []);
    await act(async () => request.resolve(updated));
    expect(button('Refresh orders').disabled).toBe(true);
    expect(entries()).toHaveLength(1);
    expect(output().textContent).toContain(`Success · ${action}`);
    expect(output().textContent).toContain(`Status: ${status}`);
    await act(async () => refresh.resolve([updated]));
    expect(button('Refresh orders').disabled).toBe(false);
    expect(container.querySelector('[aria-busy]').getAttribute('aria-busy')).toBe('false');
    expect(api[method]).toHaveBeenCalledTimes(1);
    expect(entries()).toHaveLength(1);
    expect(api.orderEvents).not.toHaveBeenCalled();
  });

  it.each([400, 403, 404, 409])('logs the actual HTTP %s error once without success', async (status) => {
    api.orders.mockResolvedValue([order()]);
    api.acceptOrder.mockRejectedValue(Object.assign(new Error('Backend order error'), { status }));
    await renderLogged(<RestaurantDashboard restaurant={restaurant} user={staff} />);
    await click(button('Accept'));
    expect(entries()).toHaveLength(1);
    expect(output().textContent).toContain('Error · Accept order');
    expect(output().textContent).toContain('Backend order error');
    expect(output().textContent).toContain(`HTTP ${status}`);
    expect(output().textContent).not.toContain('Success');
    expect(container.querySelector('[role="alert"]').textContent).toBe('Backend order error');
    expect(container.querySelector('.dashboard-success')).toBeNull();
    expect(container.querySelector('[aria-label="Last updated order"]')).toBeNull();
    expect(button('Accept').disabled).toBe(false);
    expect(api.acceptOrder).toHaveBeenCalledTimes(1);
  });

  it('records mutation success and follow-up refresh failure separately', async () => {
    api.orders.mockResolvedValue([order()]);
    api.acceptOrder.mockResolvedValue(order('ACCEPTED'));
    await renderLogged(<RestaurantDashboard restaurant={restaurant} user={staff} />);
    api.orders.mockRejectedValueOnce(new Error('Refresh failed'));
    await click(button('Accept'));
    expect(entries()).toHaveLength(2);
    expect(entries()[0].textContent).toContain('Success · Accept order');
    expect(entries()[1].textContent).toContain('Error · Refresh restaurant orders');
    expect(entries()[1].textContent).toContain('Refresh failed');
    expect(api.acceptOrder).toHaveBeenCalledTimes(1);
  });

  it('logs a local rejection validation error once without sending a command', async () => {
    api.orders.mockResolvedValue([order()]);
    await renderLogged(<RestaurantDashboard restaurant={restaurant} user={staff} />);
    await click(button('Reject'));
    expect(entries()).toHaveLength(1);
    expect(output().textContent).toContain('Error · Reject order');
    expect(output().textContent).toContain('A rejection reason is required.');
    expect(api.rejectOrder).not.toHaveBeenCalled();
  });

  it.each(['Add menu item', 'Edit menu item', 'Remove menu item', 'Change menu availability'])('logs %s once', async (action) => {
    api.addMenuItem.mockResolvedValue(item);
    api.editMenuItem.mockResolvedValue(item);
    api.removeMenuItem.mockResolvedValue(null);
    api.changeMenuItemAvailability.mockResolvedValue({ ...item, status: 'SOLD_OUT' });
    await renderLogged(<RestaurantMenu restaurant={restaurant} user={staff} />);
    if (action === 'Add menu item' || action === 'Edit menu item') {
      if (action === 'Edit menu item') await click(button('Edit Fries'));
      await change(container.querySelector('.menu-fields input'), 'Fries');
      await change(container.querySelector('.menu-fields input[type="number"]'), '5.49');
      await submit();
    } else await click(button(action === 'Remove menu item' ? 'Remove Fries' : 'Mark sold out: Fries'));
    expect(entries()).toHaveLength(1);
    expect(output().textContent).toContain(`Success · ${action}`);
    expect(output().textContent).toContain('Restaurant #restaurant-1');
  });

  it('separates menu mutation success from menu refresh failure', async () => {
    api.removeMenuItem.mockResolvedValue(null);
    await renderLogged(<RestaurantMenu restaurant={restaurant} user={staff} />);
    api.menu.mockRejectedValueOnce(new Error('Menu refresh unavailable'));
    await click(button('Remove Fries'));
    expect(entries()).toHaveLength(2);
    expect(entries()[0].textContent).toContain('Success · Remove menu item');
    expect(entries()[1].textContent).toContain('Error · Refresh menu');
  });

  it('logs local validation and backend menu errors without success', async () => {
    await renderLogged(<RestaurantMenu restaurant={restaurant} user={staff} />);
    await submit();
    expect(output().textContent).toContain('Menu item name is required.');
    await change(container.querySelector('.menu-fields input'), 'Fries');
    await change(container.querySelector('.menu-fields input[type="number"]'), '5.49');
    api.addMenuItem.mockRejectedValue(Object.assign(new Error('Only restaurant staff can manage menus'), { status: 403 }));
    await submit();
    expect(entries()).toHaveLength(2);
    expect(output().textContent).toContain('Only restaurant staff can manage menus');
    expect(output().textContent).not.toContain('Success');
  });

  it.each(['success', 'error'])('logs customer order placement %s once', async (outcome) => {
    if (outcome === 'success') api.placeOrder.mockResolvedValue(order());
    else api.placeOrder.mockRejectedValue(Object.assign(new Error('Menu item is unavailable'), { status: 400 }));
    await renderLogged(<RestaurantMenu restaurant={restaurant} user={{ id: 'customer-1', role: 'CUSTOMER' }} />);
    await click(button('Add one Fries'));
    const control = container.querySelector('.order-button');
    await act(async () => { control.click(); control.click(); });
    expect(api.placeOrder).toHaveBeenCalledTimes(1);
    expect(entries()).toHaveLength(1);
    expect(entries()[0].className).toContain(`--${outcome}`);
    expect(output().textContent).toContain(outcome === 'success' ? 'Status: PLACED' : 'Menu item is unavailable');
  });

  it('keeps customer placement success separate from a failed order-history refresh', async () => {
    api.currentUser.mockResolvedValue({ id: 'customer-1', role: 'CUSTOMER', username: 'customer' });
    api.placeOrder.mockResolvedValue(order());
    await act(async () => root.render(<RegistrationPage />));
    await click(button('Menu'));
    await click(button('Add one Fries'));
    api.orders.mockRejectedValueOnce(new Error('Order history refresh unavailable'));
    await click(container.querySelector('.order-button'));
    expect(entries()).toHaveLength(2);
    expect(entries()[0].textContent).toContain('Success · Place order');
    expect(entries()[1].textContent).toContain('Error · Refresh orders');
    expect(entries()[1].textContent).toContain('Order history refresh unavailable');
    expect(api.placeOrder).toHaveBeenCalledTimes(1);
  });
});

describe('backend order event history', () => {
  it('loads on demand, uses backend IDs/timestamps, filters unrelated events, and deduplicates across refreshes', async () => {
    const next = { ...event, eventId: 'backend-event-2', fromStatus: 'ACCEPTED', toStatus: 'PREPARING' };
    api.orderEvents.mockResolvedValue([event, event, next, { ...event, eventId: 'unrelated', orderId: 'foreign-order' }]);
    await renderLogged(<OrderEventHistory orderId="order-1" restaurantId={restaurant.id} />);
    expect(api.orderEvents).not.toHaveBeenCalled();
    await click(button('View event history'));
    expect(api.orderEvents).toHaveBeenCalledExactlyOnceWith('order-1');
    const panel = container.querySelector('[aria-label="Event history for order order-1"]');
    expect(panel.querySelectorAll('li')).toHaveLength(2);
    expect(panel.textContent).toContain('Event ID: backend-event-1');
    expect(panel.textContent).toContain('PLACED → ACCEPTED');
    expect(panel.querySelector('time').dateTime).toBe(event.occurredAt);
    expect(panel.textContent).not.toMatch(/private-actor-id|unrelated|foreign-order/);
    await click(button('Refresh event history'));
    expect(panel.querySelectorAll('li')).toHaveLength(2);
    expect(api.orderEvents).toHaveBeenCalledTimes(2);
  });

  it('guards duplicate event requests, displays loading and an empty trail', async () => {
    const request = deferred();
    api.orderEvents.mockReturnValue(request.promise);
    await renderLogged(<OrderEventHistory orderId="order-1" />);
    const control = button('View event history');
    await act(async () => { control.click(); control.click(); });
    expect(api.orderEvents).toHaveBeenCalledTimes(1);
    expect(container.querySelector('[role="status"]').textContent).toBe('Loading event history...');
    await act(async () => request.resolve([]));
    expect(container.textContent).toContain('No recorded transitions yet.');
    expect(entries()).toHaveLength(1);
  });

  it('reports an event-history failure without overwriting a successful transition', async () => {
    api.orders.mockResolvedValue([order()]);
    api.acceptOrder.mockResolvedValue(order('ACCEPTED'));
    api.orderEvents.mockRejectedValue(Object.assign(new Error('Event history unavailable'), { status: 503 }));
    await renderLogged(<RestaurantDashboard restaurant={restaurant} user={staff} />);
    api.orders.mockResolvedValue([order('ACCEPTED')]);
    api.preparationQueue.mockResolvedValue([order('ACCEPTED')]);
    await click(button('Accept'));
    await click(button('View event history'));
    expect(container.querySelector('.dashboard-success').textContent).toContain('ACCEPTED');
    expect(entries()).toHaveLength(2);
    expect(entries()[0].textContent).toContain('Success · Accept order');
    expect(entries()[1].textContent).toContain('Error · Load order events');
    expect(container.querySelector('[role="alert"]').textContent).toContain('Event history unavailable');
    expect(api.acceptOrder).toHaveBeenCalledTimes(1);
  });

  it('ignores late events from a previously selected order', async () => {
    const request = deferred();
    api.orderEvents.mockReturnValueOnce(request.promise).mockResolvedValueOnce([]);
    await renderLogged(<OrderEventHistory orderId="order-1" />);
    await click(button('View event history'));
    await renderLogged(<OrderEventHistory orderId="order-2" />);
    await click(button('View event history'));
    await act(async () => request.resolve([event]));
    expect(container.textContent).not.toContain('backend-event-1');
    expect(container.querySelector('[aria-label="Event history for order order-2"]')).not.toBeNull();
    expect(entries()).toHaveLength(1);
  });
});
