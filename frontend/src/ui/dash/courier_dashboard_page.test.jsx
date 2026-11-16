// @vitest-environment jsdom
import React, { act } from 'react';
import { createRoot } from 'react-dom/client';
import { afterEach, beforeEach, expect, it, vi } from 'vitest';
import { api } from '../../api';
import CourierDashboard from './courier_dashboard_page';
import { CommandLogProvider } from './output_console/command_log_provider';
import OutputConsole from './output_console/output_console';

vi.mock('../../api', () => ({ api: {
  courierDashboard: vi.fn(), setCourierDuty: vi.fn(), acceptDelivery: vi.fn(), declineDelivery: vi.fn(),
  pickUpDelivery: vi.fn(), deliverOrder: vi.fn()
} }));

function snapshot(state = 'IDLE', assignmentStatus = 'OFFERED', orderStatus = 'READY') {
  return {
    state, onDuty: state !== 'OFFLINE',
    assignment: assignmentStatus ? {
      assignment: { status: assignmentStatus },
      order: { id: 'order-1', restaurantId: 'r1', status: orderStatus, orderLines: [] }
    } : null
  };
}
let root, container;
const button = (label) => [...container.querySelectorAll('button')].find((element) => element.textContent === label);
async function render() {
  await act(async () => root.render(<CommandLogProvider><CourierDashboard /><OutputConsole /></CommandLogProvider>));
}
async function click(label) {
  expect(button(label)).toBeDefined();
  await act(async () => button(label).click());
}

beforeEach(() => {
  vi.resetAllMocks();
  vi.useFakeTimers();
  vi.stubGlobal('IS_REACT_ACT_ENVIRONMENT', true);
  api.courierDashboard.mockResolvedValue(snapshot());
  container = document.createElement('div');
  document.body.append(container);
  root = createRoot(container);
});

afterEach(async () => {
  await act(async () => root.unmount());
  container.remove();
  vi.useRealTimers();
  vi.unstubAllGlobals();
});

it.each([
  ['IDLE', 'OFFERED', 'READY', false, false],
  ['ASSIGNED', 'ACCEPTED', 'PREPARING', false, false],
  ['ASSIGNED', 'ACCEPTED', 'READY', true, false],
  ['CARRYING', 'PICKED_UP', 'READY', false, false],
  ['CARRYING', 'PICKED_UP', 'PICKED_UP', false, true],
  ['ASSIGNED', 'PICKED_UP', 'PICKED_UP', false, false],
  ['OFFLINE', 'ACCEPTED', 'READY', false, false]
])('guards handoffs for courier=%s, assignment=%s, order=%s', async (state, assignmentStatus, orderStatus, pickup, deliver) => {
  api.courierDashboard.mockResolvedValue(snapshot(state, assignmentStatus, orderStatus));
  await render();
  expect(button('Confirm pickup').disabled).toBe(!pickup);
  expect(button('Confirm delivery').disabled).toBe(!deliver);
  if (!pickup) await click('Confirm pickup');
  if (!deliver) await click('Confirm delivery');
  expect(api.pickUpDelivery).not.toHaveBeenCalled();
  expect(api.deliverOrder).not.toHaveBeenCalled();
});

it.each(['ASSIGNED', 'CARRYING'])('disables going offline while %s', async (state) => {
  api.courierDashboard.mockResolvedValue(snapshot(state, null));
  await render();
  expect(button('Go off duty').disabled).toBe(true);
  await click('Go off duty');
  expect(api.setCourierDuty).not.toHaveBeenCalled();
});

it.each([
  ['IDLE', 'Go off duty', false, 'OFFLINE'],
  ['OFFLINE', 'Go on duty', true, 'IDLE']
])('allows the valid duty change from %s', async (state, label, target, nextState) => {
  api.courierDashboard.mockResolvedValueOnce(snapshot(state, null)).mockResolvedValue(snapshot(nextState, null));
  await render();
  await click(label);
  expect(api.setCourierDuty).toHaveBeenCalledExactlyOnceWith(target);
  expect(container.querySelector('.courier-state').textContent).toContain(nextState);
});

