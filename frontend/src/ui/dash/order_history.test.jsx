// @vitest-environment jsdom
import React, { act } from 'react';
import { createRoot } from 'react-dom/client';
import { afterEach, beforeEach, expect, it, vi } from 'vitest';
import { api } from '../../api';
import OrderHistory from './order_history';
import RestaurantList from './restaurant_library/restaurant_library';
import { CommandLogProvider } from './output_console/command_log_provider';
import OutputConsole from './output_console/output_console';

vi.mock('../../api', () => ({ api: {
  orderHistory: vi.fn(), refundOrder: vi.fn(), cancelOrder: vi.fn(), orderSummary: vi.fn(), orderEvents: vi.fn(), restaurants: vi.fn()
} }));

const customer = { id: 'customer-1', role: 'CUSTOMER' };
const order = {
  orderId: 'aaaaaaaa-0000-0000-0000-000000000001', customerId: customer.id, restaurantId: 'restaurant-1', status: 'DELIVERED',
  createdAt: '2026-11-13T01:00:00Z', timestamps: { PLACED: '2026-11-13T01:00:00Z', DELIVERED: '2026-11-13T01:10:00Z' },
  billing: { totalCharge: null, pricingPending: true, refunds: [] },
  orderLines: [{ menuItemId: 'item-1', menuItemName: 'Original Poutine', quantity: 2, unitPrice: '12.35' }]
};
const summary = {
  ...order, orderId: order.orderId, createdAt: '2026-11-13T01:00:00Z', deliveredAt: '2026-11-13T01:10:00Z',
  totalCharge: null, pricingPending: true
};
let container, root;

beforeEach(() => {
  vi.resetAllMocks();
  vi.stubGlobal('IS_REACT_ACT_ENVIRONMENT', true);
  api.orderHistory.mockResolvedValue([order]);
  api.orderSummary.mockResolvedValue(summary);
  api.orderEvents.mockResolvedValue([]);
  api.restaurants.mockResolvedValue([]);
  container = document.createElement('div');
  document.body.append(container);
  root = createRoot(container);
});

afterEach(async () => {
  await act(async () => root.unmount());
  container.remove();
  vi.unstubAllGlobals();
});

async function render(user = customer) {
  await act(async () => root.render(<OrderHistory user={user} refreshVersion={0} />));
}

async function click(label) {
  const button = [...container.querySelectorAll('button')].find((element) => element.textContent === label);
  expect(button).toBeDefined();
  await act(async () => button.click());
}

it('adds a delivery summary while preserving the existing order card and event history', async () => {
  await render();

  expect(container.textContent).toContain('Your orders');
  expect(container.textContent).toContain('Order #aaaaaaaa');
  expect(container.querySelector('.order-card > ul').textContent).toContain('Original Poutine');
  expect(container.querySelector('.order-summary').textContent).toContain('Pricing pending');
  await click('View event history');
  expect(api.orderEvents).toHaveBeenCalledWith(order.orderId);
  expect(container.textContent).toContain('No recorded transitions yet.');
});

it.each(['PLACED', 'ACCEPTED', 'PREPARING', 'READY', 'PICKED_UP', 'REJECTED', 'CANCELLED'])
  ('does not request a summary for an order in state %s', async (status) => {
    api.orderHistory.mockResolvedValue([{ ...order, status }]);
    await render();

    expect(api.orderSummary).not.toHaveBeenCalled();
    expect(container.querySelector('.order-summary')).toBeNull();
    expect(container.textContent).toContain('Original Poutine');
  });

it.each([
  ['restaurant staff', { id: 'staff-1', role: 'RESTAURANT_STAFF' }],
  ['courier', { id: 'courier-1', role: 'COURIER' }],
  ['missing identity', { role: 'CUSTOMER' }],
  ['anonymous', null]
])('does not request billing details for %s', async (_, user) => {
  await render(user);

  expect(api.orderSummary).not.toHaveBeenCalled();
  expect(container.querySelector('.order-summary')).toBeNull();
});

