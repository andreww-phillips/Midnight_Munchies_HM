// @vitest-environment jsdom
// Characterisation of the cancel and refund commands in order_history.jsx (R2).
// These tests pin the current behaviour so the commands can be restructured safely.
import React, { act } from 'react';
import { createRoot } from 'react-dom/client';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { api } from '../../api';
import OrderHistory from './order_history';
import { CommandLogContext } from './output_console/command_log';

vi.mock('../../api', () => ({ api: {
  orderHistory: vi.fn(), refundOrder: vi.fn(), cancelOrder: vi.fn(), orderSummary: vi.fn(), orderEvents: vi.fn(),
  orderReviews: vi.fn()
} }));

const customer = { id: 'customer-1', role: 'CUSTOMER' };
const operator = { id: 'operator-1', role: 'PLATFORM_OPERATOR' };
const ID_A = 'aaaaaaaa-0000-0000-0000-000000000001';
const ID_B = 'bbbbbbbb-0000-0000-0000-000000000002';
const RESTAURANT = 'restaurant-1';
const CREATED = '2026-11-13T01:00:00Z';

const base = {
  orderId: ID_A, customerId: customer.id, restaurantId: RESTAURANT, createdAt: CREATED, billing: null,
  timestamps: { PLACED: CREATED },
  orderLines: [{ menuItemId: 'item-1', menuItemName: 'Original Poutine', quantity: 2, unitPrice: '12.35' }]
};
const placedA = { ...base, status: 'PLACED' };
const placedB = { ...base, orderId: ID_B, status: 'PLACED' };
const delivered = {
  ...base, status: 'DELIVERED', timestamps: { PLACED: CREATED, DELIVERED: '2026-11-13T01:10:00Z' },
  billing: { totalCharge: '30.19', pricingPending: false, refunds: [] }
};
const deliveredB = { ...delivered, orderId: ID_B };
const summary = { ...delivered, deliveredAt: '2026-11-13T01:10:00Z', totalCharge: '30.19', pricingPending: false };
const credit = { refundId: 'refund-1', orderId: ID_A, amount: '30.19', refundedAt: '2026-11-15T06:00:00Z',
  operatorId: operator.id };
const refundResult = { orderId: ID_A, status: 'REFUNDED', refund: credit, eventId: 'event-refund' };
const cancelResult = { id: ID_A, status: 'CANCELLED' };

const CANCEL_MISMATCH = 'The cancellation response does not match this order.';
const REFUND_MISMATCH = 'The refund response does not match this order.';

let container, root, record, unmounted;

beforeEach(() => {
  vi.resetAllMocks();
  vi.stubGlobal('IS_REACT_ACT_ENVIRONMENT', true);
  api.orderSummary.mockResolvedValue(summary);
  api.orderEvents.mockResolvedValue([]);
  api.orderReviews.mockResolvedValue({ eligible: false });
  record = vi.fn();
  unmounted = false;
  container = document.createElement('div');
  document.body.append(container);
  root = createRoot(container);
});

afterEach(async () => {
  if (!unmounted) await act(async () => root.unmount());
  container.remove();
  vi.unstubAllGlobals();
});

async function mount(user, orders, props = {}) {
  api.orderHistory.mockResolvedValue(orders);
  await act(async () => root.render(
    <CommandLogContext.Provider value={{ entries: [], record, changeIdentity: () => {} }}>
      <OrderHistory user={user} {...props} />
    </CommandLogContext.Provider>
  ));
}

const cancelButtons = () => [...container.querySelectorAll('.history-cancel-button')];
const refundButtons = () => [...container.querySelectorAll('.history-refund-button')];
const alertText = () => container.querySelector('[role="alert"]')?.textContent;
const refreshButton = () => [...container.querySelectorAll('button')].find((b) => b.textContent === 'Refresh orders');
const clickFirst = (buttons) => act(async () => buttons()[0].click());
const failure = (message, status) => Object.assign(new Error(message), status === undefined ? {} : { status });
const commandRecords = (action) => record.mock.calls.map(([entry]) => entry).filter((entry) => entry.action === action);

