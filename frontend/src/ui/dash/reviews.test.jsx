// @vitest-environment jsdom
import React, { act } from 'react';
import { createRoot } from 'react-dom/client';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { api } from '../../api';
import { OrderReviewPanel, ReviewSummary } from './reviews';

vi.mock('../../api', () => ({ api: {
  orderReviews: vi.fn(), submitReview: vi.fn(), courierReviews: vi.fn(), restaurantReviews: vi.fn()
} }));

const orderId = 'order-1';
const courierId = 'courier-1';
const initialStatus = {
  orderId, eligible: true, restaurantId: 'restaurant-1', courierId,
  restaurantReview: null, courierReview: null
};
let container;
let root;

beforeEach(() => {
  vi.resetAllMocks();
  vi.stubGlobal('IS_REACT_ACT_ENVIRONMENT', true);
  api.orderReviews.mockResolvedValue(initialStatus);
  api.courierReviews.mockResolvedValue({
    targetId: courierId, averageRating: 4.5, reviewCount: 2,
    reviews: [{ orderId: 'old-order', targetId: courierId, rating: 5, text: 'Careful delivery.',
      createdAt: '2026-11-20T12:00:00Z' }]
  });
  api.restaurantReviews.mockResolvedValue({
    targetId: 'restaurant-1', averageRating: 4, reviewCount: 2,
    reviews: [
      { orderId: 'new-order', targetId: 'restaurant-1', rating: 5, text: 'Latest review.',
        createdAt: '2026-11-21T12:00:00Z' },
      { orderId: 'old-order', targetId: 'restaurant-1', rating: 3, text: 'Older review.',
        createdAt: '2026-11-20T12:00:00Z' }
    ]
  });
  container = document.createElement('div');
  document.body.append(container);
  root = createRoot(container);
});

afterEach(async () => {
  await act(async () => root.unmount());
  container.remove();
  vi.unstubAllGlobals();
});

async function mount(element) {
  await act(async () => root.render(element));
}

async function fillReview(form, rating, text) {
  const star = [...form.querySelectorAll('button')].find((button) => button.getAttribute('aria-label') === `${rating} stars`);
  await act(async () => star.click());
  const textarea = form.querySelector('textarea');
  await act(async () => {
    Object.getOwnPropertyDescriptor(HTMLTextAreaElement.prototype, 'value').set.call(textarea, text);
    textarea.dispatchEvent(new Event('input', { bubbles: true }));
  });
}

describe('order review forms', () => {
  it('submits separate restaurant and courier ratings after an eligible delivery', async () => {
    const savedRestaurant = { orderId, targetId: 'restaurant-1', rating: 4, text: 'Good food.' };
    api.orderReviews.mockResolvedValueOnce(initialStatus).mockResolvedValueOnce({
      ...initialStatus, restaurantReview: savedRestaurant
    });
    api.submitReview.mockResolvedValue(savedRestaurant);
    await mount(<OrderReviewPanel orderId={orderId} />);

    const restaurantForm = container.querySelectorAll('.review-editor')[0];
    await fillReview(restaurantForm, 4, 'Good food.');
    await act(async () => restaurantForm.dispatchEvent(new Event('submit', { bubbles: true, cancelable: true })));

    expect(api.submitReview).toHaveBeenCalledWith(orderId, 'RESTAURANT', 4, 'Good food.');
    expect(container.textContent).toContain('Restaurant review submitted');
    expect(container.textContent).toContain('Your rating: 4 out of 5 stars');
    expect(container.textContent).toContain('Careful delivery.');
  });

  it('loads restaurant ratings and review text for the menu view', async () => {
    await mount(<ReviewSummary target="RESTAURANT" targetId="restaurant-1" />);

    expect(api.restaurantReviews).toHaveBeenCalledWith('restaurant-1');
    expect(container.textContent).toContain('4.0 / 5');
    expect(container.textContent).toContain('Latest review.');
    expect(container.textContent).not.toContain('Older review.');
    const showAll = [...container.querySelectorAll('button')].find((button) => button.textContent.includes('View all reviews'));
    await act(async () => showAll.click());
    expect(container.textContent).toContain('Older review.');
  });

  it('does not render review forms before the order is delivered', async () => {
    api.orderReviews.mockResolvedValue({ ...initialStatus, eligible: false });
    await mount(<OrderReviewPanel orderId={orderId} />);

    expect(container.querySelector('form')).toBeNull();
    expect(api.submitReview).not.toHaveBeenCalled();
  });
});
