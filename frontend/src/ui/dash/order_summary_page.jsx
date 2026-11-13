import React, { useEffect, useState } from 'react';
import { api } from '../../api';
import { formatPrice } from './menu_page/menu_format';
import { useCommandLog } from './output_console/command_log';
import './order_summary_page.css';

function DeliverySummary({ orderId }) {
  const { record } = useCommandLog();
  const [attempt, setAttempt] = useState(0);
  const [result, setResult] = useState({ loading: true, summary: null, error: '' });

  useEffect(() => {
    let active = true;
    const load = async () => {
      try {
        const summary = await api.orderSummary(orderId);
        if (!active) return;
        if (!summary || summary.orderId !== orderId) {
          throw new Error('The summary does not match this order.');
        }
        setResult({ loading: false, summary, error: '' });
        record({ action: 'Load order summary', outcome: 'success', orderId,
          restaurantId: summary.restaurantId, message: 'Delivery summary loaded.' });
      } catch (error) {
        if (!active) return;
        setResult({ loading: false, summary: null, error: error.message });
        record({ action: 'Load order summary', outcome: 'error', orderId,
          httpStatus: error.status, message: error.message });
      }
    };
    load();
    return () => { active = false; };
  }, [orderId, attempt, record]);

  const { loading, summary, error } = result;
  const pricingPending = summary?.pricingPending || summary?.totalCharge == null;

  return (
    <section className="order-summary" aria-label={`Delivery summary for order ${orderId}`} aria-busy={loading}>
      <div className="order-summary-heading">
        <h5>Delivery summary</h5>
        <button className="link-button" type="button" disabled={loading}
          onClick={() => {
            setResult({ loading: true, summary: null, error: '' });
            setAttempt((value) => value + 1);
          }}>
          {error ? 'Retry summary' : 'Refresh summary'}
        </button>
      </div>
      {loading && <p role="status">Loading delivery summary...</p>}
      {error && <p role="alert">Could not load delivery summary: {error}</p>}
      {summary && <>
        <dl className="order-summary-facts">
          <div><dt>Status</dt><dd>{summary.status === 'REFUNDED' ? 'Refunded' : 'Delivered'}</dd></div>
          <div><dt>Placed</dt><dd><time dateTime={summary.createdAt}>{new Date(summary.createdAt).toLocaleString()}</time></dd></div>
          <div><dt>Delivered</dt><dd><time dateTime={summary.deliveredAt}>{new Date(summary.deliveredAt).toLocaleString()}</time></dd></div>
        </dl>
        <ul className="order-summary-items" aria-label="Delivered items">
          {summary.orderLines.map((line, index) => <li key={`${line.menuItemId}:${index}`}>
            {line.quantity} × {line.menuItemName || 'Menu item'} · {formatPrice(line.unitPrice)} each
          </li>)}
        </ul>
        {!pricingPending && summary.charge && <dl className="order-summary-breakdown" aria-label="Charge breakdown">
          <div><dt>Items subtotal</dt><dd>{formatPrice(summary.charge.itemSubtotal)}</dd></div>
          <div><dt>Delivery fee</dt><dd>{formatPrice(summary.charge.deliveryFee)}</dd></div>
          <div><dt>Service fee</dt><dd>{formatPrice(summary.charge.serviceFee)}</dd></div>
          <div><dt>Small-order surcharge</dt><dd>{formatPrice(summary.charge.smallOrderSurcharge)}</dd></div>
        </dl>}
        <p className="order-summary-total"><strong>Total charge: </strong>
          {pricingPending ? <span>Pricing pending</span> : <span>{formatPrice(summary.totalCharge)}</span>}
        </p>
        {pricingPending && <p className="muted">Your delivery is complete. The final charge is not available yet.</p>}
      </>}
    </section>
  );
}

export default function OrderSummaryPage({ orderId, refreshVersion = 0 }) {
  return <DeliverySummary key={`${orderId}:${refreshVersion}`} orderId={orderId} />;
}