function deferred() {
  let resolve, reject;
  const promise = new Promise((done, fail) => { resolve = done; reject = fail; });
  return { promise, resolve, reject };
}

const commands = {
  cancel: {
    user: customer, orders: [placedA], buttons: cancelButtons, api: () => api.cancelOrder,
    success: cancelResult, action: 'Cancel order'
  },
  refund: {
    user: operator, orders: [delivered], buttons: refundButtons, api: () => api.refundOrder,
    success: refundResult, action: 'Refund order'
  }
};

describe('response check', () => {
  // 1
  it.each([
    ['another order id', { id: 'other-order', status: 'CANCELLED' }],
    ['a status other than CANCELLED', { id: ID_A, status: 'ACCEPTED' }],
    ['an empty body', undefined],
    ['a null body', null]
  ])('rejects a cancel response with %s, logs it and reconciles the list', async (_, result) => {
    await mount(customer, [placedA]);
    api.cancelOrder.mockResolvedValue(result);
    await clickFirst(cancelButtons);

    expect(alertText()).toBe(CANCEL_MISMATCH);
    expect(commandRecords('Cancel order')).toEqual([
      expect.objectContaining({ action: 'Cancel order', outcome: 'error', orderId: ID_A, restaurantId: RESTAURANT,
        message: CANCEL_MISMATCH })
    ]);
    expect(container.textContent).not.toContain('cancelled.');
    expect(container.querySelector('.order-card h4').textContent).toContain('PLACED');
    expect(api.orderHistory).toHaveBeenCalledTimes(2);
  });

  // 2
  it.each([
    ['another order id', { ...refundResult, orderId: 'other-order' }],
    ['a status other than REFUNDED', { ...refundResult, status: 'PENDING' }],
    ['a refund for another order', { ...refundResult, refund: { ...credit, orderId: 'other-order' } }],
    ['no refund', { orderId: ID_A, status: 'REFUNDED' }],
    ['an empty body', undefined]
  ])('rejects a refund response with %s, logs it and reconciles the list', async (_, result) => {
    await mount(operator, [delivered]);
    api.refundOrder.mockResolvedValue(result);
    await clickFirst(refundButtons);

    expect(alertText()).toBe(REFUND_MISMATCH);
    expect(commandRecords('Refund order')).toEqual([
      expect.objectContaining({ action: 'Refund order', outcome: 'error', orderId: ID_A, restaurantId: RESTAURANT,
        message: REFUND_MISMATCH })
    ]);
    expect(container.textContent).not.toContain('refunded $');
    expect(api.orderHistory).toHaveBeenCalledTimes(2);
  });
});

describe('error policy', () => {
  // 3
  it('reloads the history when a cancel fails with 404 and logs the status', async () => {
    await mount(customer, [placedA]);
    api.cancelOrder.mockRejectedValue(failure('Order does not exist', 404));
    await clickFirst(cancelButtons);

    expect(alertText()).toBe('Order does not exist');
    expect(commandRecords('Cancel order')).toEqual([
      expect.objectContaining({ outcome: 'error', message: 'Order does not exist', httpStatus: 404 })
    ]);
    expect(api.orderHistory).toHaveBeenCalledTimes(2);
  });

  // 4
  it('reloads the history when a cancel fails without an HTTP status, such as a network error', async () => {
    await mount(customer, [placedA]);
    api.cancelOrder.mockRejectedValue(new TypeError('Failed to fetch'));
    await clickFirst(cancelButtons);

    expect(alertText()).toBe('Failed to fetch');
    expect(api.orderHistory).toHaveBeenCalledTimes(2);
    expect(commandRecords('Cancel order')[0]).toEqual(
      expect.objectContaining({ outcome: 'error', message: 'Failed to fetch' })
    );
  });

  // 5
  it.each([404, 409, undefined])('reloads the history when a refund fails with status %s', async (status) => {
    await mount(operator, [delivered]);
    api.refundOrder.mockRejectedValue(failure('Refund conflict', status));
    await clickFirst(refundButtons);

    expect(alertText()).toBe('Refund conflict');
    expect(api.orderHistory).toHaveBeenCalledTimes(2);
    expect(commandRecords('Refund order')[0]).toEqual(
      expect.objectContaining({ outcome: 'error', message: 'Refund conflict', httpStatus: status })
    );
  });

  // 6
  it.each([
    ['cancel', 403], ['cancel', 500], ['refund', 403], ['refund', 500]
  ])('does not reload the history when a %s fails with %s, and the control is usable again', async (name, status) => {
    const command = commands[name];
    await mount(command.user, command.orders);
    command.api().mockRejectedValue(failure('Command failed', status));
    await clickFirst(command.buttons);

    expect(alertText()).toBe('Command failed');
    expect(api.orderHistory).toHaveBeenCalledTimes(1);
    expect(command.buttons()).toHaveLength(1);
    expect(command.buttons()[0].disabled).toBe(false);
    expect(command.buttons()[0].textContent).toBe(name === 'cancel' ? 'Cancel order' : 'Refund order');
  });
});

