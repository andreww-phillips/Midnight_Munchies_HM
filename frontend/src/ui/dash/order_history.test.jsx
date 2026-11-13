// @vitest-environment jsdom
import React, { act } from 'react';
import { createRoot } from 'react-dom/client';
import { afterEach, beforeEach, expect, it, vi } from 'vitest';
import { api } from '../../api';
import OrderHistory from './order_history';
import RestaurantList from './restaurant_library/restaurant_library';

vi.mock('../../api', () => ({ api: {
  orders: vi.fn(), orderSummary: vi.fn(), orderEvents: vi.fn(), restaurants: vi.fn()
} }));

const customer = { id: 'customer-1', role: 'CUSTOMER' };
const order = {
  id: 'aaaaaaaa-0000-0000-0000-000000000001', customerId: customer.id, restaurantId: 'restaurant-1', status: 'DELIVERED',
  orderLines: [{ menuItemId: 'item-1', menuItemName: 'Original Poutine', quantity: 2, unitPrice: '12.35' }]
};
const summary = {
  ...order, orderId: order.id, createdAt: '2026-11-13T01:00:00Z', deliveredAt: '2026-11-13T01:10:00Z',
  totalCharge: null, pricingPending: true
};
let container, root;

beforeEach(() => {
  vi.resetAllMocks();
  vi.stubGlobal('IS_REACT_ACT_ENVIRONMENT', true);
  api.orders.mockResolvedValue([order]);
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
  expect(api.orderEvents).toHaveBeenCalledWith(order.id);
  expect(container.textContent).toContain('No recorded transitions yet.');
});

it.each(['PLACED', 'ACCEPTED', 'PREPARING', 'READY', 'PICKED_UP', 'REJECTED', 'CANCELLED'])
  ('does not request a summary for an order in state %s', async (status) => {
    api.orders.mockResolvedValue([{ ...order, status }]);
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
  api.orders.mockResolvedValueOnce([{ ...order, status: 'PICKED_UP' }]).mockResolvedValueOnce([order]);
  await render();
  await click('View event history');
  expect(api.orderSummary).not.toHaveBeenCalled();
  await click('Refresh orders');

  expect(api.orderSummary).toHaveBeenCalledWith(order.id);
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
  api.orders.mockResolvedValue([{ ...order, status: 'REFUNDED' }]);
  api.orderSummary.mockResolvedValue({ ...summary, status: 'REFUNDED', totalCharge: '30.70', pricingPending: false });
  await render({ id: 'operator-1', role: 'PLATFORM_OPERATOR' });

  expect(api.orderSummary).toHaveBeenCalledWith(order.id);
  expect(container.querySelector('.order-summary').textContent).toContain('Refunded');
});

it('passes the logged-in customer through the existing restaurant library to the summary', async () => {
  await act(async () => root.render(<RestaurantList user={customer} />));

  expect(api.restaurants).toHaveBeenCalled();
  expect(api.orders).toHaveBeenCalled();
  expect(api.orderSummary).toHaveBeenCalledWith(order.id);
  expect(container.querySelector('.order-summary').textContent).toContain('Pricing pending');
});
