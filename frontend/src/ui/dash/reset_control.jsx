import React, { useState } from 'react';

export default function ResetControl({ user, pending, onReset }) {
  const [confirming, setConfirming] = useState(false);
  if (user?.role !== 'PLATFORM_OPERATOR') return null;

  return (
    <section className="simulator-reset" aria-label="Simulator reset">
      {confirming ? (
        <>
          <p>Restore the initial restaurants and menus, clear all orders, deliveries and billing, and remove accounts created since startup?</p>
          <div className="reset-actions">
            <button className="primary-button" type="button" disabled={pending}
              onClick={() => { setConfirming(false); onReset(); }}>Confirm reset</button>
            <button className="secondary-button" type="button" disabled={pending}
              onClick={() => setConfirming(false)}>Cancel</button>
          </div>
        </>
      ) : (
        <button className="secondary-button" type="button" disabled={pending}
          onClick={() => setConfirming(true)}>{pending ? 'Resetting simulator...' : 'Reset simulator'}</button>
      )}
    </section>
  );
}
