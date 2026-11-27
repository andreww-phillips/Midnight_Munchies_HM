import React, { useEffect, useState } from 'react';
import { api } from '../../api';
import './reviews.css';

const targets = {
  RESTAURANT: { title: 'Restaurant', loadSummary: (id) => api.restaurantReviews(id) },
  COURIER: { title: 'Courier', loadSummary: (id) => api.courierReviews(id) }
};

export function ReviewSummary({ target, targetId, onSessionExpired, compact = false }) {
  const [summary, setSummary] = useState(null);
  const [error, setError] = useState('');
  const [showAll, setShowAll] = useState(false);
  const title = targets[target]?.title;

  useEffect(() => {
    if (!title || !targetId) return undefined;
    let active = true;
    setSummary(null);
    setError('');
    setShowAll(false);
    targets[target].loadSummary(targetId)
      .then((result) => { if (active) setSummary(result); })
      .catch((loadError) => {
        if (!active) return;
        setError(loadError.message);
        if (loadError.status === 401) onSessionExpired?.(loadError.message);
      });
    return () => { active = false; };
  }, [target, targetId, title, onSessionExpired]);

  if (!title || !targetId) return null;
  if (error) return <p className="review-error" role="alert">{error}</p>;
  if (!summary) return <p className="muted" role="status">Loading {title.toLowerCase()} reviews...</p>;

  const visibleReviews = compact ? [] : showAll ? summary.reviews : summary.reviews.slice(0, 1);
  return (
    <section className={`review-summary${compact ? ' review-summary-compact' : ''}`} aria-label={`${title} reviews`}>
      <h3>{title} reviews</h3>
      {summary.reviewCount === 0 ? (
        <p className="muted">No reviews yet.</p>
      ) : (
        <>
          <p className="review-average">
            <span aria-label={`${summary.averageRating} out of 5 stars`}>★ {Number(summary.averageRating).toFixed(1)} / 5</span>
            {' '}· {summary.reviewCount} {summary.reviewCount === 1 ? 'review' : 'reviews'}
          </p>
          {!compact && <>
            <ul className="review-list">
              {visibleReviews.map((review) => (
              <li key={`${review.orderId}-${review.targetId}`}>
                <p aria-label={`${review.rating} out of 5 stars`}>{'★'.repeat(review.rating)}{'☆'.repeat(5 - review.rating)}</p>
                <p>{review.text}</p>
                <time dateTime={review.createdAt}>{new Date(review.createdAt).toLocaleDateString()}</time>
              </li>
              ))}
            </ul>
            {summary.reviewCount > 1 && <button className="link-button" type="button"
              aria-expanded={showAll} onClick={() => setShowAll((value) => !value)}>
              {showAll ? 'Show latest review' : `View all reviews (${summary.reviewCount})`}
            </button>}
          </>}
        </>
      )}
    </section>
  );
}

function ReviewEditor({ orderId, target, review, onSubmitted, onSessionExpired }) {
  const [rating, setRating] = useState(0);
  const [text, setText] = useState('');
  const [pending, setPending] = useState(false);
  const [error, setError] = useState('');
  const title = targets[target].title;

  const submit = async (event) => {
    event.preventDefault();
    if (pending || rating < 1 || rating > 5 || !text.trim()) return;
    setPending(true);
    setError('');
    try {
      await api.submitReview(orderId, target, rating, text.trim());
      setRating(0);
      setText('');
      await onSubmitted();
    } catch (submitError) {
      setError(submitError.message);
      if (submitError.status === 401) onSessionExpired?.(submitError.message);
    } finally {
      setPending(false);
    }
  };

  if (review) {
    return (
      <section className="review-editor" aria-label={`${title} review`}>
        <h4>{title} review submitted</h4>
        <p aria-label={`Your rating: ${review.rating} out of 5 stars`}>
          {'★'.repeat(review.rating)}{'☆'.repeat(5 - review.rating)} {review.rating} / 5
        </p>
        <p>{review.text}</p>
      </section>
    );
  }

  return (
    <form className="review-editor" onSubmit={submit}>
      <h4>Review the {title.toLowerCase()}</h4>
      <fieldset disabled={pending}>
        <legend>Choose a star rating</legend>
        <div className="review-stars" role="group" aria-label={`${title} star rating`}>
          {[1, 2, 3, 4, 5].map((value) => (
            <button key={value} type="button" aria-label={`${value} star${value === 1 ? '' : 's'}`}
              aria-pressed={rating === value} onClick={() => setRating(value)}>
              {value <= rating ? '★' : '☆'}
            </button>
          ))}
        </div>
        <label>
          Your review
          <textarea value={text} onChange={(event) => setText(event.target.value)}
            maxLength={1000} required rows={3} />
        </label>
        <button className="primary-button" type="submit"
          disabled={rating === 0 || !text.trim()}>
          {pending ? 'Submitting...' : 'Submit review'}
        </button>
      </fieldset>
      {error && <p className="review-error" role="alert">{error}</p>}
    </form>
  );
}

export function OrderReviewPanel({ orderId, onSessionExpired }) {
  const [status, setStatus] = useState(null);
  const [error, setError] = useState('');
  const [refresh, setRefresh] = useState(0);

  useEffect(() => {
    let active = true;
    api.orderReviews(orderId)
      .then((result) => { if (active) setStatus(result); })
      .catch((loadError) => {
        if (!active) return;
        setError(loadError.message);
        if (loadError.status === 401) onSessionExpired?.(loadError.message);
      });
    return () => { active = false; };
  }, [orderId, refresh, onSessionExpired]);

  if (error) return <p className="review-error" role="alert">{error}</p>;
  if (!status) return <p className="muted" role="status">Loading review options...</p>;
  if (!status.eligible) return null;

  return (
    <section className="order-review-panel" aria-label="Order reviews">
      <h3>Rate your completed delivery</h3>
      <ReviewEditor orderId={orderId} target="RESTAURANT" review={status.restaurantReview}
        onSubmitted={() => setRefresh((value) => value + 1)} onSessionExpired={onSessionExpired} />
      <ReviewEditor orderId={orderId} target="COURIER" review={status.courierReview}
        onSubmitted={() => setRefresh((value) => value + 1)} onSessionExpired={onSessionExpired} />
      <ReviewSummary target="COURIER" targetId={status.courierId} onSessionExpired={onSessionExpired} />
    </section>
  );
}
