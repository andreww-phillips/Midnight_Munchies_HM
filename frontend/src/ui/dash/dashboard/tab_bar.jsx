import React from 'react';

const TABS = [
  { id: 'map', label: 'Map' },
  { id: 'orders', label: 'Orders' },
  { id: 'delivery', label: 'Delivery' },
  { id: 'history', label: 'Pricing & History' }
];

export default function TabBar({ active, onChange }) {
  return (
    <nav className="tab-bar" role="tablist" aria-label="Dashboard modules">
      {TABS.map((tab) => (
        <button
          key={tab.id}
          type="button"
          role="tab"
          id={`tab-${tab.id}`}
          aria-selected={active === tab.id}
          aria-controls="dashboard-panel"
          className={active === tab.id ? 'tab is-active' : 'tab'}
          onClick={() => onChange(tab.id)}
        >
          {tab.label}
        </button>
      ))}
    </nav>
  );
}
