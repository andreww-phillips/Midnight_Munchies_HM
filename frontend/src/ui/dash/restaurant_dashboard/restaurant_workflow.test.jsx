// @vitest-environment jsdom
import React, { act } from 'react';
import { createRoot } from 'react-dom/client';
import { afterEach, beforeEach, expect, it, vi } from 'vitest';
import RegistrationPage from '../registration_page/registration_page';
import { api } from '../../../api';

vi.mock('../../../api', () => ({ api: {
  currentUser: vi.fn(), login: vi.fn(), logout: vi.fn(), restaurants: vi.fn(), orders: vi.fn(),
  preparationQueue: vi.fn(), acceptOrder: vi.fn(), rejectOrder: vi.fn(), startNextPreparation: vi.fn(),
  markOrderReady: vi.fn(), orderEvents: vi.fn()
} }));

const staff = { id: 'staff-1', role: 'RESTAURANT_STAFF', username: 'staff@example.com' };
const restaurant = { id: 'restaurant-1', ownerId: staff.id, name: 'Workflow Diner' };
const order = (id, status = 'PLACED', extra = {}) => ({ id, status, restaurantId: restaurant.id,
  orderLines: [{ menuItemId: 'fries', menuItemName: 'Workflow Fries', quantity: 2, unitPrice: '5.49' }], ...extra });
let root, container;
const button = (label, scope = container) => [...scope.querySelectorAll('button')].find((element) => element.textContent === label);
const group = (name) => container.querySelector(`section[aria-label="${name}"]`);
const card = (id, scope = container) => [...scope.querySelectorAll('article')].find((element) => element.querySelector('h4')?.textContent === `Order #${id}`);
const consoleEntries = () => [...container.querySelectorAll('.console-entry')];
async function click(element) { expect(element).toBeDefined(); await act(async () => element.click()); }
async function change(input, value) {
  await act(async () => {
    Object.getOwnPropertyDescriptor(HTMLInputElement.prototype, 'value').set.call(input, value);
    input.dispatchEvent(new Event('input', { bubbles: true }));
  });
}
async function submit(form) {
  await act(async () => form.dispatchEvent(new Event('submit', { bubbles: true, cancelable: true })));
}
function snapshot(orders, queue = []) {
  // Script server responses explicitly; transition rules remain in the real backend tests.
  api.orders.mockResolvedValue(orders);
  api.preparationQueue.mockResolvedValue(queue);
}
async function login(account = staff) {
  api.login.mockResolvedValueOnce(account);
  await change(container.querySelector('[name="username"]'), account.username);
  await change(container.querySelector('[name="password"]'), 'demo-password');
  await submit(container.querySelector('form'));
}

beforeEach(async () => {
  vi.resetAllMocks();
  vi.stubGlobal('IS_REACT_ACT_ENVIRONMENT', true);
  api.currentUser.mockRejectedValue(Object.assign(new Error('Authentication required'), { status: 401 }));
  api.logout.mockResolvedValue(null);
  api.restaurants.mockResolvedValue([restaurant, { id: 'foreign-restaurant', name: 'Other Diner', ownerId: 'other-staff' }]);
  snapshot([]);
  container = document.createElement('div');
  document.body.append(container);
  root = createRoot(container);
  await act(async () => root.render(<RegistrationPage />));
});
afterEach(async () => {
  await act(async () => root.unmount());
  container.remove();
  vi.unstubAllGlobals();
});