describe('pending command', () => {
  // 7
  it('disables every cancel control and the refresh control while one cancel is pending', async () => {
    await mount(customer, [placedA, placedB]);
    const pending = deferred();
    api.cancelOrder.mockReturnValue(pending.promise);
    await clickFirst(cancelButtons);

    const [first, second] = cancelButtons();
    expect(first.disabled).toBe(true);
    expect(second.disabled).toBe(true);
    expect(first.textContent).toBe('Cancelling...');
    expect(second.textContent).toBe('Cancel order');
    expect(refreshButton().disabled).toBe(true);

    api.orderHistory.mockResolvedValue([{ ...placedA, status: 'CANCELLED' }, placedB]);
    await act(async () => pending.resolve(cancelResult));
    expect(cancelButtons()).toHaveLength(1);
    expect(cancelButtons()[0].disabled).toBe(false);
    expect(refreshButton().disabled).toBe(false);
  });

  it('disables every refund control and the refresh control while one refund is pending', async () => {
    await mount(operator, [delivered, deliveredB]);
    const pending = deferred();
    api.refundOrder.mockReturnValue(pending.promise);
    await clickFirst(refundButtons);

    const [first, second] = refundButtons();
    expect(first.disabled).toBe(true);
    expect(second.disabled).toBe(true);
    expect(first.textContent).toBe('Refunding...');
    expect(second.textContent).toBe('Refund order');
    expect(refreshButton().disabled).toBe(true);

    api.orderHistory.mockResolvedValue([
      { ...delivered, status: 'REFUNDED', billing: { ...delivered.billing, refunds: [credit] } }, deliveredB
    ]);
    await act(async () => pending.resolve(refundResult));
    expect(refundButtons()).toHaveLength(1);
    expect(refundButtons()[0].disabled).toBe(false);
  });

  // 8
  it('clears the previous success notice when the next command starts', async () => {
    await mount(customer, [placedA, placedB]);
    api.cancelOrder.mockResolvedValueOnce(cancelResult);
    api.orderHistory.mockResolvedValue([{ ...placedA, status: 'CANCELLED' }, placedB]);
    await clickFirst(cancelButtons);
    expect(container.textContent).toContain('Order #aaaaaaaa cancelled.');

    api.cancelOrder.mockReturnValue(deferred().promise);
    await clickFirst(cancelButtons);

    expect(container.textContent).not.toContain('Order #aaaaaaaa cancelled.');
    expect(container.querySelector('[role="status"]')).toBeNull();
  });

  it('clears the previous error when the next command starts', async () => {
    await mount(customer, [placedA]);
    api.cancelOrder.mockRejectedValueOnce(failure('Denied', 403));
    await clickFirst(cancelButtons);
    expect(alertText()).toBe('Denied');

    api.cancelOrder.mockReturnValue(deferred().promise);
    await clickFirst(cancelButtons);

    expect(alertText()).toBeUndefined();
  });
});