it('loads a summary when an existing order refresh reveals delivery and keeps event history open', async () => {
  api.orderHistory.mockResolvedValueOnce([{ ...order, status: 'PICKED_UP' }]).mockResolvedValueOnce([order]);
  await render();
  await click('View event history');
  expect(api.orderSummary).not.toHaveBeenCalled();
  await click('Refresh orders');

  expect(api.orderSummary).toHaveBeenCalledWith(order.orderId);
  expect(container.textContent).toContain('Delivery summary');
  expect(container.textContent).toContain('No recorded transitions yet.');
  expect(container.textContent).toContain('Refresh event history');
});

it('refreshes an already displayed summary along with the orders list', async () => {
  await render();
  api.orderSummary.mockResolvedValue({ ...summary, totalCharge: '30.70', pricingPending: false });
  await click('Refresh orders');

  expect(api.orderSummary).toHaveBeenCalledTimes(2);
  expect(container.querySelector('.order-summary-total').textContent).toContain('$30.70');
});

it('supports delivered and future refunded summaries for an authorized operator history view', async () => {
  api.orderHistory.mockResolvedValue([{ ...order, status: 'REFUNDED' }]);
  api.orderSummary.mockResolvedValue({ ...summary, status: 'REFUNDED', totalCharge: '30.70', pricingPending: false });
  await render({ id: 'operator-1', role: 'PLATFORM_OPERATOR' });

  expect(api.orderSummary).toHaveBeenCalledWith(order.orderId);
  expect(container.querySelector('.order-summary').textContent).toContain('Refunded');
});

it('passes the logged-in customer through the existing restaurant library to the summary', async () => {
  await act(async () => root.render(<RestaurantList user={customer} />));

  expect(api.restaurants).toHaveBeenCalled();
  expect(api.orderHistory).toHaveBeenCalled();
  expect(api.orderSummary).toHaveBeenCalledWith(order.orderId);
  expect(container.querySelector('.order-summary').textContent).toContain('Pricing pending');
});

it('shows the restaurant rejection reason in the customer history and inline order list', async () => {
  api.orderHistory.mockResolvedValue([{ ...order, status: 'REJECTED', rejectionReason: 'Kitchen unavailable' }]);
  await render();
  expect(container.querySelector('.order-card').textContent).toContain('Rejection reason: Kitchen unavailable');
  expect(api.orderSummary).not.toHaveBeenCalled();

  await act(async () => root.render(<RestaurantList user={customer} />));
  expect(container.querySelector('.order-card').textContent).toContain('Rejection reason: Kitchen unavailable');
});

const operator = { id: 'operator-1', role: 'PLATFORM_OPERATOR' };
const paid = { ...order, billing: { totalCharge: '30.19', pricingPending: false, refunds: [] } };
const credit = { refundId: 'refund-1', orderId: order.orderId, amount: '30.19',
  refundedAt: '2026-11-15T06:00:00Z', operatorId: operator.id };
const refunded = { ...paid, status: 'REFUNDED', billing: { ...paid.billing, refunds: [credit] },
  timestamps: { ...paid.timestamps, REFUNDED: credit.refundedAt } };
const refundResult = { orderId: order.orderId, status: 'REFUNDED', refund: credit, eventId: 'event-refund' };

async function renderRefundHistory(user = operator, onSessionExpired) {
  api.orderHistory.mockResolvedValue([paid]);
  api.orderSummary.mockResolvedValue({ ...summary, totalCharge: '30.19', pricingPending: false });
  await act(async () => root.render(<CommandLogProvider>
    <OrderHistory user={user} onSessionExpired={onSessionExpired} /><OutputConsole />
  </CommandLogProvider>));
}

function deferred() {
  let resolve;
  const promise = new Promise((finish) => { resolve = finish; });
  return { promise, resolve };
}

it('shows only operator refund controls and uses the session-scoped history API', async () => {
  await renderRefundHistory();
  expect(api.orderHistory).toHaveBeenCalledWith();
  expect(container.textContent).toContain('All orders and billing');
  expect([...container.querySelectorAll('button')].some((button) => button.textContent === 'Refund order')).toBe(true);
});

