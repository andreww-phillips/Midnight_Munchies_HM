// @vitest-environment jsdom
import React, { act } from 'react';
import { createRoot } from 'react-dom/client';
import { afterEach, beforeEach, expect, it, vi } from 'vitest';
import RegistrationPage from './registration_page';
import { api } from '../../../api';

vi.mock('../../../api', () => ({ api: {
  currentUser: vi.fn(), login: vi.fn(), register: vi.fn(), logout: vi.fn(),
  restaurants: vi.fn(), orders: vi.fn(), menu: vi.fn(), addMenuItem: vi.fn()
} }));

let container;
let root;
beforeEach(() => {
  vi.resetAllMocks();
  vi.stubGlobal('IS_REACT_ACT_ENVIRONMENT', true);
  api.currentUser.mockRejectedValue(Object.assign(new Error('Please log in to continue.'), { status: 401 }));
  api.restaurants.mockResolvedValue([]);
  api.orders.mockResolvedValue([]);
  container = document.createElement('div');
  document.body.append(container);
  root = createRoot(container);
});
afterEach(async () => {
  await act(async () => root.unmount());
  container.remove();
  vi.unstubAllGlobals();
});

const button = (text) => [...container.querySelectorAll('button')].find((element) => element.textContent === text);
async function click(element) { await act(async () => element.click()); }
async function change(name, value) {
  await act(async () => {
    const element = container.querySelector(`input[name="${name}"], select[name="${name}"]`);
    const prototype = element.tagName === 'SELECT' ? HTMLSelectElement.prototype : HTMLInputElement.prototype;
    Object.getOwnPropertyDescriptor(prototype, 'value').set.call(element, value);
    element.dispatchEvent(new Event(element.tagName === 'SELECT' ? 'change' : 'input', { bubbles: true }));
  });
}
async function submit() {
  await act(async () => container.querySelector('form').dispatchEvent(new Event('submit', { bubbles: true, cancelable: true })));
}
async function registerAs(role) {
  await act(async () => root.render(<RegistrationPage />));
  expect(container.querySelector('[role="alert"]')).toBeNull();
  await click(button('Create an account'));
  await change('role', role);
  await change('username', `${role}@example.com`);
  await change('password', 'password');
}

it.each([
  ['customer', 'CUSTOMER'],
  ['courier', 'COURIER'],
  ['restaurant', 'RESTAURANT_STAFF']
])('registers a %s through the backend without a database or uploads', async (role, serverRole) => {
  const account = { id: 'registered-id', username: `${role}@example.com`, role: serverRole };
  api.register.mockResolvedValue(account);
  await registerAs(role);
  if (role === 'customer') {
    await change('first_name', 'Alex');
    await change('last_name', 'Smith');
    await change('address_latitude', '45.5');
    await change('address_longitude', '-73.56');
  }
  if (role === 'courier') await change('work_id', '123');
  if (role === 'restaurant') await change('restaurant_name', 'New Diner');
  await submit();

  expect(api.register).toHaveBeenCalledWith(account.username, 'password', serverRole,
    role === 'restaurant' ? { restaurantName: 'New Diner', restaurantLogo: null, menu: null } : {});
  expect(container.textContent).toContain(`Welcome, ${account.username}`);
  expect(api.restaurants).toHaveBeenCalled();
});

it('allows retrying a failed backend registration', async () => {
  api.register.mockRejectedValueOnce(new Error('Registration unavailable')).mockResolvedValue({
    id: 'registered-id', username: 'customer@example.com', role: 'CUSTOMER'
  });
  await registerAs('customer');
  await change('first_name', 'Alex');
  await change('last_name', 'Smith');
  await submit();
  expect(container.querySelector('[role="alert"]').textContent).toBe('Registration unavailable');
  await submit();
  expect(api.register).toHaveBeenCalledTimes(2);
  expect(container.textContent).toContain('Welcome, customer@example.com');
});

it('keeps a selected logo local instead of uploading it to Supabase', async () => {
  vi.stubGlobal('FileReader', class {
    readAsDataURL() {
      this.result = 'data:image/png;base64,bG9nbw==';
      this.onload();
    }
  });
  api.register.mockResolvedValue({ id: 'registered-id', username: 'restaurant@example.com', role: 'RESTAURANT_STAFF' });
  await registerAs('restaurant');
  await change('restaurant_name', 'New Diner');
  await act(async () => {
    const logo = container.querySelector('input[name="logo"]');
    Object.defineProperty(logo, 'files', { value: [new File(['logo'], 'logo.png', { type: 'image/png' })] });
    logo.dispatchEvent(new Event('change', { bubbles: true }));
  });
  await submit();
  expect(api.register).toHaveBeenCalledWith('restaurant@example.com', 'password', 'RESTAURANT_STAFF', {
    restaurantName: 'New Diner', restaurantLogo: 'data:image/png;base64,bG9nbw==', menu: null
  });
});

it('logs in, passes the full identity to the menu, and logs out through the same API', async () => {
  const user = { id: 'owner-id', username: 'staff@example.com', role: 'RESTAURANT_STAFF' };
  api.login.mockResolvedValue(user);
  api.restaurants.mockResolvedValue([{ id: 'restaurant-id', ownerId: user.id, name: 'Owned Diner' }]);
  api.menu.mockResolvedValue([]);
  api.logout.mockResolvedValue(null);
  await act(async () => root.render(<RegistrationPage />));
  await change('username', user.username);
  await change('password', 'password');
  await submit();
  await click(button('Edit menu'));
  expect(button('Add item')).toBeDefined();
  await click(button('Log out'));
  expect(api.logout).toHaveBeenCalledTimes(1);
  expect(container.querySelector('form')).not.toBeNull();
  expect(container.textContent).not.toContain('Owned Diner');
});

it.each([
  ['customer', 'Please complete all customer profile fields.'],
  ['courier', 'Please complete all courier profile fields.'],
  ['restaurant', 'Please provide the restaurant name.']
])('keeps PR 36 validation for an incomplete %s profile', async (role, message) => {
  await registerAs(role);
  await submit();
  expect(container.querySelector('[role="alert"]').textContent).toBe(message);
  expect(api.register).not.toHaveBeenCalled();
});

it('preserves menu images and descriptions in the integrated restaurant library', async () => {
  api.currentUser.mockResolvedValue({ id: 'customer-id', role: 'CUSTOMER' });
  api.restaurants.mockResolvedValue([{ id: 'restaurant-id', name: 'New_Diner', logo: 'https://example.com/logo.png' }]);
  api.menu.mockResolvedValue([{ id: 'item-id', name: 'Poutine', price: 12.35, status: 'AVAILABLE',
    image: 'https://example.com/poutine.png', description: ['Fries', 'Cheese curds'] }]);
  await act(async () => root.render(<RegistrationPage />));
  expect(container.querySelector('img').src).toBe('https://example.com/logo.png');
  await click(button('Menu'));
  expect(container.querySelector('.menu-item img').src).toBe('https://example.com/poutine.png');
  expect(container.textContent).toContain('Fries, Cheese curds');
  expect(button('Add item')).toBeUndefined();
});
