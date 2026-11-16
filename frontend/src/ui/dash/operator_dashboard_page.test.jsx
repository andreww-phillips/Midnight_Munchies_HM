// @vitest-environment jsdom
import React, { act } from 'react';
import { createRoot } from 'react-dom/client';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { api } from '../../api';
import { CommandLogProvider } from './output_console/command_log_provider';
import OutputConsole from './output_console/output_console';
import OperatorDashboard from './operator_dashboard_page';
import { formatWaiting } from './operator_format';

vi.mock('../../api', () => ({ api: { operatorDeliveries: vi.fn(), reassignDelivery: vi.fn() } }));

const accepted = {
  orderId: 'aaaaaaaa-0000-0000-0000-000000000001', courierId: 'cccccccc-0000-0000-0000-000000000001',
  status: 'ACCEPTED', waitingSeconds: 125, reassignable: true
};
const pickedUp = {
  orderId: 'bbbbbbbb-0000-0000-0000-000000000002', courierId: 'dddddddd-0000-0000-0000-000000000002',
  status: 'PICKED_UP', waitingSeconds: 30, reassignable: false
};

let container, root;
const button = (row) => container.querySelectorAll('tbody tr')[row].querySelector('button');

async function render(props = {}) {
  await act(async () => root.render(
    <CommandLogProvider><OperatorDashboard {...props} /><OutputConsole /></CommandLogProvider>
  ));
}

beforeEach(() => {
  vi.resetAllMocks();
  vi.stubGlobal('IS_REACT_ACT_ENVIRONMENT', true);
  api.operatorDeliveries.mockResolvedValue({ active: [accepted, pickedUp], queued: [] });
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

describe('OperatorDashboard', () => {
  it('lists active deliveries and only allows reassigning ones not picked up', async () => {
    await render();

    expect(container.querySelectorAll('tbody tr')).toHaveLength(2);
    expect(container.textContent).toContain('2m 05s');
    expect(button(0).disabled).toBe(false);
    expect(button(1).disabled).toBe(true);
  });

  it('reassigns without choosing a courier and logs the result', async () => {
    api.reassignDelivery.mockResolvedValue({ queued: false, replacement: { courierId: 'eeeeeeee-0000' } });
    await render();

    await act(async () => button(0).click());

    expect(api.reassignDelivery).toHaveBeenCalledWith(accepted.orderId);
    expect(container.textContent).toContain('offered to courier eeeeeeee');
    expect(container.textContent).toContain('Reassign stalled order');
  });

  it('reports when the order falls back to the dispatch queue', async () => {
    api.reassignDelivery.mockResolvedValue({ queued: true, replacement: null });
    await render();
    api.operatorDeliveries.mockResolvedValue({ active: [pickedUp], queued: [accepted.orderId] });

    await act(async () => button(0).click());

    expect(container.textContent).toContain('waiting in the dispatch queue');
    expect(container.querySelector('.operator-queue').textContent).toContain('#aaaaaaaa');
  });

  it('shows and logs a rejected reassignment', async () => {
    api.reassignDelivery.mockRejectedValue(Object.assign(new Error('A picked-up order cannot be reassigned'), { status: 409 }));
    await render();

    await act(async () => button(0).click());

    expect(container.querySelector('[role="alert"]').textContent).toContain('cannot be reassigned');
  });

  it('formats waiting time', () => {
    expect(formatWaiting(0)).toBe('0m 00s');
    expect(formatWaiting(61)).toBe('1m 01s');
  });

  it('logs explicit refresh success without logging successful polls', async () => {
    vi.useFakeTimers();
    await render();
    await act(async () => vi.advanceTimersByTimeAsync(8000));
    expect(container.querySelectorAll('.console-entry')).toHaveLength(0);
    await act(async () => container.querySelector('.section-heading button').click());
    expect(container.querySelectorAll('.console-entry')).toHaveLength(1);
    expect(container.querySelector('[role="log"]').textContent).toContain('Success · Refresh delivery management');
  });

  it('logs a successful reassignment and its failed refresh as separate outcomes', async () => {
    await render();
    api.reassignDelivery.mockResolvedValue({ queued: true });
    api.operatorDeliveries.mockRejectedValue(Object.assign(new Error('Management unavailable'), { status: 503 }));
    await act(async () => button(0).click());
    const entries = [...container.querySelectorAll('.console-entry')];
    expect(entries).toHaveLength(2);
    expect(entries[0].textContent).toContain('Success · Reassign stalled order');
    expect(entries[1].textContent).toContain('Error · Refresh delivery management');
    expect(entries[1].textContent).toContain('HTTP 503');
    expect(container.querySelector('[role="alert"]').textContent).toContain('Reassignment succeeded');
    expect(api.reassignDelivery).toHaveBeenCalledTimes(1);
  });

  it('logs initial and changed read failures while coalescing identical failed polls until recovery', async () => {
    vi.useFakeTimers();
    const failure = Object.assign(new Error('Server down'), { status: 503 });
    api.operatorDeliveries.mockRejectedValue(failure);
    await render();
    await act(async () => vi.advanceTimersByTimeAsync(8000));
    expect(container.querySelectorAll('.console-entry--error')).toHaveLength(1);
    api.operatorDeliveries.mockRejectedValue(Object.assign(new Error('Access denied'), { status: 403 }));
    await act(async () => vi.advanceTimersByTimeAsync(4000));
    expect(container.querySelectorAll('.console-entry--error')).toHaveLength(2);
    api.operatorDeliveries.mockResolvedValue({ active: [], queued: [] });
    await act(async () => vi.advanceTimersByTimeAsync(4000));
    api.operatorDeliveries.mockRejectedValue(failure);
    await act(async () => vi.advanceTimersByTimeAsync(4000));
    expect(container.querySelectorAll('.console-entry--error')).toHaveLength(3);
  });

  it('logs each failed manual refresh even while background polling is failing', async () => {
    api.operatorDeliveries.mockRejectedValue(Object.assign(new Error('Server down'), { status: 503 }));
    await render();
    await act(async () => container.querySelector('.section-heading button').click());
    await act(async () => container.querySelector('.section-heading button').click());
    expect(container.querySelectorAll('.console-entry--error')).toHaveLength(3);
  });

  it('keeps successful reassignment in the log when its refresh reports session expiry', async () => {
    const onSessionExpired = vi.fn();
    await render({ onSessionExpired });
    api.reassignDelivery.mockResolvedValue({ queued: true });
    api.operatorDeliveries.mockRejectedValue(Object.assign(new Error('Authentication required'), { status: 401 }));
    await act(async () => button(0).click());
    expect(onSessionExpired).toHaveBeenCalledWith(expect.stringContaining('Reassignment succeeded'));
    expect(container.querySelector('.console-entry--success').textContent).toContain('Reassign stalled order');
    expect(container.querySelector('.console-entry--error').textContent).toContain('HTTP 401');
  });

  it('does not issue or log duplicate reassignments from rapid clicks', async () => {
    let resolve;
    api.reassignDelivery.mockReturnValue(new Promise((finish) => { resolve = finish; }));
    await render();
    const control = button(0);
    await act(async () => { control.click(); control.click(); });
    expect(api.reassignDelivery).toHaveBeenCalledTimes(1);
    await act(async () => resolve({ queued: true }));
    expect(container.querySelectorAll('.console-entry--success')).toHaveLength(1);
  });
});