it.each(['CUSTOMER', 'RESTAURANT_STAFF', 'COURIER'])('never offers refunds to %s', async (role) => {
  await renderRefundHistory({ id: 'viewer-1', role });
  expect(container.querySelector('.history-refund-button')).toBeNull();
});

it.each([
  { ...paid, status: 'READY' },
  { ...paid, billing: { totalCharge: null, pricingPending: true, refunds: [] } },
  refunded,
  { ...paid, billing: { ...paid.billing, refunds: [credit] } }
])('does not offer a refund for an ineligible history snapshot %#', async (entry) => {
  api.orderHistory.mockResolvedValue([entry]);
  await render(operator);
  expect(container.querySelector('.history-refund-button')).toBeNull();
});

it('allows refunding a recorded zero charge', async () => {
  api.orderHistory.mockResolvedValue([{ ...paid, billing: { ...paid.billing, totalCharge: '0.00' } }]);
  await render(operator);
  expect(container.querySelector('.history-refund-button')).not.toBeNull();
});

it('refreshes history and the summary after refund and logs the command', async () => {
  await renderRefundHistory();
  api.refundOrder.mockResolvedValue(refundResult);
  api.orderHistory.mockResolvedValue([refunded]);
  api.orderSummary.mockResolvedValue({ ...summary, status: 'REFUNDED', totalCharge: '30.19', pricingPending: false });
  await click('Refund order');

  expect(api.refundOrder).toHaveBeenCalledExactlyOnceWith(order.orderId);
  expect(container.querySelector('.order-card h4').textContent).toContain('REFUNDED');
  expect(container.querySelector('.order-summary-total').textContent).toContain('Original charge: $30.19');
  expect(container.querySelector('.history-refunds').textContent).toContain('Refund amount: $30.19');
  expect(container.querySelector('.history-refunds time').dateTime).toBe(credit.refundedAt);
  expect(container.querySelector('.history-refund-button')).toBeNull();
  expect(container.querySelector('[role="log"]').textContent).toContain('Success · Refund order');
  expect(container.querySelector('[role="log"]').textContent).toContain('Success · Refresh orders');
});

it('prevents duplicate refund clicks while a command is pending', async () => {
  await renderRefundHistory();
  const pending = deferred();
  api.refundOrder.mockReturnValue(pending.promise);
  const button = container.querySelector('.history-refund-button');
  await act(async () => { button.click(); button.click(); });
  expect(api.refundOrder).toHaveBeenCalledTimes(1);
  expect(button.disabled).toBe(true);
  expect(button.textContent).toBe('Refunding...');
  api.orderHistory.mockResolvedValue([refunded]);
  await act(async () => pending.resolve(refundResult));
  expect(container.querySelector('.history-refund-button')).toBeNull();
});

it.each([403, 409, 500])('shows and logs refund HTTP %s errors without a false success', async (status) => {
  await renderRefundHistory();
  api.refundOrder.mockRejectedValue(Object.assign(new Error('Refund command denied'), { status }));
  if (status === 409) api.orderHistory.mockResolvedValue([refunded]);
  await click('Refund order');
  expect(container.querySelector('[role="alert"]').textContent).toBe('Refund command denied');
  expect(container.querySelector('[role="log"]').textContent).toContain(`HTTP ${status}`);
  expect(container.querySelector('[role="log"]').textContent).not.toContain('Success · Refund order');
  if (status === 409) expect(container.querySelector('.history-refund-button')).toBeNull();
});

it('preserves a successful refund notice when the following history refresh fails', async () => {
  await renderRefundHistory();
  api.refundOrder.mockResolvedValue(refundResult);
  api.orderHistory.mockRejectedValue(new Error('History unavailable'));
  await click('Refund order');
  expect(container.textContent).toContain('refunded $30.19');
  expect(container.querySelector('[role="alert"]').textContent).toContain('History unavailable');
  expect(container.querySelector('.history-refund-button')).toBeNull();
});