it('runs the restaurant workflow from login through reverse acceptance, rejection, FIFO, ready, and event IDs', async () => {
  await login();
  expect([...container.querySelectorAll('button')].filter((element) => element.textContent === 'Manage orders')).toHaveLength(1);
  await click(button('Manage orders'));
  expect(group('Incoming orders').textContent).toContain('No incoming orders.');

  const first = order('placed-first'), second = order('placed-second'), third = order('to-reject');
  let finishIncoming;
  api.orders.mockReturnValueOnce(new Promise((resolve) => { finishIncoming = resolve; }));
  await click(button('Refresh orders'));
  expect(container.querySelector('[role="status"]').textContent).toContain('Loading');
  expect(button('Refresh orders').disabled).toBe(true);
  await act(async () => finishIncoming([first, second, third]));
  expect(group('Incoming orders').querySelectorAll('article')).toHaveLength(3);
  expect(card(first.id).textContent).toContain('2 × Workflow Fries');

  const secondAccepted = order(second.id, 'ACCEPTED');
  api.acceptOrder.mockResolvedValueOnce(secondAccepted);
  snapshot([first, secondAccepted, third], [secondAccepted]);
  await click(button('Accept', card(second.id)));
  const firstAccepted = order(first.id, 'ACCEPTED');
  api.acceptOrder.mockResolvedValueOnce(firstAccepted);
  snapshot([firstAccepted, secondAccepted, third], [secondAccepted, firstAccepted]);
  await click(button('Accept', card(first.id)));
  expect([...group('Preparation queue').querySelectorAll('h4')].map((element) => element.textContent))
    .toEqual(['Order #placed-second', 'Order #placed-first']);
  expect(button('Accept', card(second.id))).toBeUndefined();
  expect(button('Mark Ready', card(first.id))).toBeUndefined();

  await change(card(third.id).querySelector('input'), '  Kitchen closed  ');
  const rejected = order(third.id, 'REJECTED', { rejectionReason: 'Kitchen closed' });
  api.rejectOrder.mockResolvedValueOnce(rejected);
  snapshot([firstAccepted, secondAccepted, rejected], [secondAccepted, firstAccepted]);
  await submit(card(third.id).querySelector('form'));
  expect(api.rejectOrder).toHaveBeenCalledExactlyOnceWith(restaurant.id, third.id, 'Kitchen closed');
  expect(card(third.id, group('Other orders')).textContent).toContain('Rejection reason: Kitchen closed');
  expect(button('Reject', card(third.id))).toBeUndefined();

  const preparing = order(second.id, 'PREPARING');
  api.startNextPreparation.mockResolvedValueOnce(preparing);
  snapshot([firstAccepted, preparing, rejected], [firstAccepted]);
  await click(button('Start next preparation'));
  expect(api.startNextPreparation).toHaveBeenCalledExactlyOnceWith(restaurant.id);
  expect(card(second.id, group('Preparing'))).toBeDefined();
  expect(group('Preparation queue').querySelectorAll(':scope > button')).toHaveLength(1);
  expect(button('Start next preparation').disabled).toBe(false);

  const ready = order(second.id, 'READY');
  let finishReady;
  api.markOrderReady.mockReturnValueOnce(new Promise((resolve) => { finishReady = resolve; }));
  await click(button('Mark Ready', card(second.id)));
  expect(button('Start next preparation').disabled).toBe(true);
  expect(button('Mark Ready', card(second.id)).disabled).toBe(true);
  expect(container.querySelector('.dashboard-success')).toBeNull();
  snapshot([firstAccepted, ready, rejected], [firstAccepted]);
  await act(async () => finishReady(ready));
  expect(card(second.id, group('Ready'))).toBeDefined();
  expect(button('Mark Ready', card(second.id))).toBeUndefined();
  expect(container.querySelector('.dashboard-success').textContent).toContain('READY');
  expect(container.querySelector('h2').textContent).toBe('Workflow Diner orders');

  const events = ['ACCEPTED', 'PREPARING', 'READY'].map((toStatus, index) => ({
    eventId: `server-event-${index + 1}`, orderId: second.id, restaurantId: restaurant.id,
    fromStatus: ['PLACED', 'ACCEPTED', 'PREPARING'][index], toStatus,
    occurredAt: `2026-10-23T22:00:0${index}Z`, actorId: staff.id, actorRole: staff.role
  }));
  api.orderEvents.mockResolvedValueOnce(events);
  await click(button('View event history', card(second.id)));
  expect(api.orderEvents).toHaveBeenCalledExactlyOnceWith(second.id);
  const trail = container.querySelector(`[aria-label="Event history for order ${second.id}"]`);
  expect(trail.querySelectorAll('li')).toHaveLength(3);
  for (const event of events) expect(trail.textContent).toContain(`Event ID: ${event.eventId}`);
  const commands = consoleEntries().map((entry) => entry.querySelector('strong').textContent);
  expect(commands.filter((text) => text === 'Success · Accept order')).toHaveLength(2);
  for (const action of ['Log in', 'Refresh restaurant orders', 'Reject order', 'Start preparation', 'Mark order ready', 'Load order events']) {
    expect(commands.filter((text) => text === `Success · ${action}`)).toHaveLength(1);
  }

  await click(button('Log out'));
  expect(consoleEntries()).toHaveLength(1);
  expect(container.querySelector('[role="log"]').textContent).not.toContain(second.id);
  await login({ id: 'customer-1', role: 'CUSTOMER', username: 'customer@example.com' });
  expect(button('Manage orders')).toBeUndefined();
  expect(container.querySelector('.order-history').textContent).toContain('READY');
  expect(container.querySelector('.order-history').textContent).toContain('Workflow Fries');
});

it('recovers from a concurrent transition, distinguishes refresh failure from mutation failure, and handles expiry', async () => {
  snapshot([order('conflict')]);
  await login();
  await click(button('Manage orders'));
  const rejected = order('conflict', 'REJECTED', { rejectionReason: 'Another session rejected it' });
  api.acceptOrder.mockRejectedValueOnce(Object.assign(new Error('Cannot accept an order that is REJECTED'), { status: 409 }));
  snapshot([rejected]);
  await click(button('Accept', card('conflict')));
  expect(container.querySelector('[role="alert"]').textContent).toContain('Cannot accept');
  expect(card('conflict', group('Other orders')).textContent).toContain('Another session rejected it');
  expect(container.querySelector('.dashboard-success')).toBeNull();
  expect(consoleEntries().filter((entry) => entry.textContent.includes('Error · Accept order'))).toHaveLength(1);

  snapshot([rejected, order('new-order')]);
  await click(button('Refresh orders'));
  api.acceptOrder.mockResolvedValueOnce(order('new-order', 'ACCEPTED'));
  api.orders.mockRejectedValueOnce(new Error('Dashboard refresh unavailable'));
  await click(button('Accept', card('new-order')));
  expect(container.querySelector('.dashboard-success').textContent).toContain('new-order is now ACCEPTED');
  expect(container.querySelector('[role="alert"]').textContent).toContain('operation succeeded, but the dashboard could not refresh');
  expect(button('Accept')).toBeUndefined();
  const lastEntries = consoleEntries().slice(-2);
  expect(lastEntries[0].textContent).toContain('Success · Accept order');
  expect(lastEntries[1].textContent).toContain('Error · Refresh restaurant orders');
  expect(api.acceptOrder).toHaveBeenCalledTimes(2);

  api.orders.mockRejectedValueOnce(Object.assign(new Error('Authentication required'), { status: 401 }));
  await click(button('Refresh orders'));
  expect(container.querySelector('.auth-card form')).not.toBeNull();
  expect(container.querySelector('[role="alert"]').textContent).toContain('Your session expired');
  expect(consoleEntries()).toHaveLength(1);
  expect(container.querySelector('[role="log"]').textContent).not.toMatch(/conflict|new-order|restaurant-1/);
  expect(api.acceptOrder).toHaveBeenCalledTimes(2);
});
