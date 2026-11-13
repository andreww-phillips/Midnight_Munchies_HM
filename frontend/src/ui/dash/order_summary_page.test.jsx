// @vitest-environment jsdom
import React, { act } from 'react';
import { createRoot } from 'react-dom/client';
import { afterEach, beforeEach, expect, it, vi } from 'vitest';
import { api } from '../../api';
import { CommandLogContext } from './output_console/command_log';
import OrderSummaryPage from './order_summary_page';

vi.mock('../../api', () => ({ api: { orderSummary: vi.fn() } }));

const summary = {
  orderId: 'aaaaaaaa-0000-0000-0000-000000000001', restaurantId: 'restaurant-1', status: 'DELIVERED',
  createdAt: '2026-11-13T01:00:00Z', deliveredAt: '2026-11-13T01:10:00Z',
  orderLines: [{ menuItemId: 'item-1', menuItemName: 'Original Poutine', quantity: 2, unitPrice: '12.35' }],
  totalCharge: null, pricingPending: true
};
const record = vi.fn();
let container, root;

beforeEach(() => {
  vi.resetAllMocks();
  vi.stubGlobal('IS_REACT_ACT_ENVIRONMENT', true);
  api.orderSummary.mockResolvedValue(summary);
  container = document.createElement('div');
  document.body.append(container);
  root = createRoot(container);
});

afterEach(async () => {
  await act(async () => root.unmount());
  container.remove();
  vi.unstubAllGlobals();
});

async function render(props = {}) {
  await act(async () => root.render(
    <CommandLogContext.Provider value={{ record }}>
      <OrderSummaryPage orderId={summary.orderId} {...props} />
    </CommandLogContext.Provider>
  ));
}

async function clickRefresh() {
  await act(async () => container.querySelector('button').click());
}

function deferred() {
  let resolve;
  const promise = new Promise((res) => { resolve = res; });
  return { promise, resolve };
}

it('loads original purchased items and delivery times with explicit pending pricing', async () => {
  await render();

  expect(api.orderSummary).toHaveBeenCalledWith(summary.orderId);
  expect(container.textContent).toContain('2 × Original Poutine');
  expect(container.textContent).toContain('$12.35 each');
  expect(container.textContent).toContain('Pricing pending');
  expect(container.querySelector('.order-summary-total').textContent).not.toContain('$0.00');
  expect([...container.querySelectorAll('time')].map((time) => time.dateTime))
    .toEqual([summary.createdAt, summary.deliveredAt]);
  expect(record).toHaveBeenCalledWith(expect.objectContaining({
    action: 'Load order summary', outcome: 'success', orderId: summary.orderId, restaurantId: summary.restaurantId
  }));
});

it.each([0, 30.70])('shows the recorded total %s without inventing a fee breakdown', async (amount) => {
  api.orderSummary.mockResolvedValue({ ...summary, totalCharge: amount, pricingPending: false });
  await render();

  expect(container.querySelector('.order-summary-total').textContent).toContain(`$${amount.toFixed(2)}`);
  expect(container.textContent).not.toContain('Pricing pending');
  expect(container.textContent).not.toContain('Delivery fee');
});

it('keeps an absent total pending even if the response flag is false', async () => {
  api.orderSummary.mockResolvedValue({ ...summary, pricingPending: false });
  await render();

  expect(container.querySelector('.order-summary-total').textContent).toContain('Pricing pending');
  expect(container.querySelector('.order-summary-total').textContent).not.toContain('$0.00');
});

it('announces loading and disables refresh while the request is pending', async () => {
  const pending = deferred();
  api.orderSummary.mockReturnValue(pending.promise);
  await render();

  expect(container.querySelector('[role="status"]').textContent).toContain('Loading delivery summary');
  expect(container.querySelector('section').getAttribute('aria-busy')).toBe('true');
  expect(container.querySelector('button').disabled).toBe(true);
  await act(async () => pending.resolve(summary));
  expect(container.querySelector('section').getAttribute('aria-busy')).toBe('false');
  expect(container.querySelector('button').disabled).toBe(false);
});