it('shows courier facts without prices, billing, refunds, or unavailable event controls', async () => {
  api.orderHistory.mockResolvedValue([refunded]);
  await render({ id: 'courier-1', role: 'COURIER' });
  expect(container.textContent).toContain('Your delivery history');
  expect(container.textContent).toContain('Original Poutine');
  expect(container.textContent).not.toContain('$');
  expect(container.querySelector('.order-summary')).toBeNull();
  expect(container.querySelector('.history-refunds')).toBeNull();
  expect(container.querySelector('.order-event-history')).toBeNull();
  expect(api.orderSummary).not.toHaveBeenCalled();
  expect(api.orderEvents).not.toHaveBeenCalled();
});

it('handles session expiry during a history read and clears displayed records', async () => {
  const onSessionExpired = vi.fn();
  await renderRefundHistory(operator, onSessionExpired);
  api.orderHistory.mockRejectedValue(Object.assign(new Error('Authentication required'), { status: 401 }));
  await click('Refresh orders');
  expect(onSessionExpired).toHaveBeenCalledOnce();
  expect(container.querySelector('.order-card')).toBeNull();
});

it('handles session expiry during a refund and clears displayed records', async () => {
  const onSessionExpired = vi.fn();
  await renderRefundHistory(operator, onSessionExpired);
  api.refundOrder.mockRejectedValue(Object.assign(new Error('Authentication required'), { status: 401 }));
  await click('Refund order');
  expect(onSessionExpired).toHaveBeenCalledOnce();
  expect(container.querySelector('.order-card')).toBeNull();
});

it('ignores late history responses after changing accounts', async () => {
  const pending = deferred();
  api.orderHistory.mockReturnValueOnce(pending.promise).mockResolvedValueOnce([]);
  await render(operator);
  await render({ id: 'new-customer', role: 'CUSTOMER' });
  await act(async () => pending.resolve([refunded]));
  expect(container.textContent).toContain('No orders yet.');
  expect(container.textContent).not.toContain('Original Poutine');
  expect(container.querySelector('.history-refunds')).toBeNull();
});

it('ignores a late refund response after changing accounts', async () => {
  api.orderHistory.mockResolvedValue([paid]);
  await render(operator);
  const pending = deferred();
  api.refundOrder.mockReturnValue(pending.promise);
  await click('Refund order');
  api.orderHistory.mockResolvedValue([]);
  await render({ id: 'new-customer', role: 'CUSTOMER' });
  await act(async () => pending.resolve(refundResult));
  expect(container.textContent).toContain('No orders yet.');
  expect(container.textContent).not.toContain('refunded $30.19');
});

const placedOrder = { ...order, status: 'PLACED', billing: null,
  timestamps: { PLACED: order.createdAt } };
async function renderCancellationHistory(user = customer, props = {}) {
  await act(async () => root.render(<CommandLogProvider>
    <OrderHistory user={user} {...props} /><OutputConsole />
  </CommandLogProvider>));
}

it('cancels the customer\'s placed order, refreshes history and records the result', async () => {
  api.orderHistory.mockResolvedValue([placedOrder]);
  api.cancelOrder.mockResolvedValue({ id: placedOrder.orderId, status: 'CANCELLED' });
  await renderCancellationHistory();
  api.orderHistory.mockResolvedValue([{ ...placedOrder, status: 'CANCELLED' }]);
  await click('Cancel order');
  expect(api.cancelOrder).toHaveBeenCalledExactlyOnceWith(placedOrder.orderId);
  expect(container.querySelector('.order-card h4').textContent).toContain('CANCELLED');
  expect(container.querySelector('.history-cancel-button')).toBeNull();
  expect(container.querySelector('[role="log"]').textContent).toContain('Success · Cancel order');
});

it.each(['ACCEPTED', 'PREPARING', 'READY', 'PICKED_UP', 'DELIVERED', 'REFUNDED', 'REJECTED', 'CANCELLED'])
  ('does not offer customer cancellation in state %s', async (status) => {
    api.orderHistory.mockResolvedValue([{ ...placedOrder, status }]);
    await render();
    expect(container.querySelector('.history-cancel-button')).toBeNull();
  });

