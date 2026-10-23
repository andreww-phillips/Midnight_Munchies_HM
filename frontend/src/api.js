const API_BASE_URL = import.meta.env.VITE_API_URL || "http://localhost:8080";

async function request(path, options = {}) {
  const response = await fetch(`${API_BASE_URL}${path}`, {
    credentials: "include",
    headers: {
      "Content-Type": "application/json",
      ...options.headers
    },
    ...options
  });

  if (!response.ok) {
    let message = `Request failed (${response.status})`;
    try {
      const body = await response.json();
      if (body.message) message = body.message;
    } catch {
      // Some successful or error responses intentionally have no JSON body.
    }
    if (response.status === 401) message = message === `Request failed (401)` ? "Please log in to continue." : message;
    if (response.status === 403) message = message === `Request failed (403)` ? "You do not have permission to perform this action." : message;
    const error = new Error(message);
    error.status = response.status;
    throw error;
  }

  if (response.status === 204) return null;
  return response.json();
}

export const api = {
  currentUser: () => request("/api/auth/me"),
  login: (username, password) => request("/api/auth/login", {
    method: "POST",
    body: JSON.stringify({ username, password })
  }),
  register: (username, password, role = "CUSTOMER", details = {}) => request("/api/auth/register", {
    method: "POST",
    body: JSON.stringify({ ...details, username, password, role })
  }),
  logout: () => request("/api/auth/logout", { method: "POST" }),
  restaurants: () => request("/api/ord/restaurants"),
  menu: (restaurantId) => request(`/api/ord/restaurants/${restaurantId}/menu`),
  addMenuItem: (restaurantId, name, price) => request(`/api/ord/restaurants/${restaurantId}/menu`, {
    method: "POST",
    body: JSON.stringify({ name, price })
  }),
  editMenuItem: (restaurantId, menuItemId, name, price) => request(`/api/ord/restaurants/${restaurantId}/menu/${menuItemId}`, {
    method: "PUT",
    body: JSON.stringify({ name, price })
  }),
  removeMenuItem: (restaurantId, menuItemId) => request(`/api/ord/restaurants/${restaurantId}/menu/${menuItemId}`, {
    method: "DELETE"
  }),
  changeMenuItemAvailability: (restaurantId, menuItemId, status) => request(`/api/ord/restaurants/${restaurantId}/menu/${menuItemId}/status`, {
    method: "PATCH",
    body: JSON.stringify({ status })
  }),
  placeOrder: (restaurantId, lines) => request(`/api/ord/restaurants/${restaurantId}/orders`, {
    method: "POST",
    body: JSON.stringify({ lines })
  }),
  orders: () => request("/api/ord/orders"),
  preparationQueue: (restaurantId) => request(`/api/ord/restaurants/${restaurantId}/preparation`),
  acceptOrder: (restaurantId, orderId) => request(`/api/ord/restaurants/${restaurantId}/orders/${orderId}/accept`, {
    method: "POST"
  }),
  rejectOrder: (restaurantId, orderId, reason) => request(`/api/ord/restaurants/${restaurantId}/orders/${orderId}/reject`, {
    method: "POST",
    body: JSON.stringify({ reason })
  }),
  startNextPreparation: (restaurantId) => request(`/api/ord/restaurants/${restaurantId}/preparation/next`, {
    method: "POST"
  }),
  markOrderReady: (restaurantId, orderId) => request(`/api/ord/restaurants/${restaurantId}/orders/${orderId}/ready`, {
    method: "POST"
  }),
  orderEvents: (orderId) => request(`/api/ord/orders/${orderId}/events`)
};
