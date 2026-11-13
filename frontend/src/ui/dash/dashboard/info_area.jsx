import React from 'react';
import ResetControl from '../reset_control.jsx';

export default function InfoArea({ user, restaurant, showLabels, onToggleLabels, onReset, onSessionExpired }) {
  return (
    <section className="info-area" aria-label="Information area">
      <div className="info-block">
        <span className="info-title">Logged in</span>
        <span className="info-user">{user.username}</span>
        <span className="info-role">Role: {user.role}</span>
      </div>
      <div className="info-block info-restaurant">
        <span className="info-title">Selected restaurant</span>
        {restaurant ? (
          <>
            <span className="info-restaurant-name">{restaurant.name}</span>
            <dl className="info-stats">
              <div><dt>Queued</dt><dd>{restaurant.queuedCount}</dd></div>
              <div><dt>In preparation</dt><dd>{restaurant.preparingCount}</dd></div>
              <div><dt>Ready</dt><dd>{restaurant.readyCount}</dd></div>
            </dl>
          </>
        ) : (
          <span className="muted">Click a restaurant on the map.</span>
        )}
      </div>
      <div className="info-block info-controls">
        <label className="map-toggle">
          <input type="checkbox" checked={showLabels} onChange={(event) => onToggleLabels(event.target.checked)} />
          Show labels
        </label>
        {user.role === 'PLATFORM_OPERATOR' && <ResetControl onReset={onReset} onSessionExpired={onSessionExpired} />}
      </div>
    </section>
  );
}