it('shows and logs an API error and allows a successful retry', async () => {
  api.orderSummary.mockRejectedValueOnce(Object.assign(new Error('Authentication is required'), { status: 401 }));
  await render();

  expect(container.querySelector('[role="alert"]').textContent).toContain('Authentication is required');
  expect(container.querySelector('button').textContent).toBe('Retry summary');
  expect(record).toHaveBeenCalledWith(expect.objectContaining({ outcome: 'error', httpStatus: 401 }));
  await clickRefresh();
  expect(api.orderSummary).toHaveBeenCalledTimes(2);
  expect(container.querySelector('[role="alert"]')).toBeNull();
  expect(container.textContent).toContain('Original Poutine');
  expect(record).toHaveBeenLastCalledWith(expect.objectContaining({ outcome: 'success' }));
});

it('refreshes pending pricing when a recorded charge becomes available', async () => {
  await render();
  api.orderSummary.mockResolvedValue({ ...summary, totalCharge: '30.70', pricingPending: false });
  await clickRefresh();

  expect(container.querySelector('.order-summary-total').textContent).toContain('$30.70');
  expect(container.textContent).not.toContain('Pricing pending');
});

it('clears the old total if a summary refresh fails', async () => {
  api.orderSummary.mockResolvedValueOnce({ ...summary, totalCharge: '30.70', pricingPending: false });
  await render();
  api.orderSummary.mockRejectedValueOnce(new Error('Summary unavailable'));
  await clickRefresh();

  expect(container.querySelector('[role="alert"]').textContent).toContain('Summary unavailable');
  expect(container.textContent).not.toContain('$30.70');
});

it('ignores and does not log a response arriving after unmount', async () => {
  const pending = deferred();
  api.orderSummary.mockReturnValue(pending.promise);
  await render();
  await act(async () => root.render(null));
  await act(async () => pending.resolve(summary));

  expect(container.textContent).toBe('');
  expect(record).not.toHaveBeenCalled();
});

it('ignores a previous orders response after switching to another order', async () => {
  const oldRequest = deferred();
  const next = { ...summary, orderId: 'order-2', orderLines: [{ ...summary.orderLines[0], menuItemName: 'Second meal' }] };
  api.orderSummary.mockReturnValueOnce(oldRequest.promise).mockResolvedValueOnce(next);
  await render();
  await render({ orderId: next.orderId });
  await act(async () => oldRequest.resolve(summary));

  expect(container.textContent).toContain('Second meal');
  expect(container.textContent).not.toContain('Original Poutine');
  expect(record).toHaveBeenCalledTimes(1);
  expect(record).toHaveBeenCalledWith(expect.objectContaining({ orderId: next.orderId }));
});

it('ignores an earlier request when a parent refresh finishes first', async () => {
  const oldRequest = deferred();
  api.orderSummary.mockReturnValueOnce(oldRequest.promise)
    .mockResolvedValueOnce({ ...summary, totalCharge: '30.70', pricingPending: false });
  await render();
  await render({ refreshVersion: 1 });
  await act(async () => oldRequest.resolve(summary));

  expect(container.querySelector('.order-summary-total').textContent).toContain('$30.70');
  expect(record).toHaveBeenCalledTimes(1);
});

it('does not display a response for the wrong order', async () => {
  api.orderSummary.mockResolvedValue({ ...summary, orderId: 'wrong-order' });
  await render();

  expect(container.querySelector('[role="alert"]').textContent).toContain('does not match this order');
  expect(container.textContent).not.toContain('Original Poutine');
  expect(record).toHaveBeenCalledWith(expect.objectContaining({ outcome: 'error' }));
});

it('shows the original charge on a future refunded summary', async () => {
  api.orderSummary.mockResolvedValue({ ...summary, status: 'REFUNDED', totalCharge: '30.70', pricingPending: false });
  await render();

  expect(container.querySelector('dd').textContent).toBe('Refunded');
  expect(container.querySelector('.order-summary-total').textContent).toContain('$30.70');
});