it('refreshes controls through acceptance, pickup and delivery and logs each successful command', async () => {
  await render();
  api.courierDashboard.mockResolvedValue(snapshot('ASSIGNED', 'ACCEPTED', 'READY'));
  await click('Accept offer');
  expect(api.acceptDelivery).toHaveBeenCalledExactlyOnceWith('order-1');
  expect(button('Confirm pickup').disabled).toBe(false);
  expect(button('Confirm delivery').disabled).toBe(true);

  api.courierDashboard.mockResolvedValue(snapshot('CARRYING', 'PICKED_UP', 'PICKED_UP'));
  await click('Confirm pickup');
  expect(api.pickUpDelivery).toHaveBeenCalledExactlyOnceWith('order-1');
  expect(button('Confirm delivery').disabled).toBe(false);

  api.courierDashboard.mockResolvedValue(snapshot('IDLE', null));
  await click('Confirm delivery');
  expect(api.deliverOrder).toHaveBeenCalledExactlyOnceWith('order-1');
  expect(container.textContent).toContain('No active delivery');
  expect(container.querySelectorAll('.console-entry--success')).toHaveLength(3);
});

it('does not accept an offer whose order is no longer ready', async () => {
  api.courierDashboard.mockResolvedValue(snapshot('IDLE', 'OFFERED', 'CANCELLED'));
  await render();
  expect(button('Accept offer').disabled).toBe(true);
  await click('Accept offer');
  expect(api.acceptDelivery).not.toHaveBeenCalled();
});

it('blocks duplicate handoffs and background polling while a command is pending', async () => {
  let resolve;
  api.courierDashboard.mockResolvedValue(snapshot('ASSIGNED', 'ACCEPTED', 'READY'));
  api.pickUpDelivery.mockReturnValue(new Promise((finish) => { resolve = finish; }));
  await render();
  const pickup = button('Confirm pickup');
  await act(async () => { pickup.click(); pickup.click(); });
  expect(api.pickUpDelivery).toHaveBeenCalledTimes(1);
  await act(async () => vi.advanceTimersByTimeAsync(4000));
  expect(api.courierDashboard).toHaveBeenCalledTimes(1);
  api.courierDashboard.mockResolvedValue(snapshot('CARRYING', 'PICKED_UP', 'PICKED_UP'));
  await act(async () => resolve({}));
  expect(button('Confirm delivery').disabled).toBe(false);
});

it('disables stale controls after a read failure and recovers after refresh', async () => {
  api.courierDashboard.mockResolvedValue(snapshot('ASSIGNED', 'ACCEPTED', 'READY'));
  await render();
  api.courierDashboard.mockRejectedValue(new Error('Delivery status unavailable'));
  await act(async () => vi.advanceTimersByTimeAsync(4000));
  expect(button('Confirm pickup').disabled).toBe(true);
  await click('Confirm pickup');
  expect(api.pickUpDelivery).not.toHaveBeenCalled();
  api.courierDashboard.mockResolvedValue(snapshot('ASSIGNED', 'ACCEPTED', 'READY'));
  await click('Refresh status');
  expect(button('Confirm pickup').disabled).toBe(false);
});

it('reconciles a rejected stale handoff without reporting success', async () => {
  api.courierDashboard.mockResolvedValueOnce(snapshot('ASSIGNED', 'ACCEPTED', 'READY'))
    .mockResolvedValue(snapshot('IDLE', null));
  api.pickUpDelivery.mockRejectedValue(Object.assign(new Error('Assignment changed'), { status: 409 }));
  await render();
  await click('Confirm pickup');
  expect(button('Confirm pickup')).toBeUndefined();
  expect(container.querySelector('[role="alert"]').textContent).toBe('Assignment changed');
  expect(container.querySelector('.console-entry--success')).toBeNull();
});