describe('after unmount', () => {
  // 9
  it.each([
    ['cancel', 'resolves'], ['cancel', 'rejects'], ['refund', 'resolves'], ['refund', 'rejects']
  ])('a %s that %s after the component unmounts logs nothing and does not reload', async (name, outcome) => {
    const command = commands[name];
    await mount(command.user, command.orders);
    const pending = deferred();
    command.api().mockReturnValue(pending.promise);
    await clickFirst(command.buttons);
    await act(async () => root.unmount());
    unmounted = true;
    const logged = record.mock.calls.length;

    await act(async () => (outcome === 'resolves' ? pending.resolve(command.success) : pending.reject(failure('Late failure', 500))));

    expect(record.mock.calls.length).toBe(logged);
    expect(commandRecords(command.action)).toEqual([]);
    expect(api.orderHistory).toHaveBeenCalledTimes(1);
  });
});

describe('log entries', () => {
  // 10
  it('records exactly these fields for a successful cancel and the refresh that follows', async () => {
    await mount(customer, [placedA]);
    api.cancelOrder.mockResolvedValue(cancelResult);
    api.orderHistory.mockResolvedValue([{ ...placedA, status: 'CANCELLED' }]);
    await clickFirst(cancelButtons);

    expect(record.mock.calls.map(([entry]) => entry)).toEqual([
      { action: 'Cancel order', outcome: 'success', orderId: ID_A, restaurantId: RESTAURANT, status: 'CANCELLED',
        message: 'Order #aaaaaaaa cancelled.' },
      { action: 'Refresh orders', outcome: 'success', message: 'Order history refreshed.' }
    ]);
  });

  it('records exactly these fields for a successful refund and the refresh that follows', async () => {
    await mount(operator, [delivered]);
    api.refundOrder.mockResolvedValue(refundResult);
    await clickFirst(refundButtons);

    // The delivery summary shown on a delivered order logs its own entries; they are not part of the command.
    const own = record.mock.calls.map(([entry]) => entry).filter((entry) => entry.action !== 'Load order summary');
    expect(own).toEqual([
      { action: 'Refund order', outcome: 'success', orderId: ID_A, restaurantId: RESTAURANT, status: 'REFUNDED',
        message: 'Order #aaaaaaaa refunded $30.19.' },
      { action: 'Refresh orders', outcome: 'success', message: 'Order history refreshed.' }
    ]);
  });

  it('records exactly these fields for a failed command', async () => {
    await mount(customer, [placedA]);
    api.cancelOrder.mockRejectedValue(failure('Not allowed', 403));
    await clickFirst(cancelButtons);

    expect(record.mock.calls.map(([entry]) => entry)).toEqual([
      { action: 'Cancel order', outcome: 'error', orderId: ID_A, restaurantId: RESTAURANT, message: 'Not allowed',
        httpStatus: 403 }
    ]);
  });
});

describe('who can start a command', () => {
  // 11
  it.each([
    ['cancel', 'a customer viewing an accepted order', customer, [{ ...placedA, status: 'ACCEPTED' }]],
    ['cancel', 'a customer viewing another customer\'s order', customer, [{ ...placedA, customerId: 'other-customer' }]],
    ['cancel', 'an operator viewing a placed order', operator, [placedA]],
    ['refund', 'a customer viewing a delivered order', customer, [delivered]],
    ['refund', 'an operator viewing an order with pricing pending',
      operator, [{ ...delivered, billing: { totalCharge: null, pricingPending: true, refunds: [] } }]],
    ['refund', 'an operator viewing an order that already has a refund',
      operator, [{ ...delivered, billing: { ...delivered.billing, refunds: [credit] } }]]
  ])('offers no %s control to %s and sends no request', async (name, _, user, orders) => {
    await mount(user, orders);

    expect(commands[name].buttons()).toHaveLength(0);
    expect(api.cancelOrder).not.toHaveBeenCalled();
    expect(api.refundOrder).not.toHaveBeenCalled();
  });
});