it.each(['PLATFORM_OPERATOR', 'RESTAURANT_STAFF', 'COURIER'])('does not expose the customer cancellation control to %s', async (role) => {
  api.orderHistory.mockResolvedValue([placedOrder]);
  await render({ ...customer, role });
  expect(container.querySelector('.history-cancel-button')).toBeNull();
});

it('does not offer cancellation for another customer\'s order', async () => {
  api.orderHistory.mockResolvedValue([{ ...placedOrder, customerId: 'other-customer' }]);
  await render();
  expect(container.querySelector('.history-cancel-button')).toBeNull();
});

it('blocks duplicate cancellation requests while the command is pending', async () => {
  const pending = deferred();
  api.orderHistory.mockResolvedValue([placedOrder]);
  api.cancelOrder.mockReturnValue(pending.promise);
  await renderCancellationHistory();
  const button = container.querySelector('.history-cancel-button');
  await act(async () => { button.click(); button.click(); });
  expect(api.cancelOrder).toHaveBeenCalledTimes(1);
  expect(button.disabled).toBe(true);
  expect(button.textContent).toBe('Cancelling...');
  api.orderHistory.mockResolvedValue([{ ...placedOrder, status: 'CANCELLED' }]);
  await act(async () => pending.resolve({ id: placedOrder.orderId, status: 'CANCELLED' }));
});

it('reconciles a cancellation rejected because staff already accepted the order', async () => {
  api.orderHistory.mockResolvedValue([placedOrder]);
  api.cancelOrder.mockRejectedValue(Object.assign(new Error('Order is no longer placed'), { status: 409 }));
  await renderCancellationHistory();
  api.orderHistory.mockResolvedValue([{ ...placedOrder, status: 'ACCEPTED' }]);
  await click('Cancel order');
  expect(container.querySelector('.history-cancel-button')).toBeNull();
  expect(container.querySelector('.order-card h4').textContent).toContain('ACCEPTED');
  expect(container.querySelector('[role="alert"]').textContent).toBe('Order is no longer placed');
  expect(container.querySelector('[role="log"]').textContent).not.toContain('Success · Cancel order');
});

it('preserves cancellation success when the following history refresh fails', async () => {
  api.orderHistory.mockResolvedValue([placedOrder]);
  api.cancelOrder.mockResolvedValue({ id: placedOrder.orderId, status: 'CANCELLED' });
  await renderCancellationHistory();
  api.orderHistory.mockRejectedValue(new Error('History unavailable'));
  await click('Cancel order');
  expect(container.textContent).toContain('Order #aaaaaaaa cancelled.');
  expect(container.querySelector('[role="log"]').textContent).toContain('Success · Cancel order');
  expect(container.querySelector('[role="alert"]').textContent).toBe('History unavailable');
});

it('clears order data when cancellation reports an expired session', async () => {
  const onSessionExpired = vi.fn();
  api.orderHistory.mockResolvedValue([placedOrder]);
  api.cancelOrder.mockRejectedValue(Object.assign(new Error('Session expired'), { status: 401 }));
  await renderCancellationHistory(customer, { onSessionExpired });
  await click('Cancel order');
  expect(onSessionExpired).toHaveBeenCalledOnce();
  expect(container.querySelector('.order-card')).toBeNull();
});

it('ignores a late cancellation response after changing accounts', async () => {
  const pending = deferred();
  api.orderHistory.mockResolvedValue([placedOrder]);
  api.cancelOrder.mockReturnValue(pending.promise);
  await renderCancellationHistory();
  await click('Cancel order');
  api.orderHistory.mockResolvedValue([]);
  await renderCancellationHistory({ id: 'new-customer', role: 'CUSTOMER' });
  await act(async () => pending.resolve({ id: placedOrder.orderId, status: 'CANCELLED' }));
  expect(container.textContent).toContain('No orders yet.');
  expect(container.querySelector('[role="log"]').textContent).not.toContain('Success · Cancel order');
});
