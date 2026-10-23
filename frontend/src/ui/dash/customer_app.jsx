import { useEffect, useMemo, useState } from "react";
import { api } from "../../api";
import "./customer_app.css";

export const formatPrice = (price) => `$${Number(price).toFixed(2)}`;

function ErrorMessage({ message }) {
  return message ? <p className="error-message" role="alert">{message}</p> : null;
}

function AuthScreen({ onAuthenticated }) {
  const [isRegistering, setIsRegistering] = useState(false);
  const [username, setUsername] = useState("");
  const [password, setPassword] = useState("");
  const [role, setRole] = useState("");
  const [firstName, setFirstName] = useState("");
  const [lastName, setLastName] = useState("");
  const [deliveryLongitude, setDeliveryLongitude] = useState("");
  const [deliveryLatitude, setDeliveryLatitude] = useState("");
  const [workId, setWorkId] = useState("");
  const [restaurantName, setRestaurantName] = useState("");
  const [error, setError] = useState("");
  const [submitting, setSubmitting] = useState(false);

  const submit = async (event) => {
    event.preventDefault();
    setError("");
    setSubmitting(true);
    try {
      if (isRegistering && !role) {
        throw new Error("Please select a role.");
      }
      if (isRegistering && role === "CUSTOMER" &&
        (!firstName || !lastName || deliveryLongitude === "" || deliveryLatitude === "")) {
        throw new Error("Please enter your name and delivery coordinates.");
      }
      if (isRegistering && role === "COURIER" && (!firstName || !lastName || !workId)) {
        throw new Error("Please enter your name and work ID.");
      }
      if (isRegistering && role === "RESTAURANT_STAFF" && !restaurantName) {
        throw new Error("Please enter your restaurant name.");
      }
      const user = isRegistering
        ? await api.register(username, password, role)
        : await api.login(username, password);
      onAuthenticated(user);
    } catch (submitError) {
      setError(submitError.message);
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <main className="auth-card">
      <p className="eyebrow">MIDNIGHT MUNCHIES</p>
      <h1>{isRegistering ? "Create your account" : "Welcome back"}</h1>
      <p className="muted">Order your favourite food without the late-night wait.</p>
      <form onSubmit={submit}>
        <label>
          Username
          <input value={username} onChange={(event) => setUsername(event.target.value)} required autoComplete="username" />
        </label>
        <label>
          Password
          <input type="password" value={password} onChange={(event) => setPassword(event.target.value)} required autoComplete={isRegistering ? "new-password" : "current-password"} />
        </label>
        {isRegistering && (
          <>
            <label>
              Role
              <select value={role} onChange={(event) => setRole(event.target.value)} required>
                <option value="">Select a role</option>
                <option value="CUSTOMER">Customer</option>
                <option value="COURIER">Courier</option>
                <option value="RESTAURANT_STAFF">Restaurant Staff</option>
              </select>
            </label>
            {(role === "CUSTOMER" || role === "COURIER") && (
              <>
                <label>
                  First name
                  <input value={firstName} onChange={(event) => setFirstName(event.target.value)} required />
                </label>
                <label>
                  Last name
                  <input value={lastName} onChange={(event) => setLastName(event.target.value)} required />
                </label>
                <label>
                  Delivery longitude
                  <input
                    type="number"
                    step="any"
                    value={deliveryLongitude}
                    onChange={(event) => setDeliveryLongitude(event.target.value)}
                    required
                  />
                </label>
                <label>
                  Delivery latitude
                  <input
                    type="number"
                    step="any"
                    value={deliveryLatitude}
                    onChange={(event) => setDeliveryLatitude(event.target.value)}
                    required
                  />
                </label>
              </>
            )}
            {role === "COURIER" && (
              <label>
                Work ID
                <input type="number" value={workId} onChange={(event) => setWorkId(event.target.value)} required />
              </label>
            )}
            {role === "RESTAURANT_STAFF" && (
              <label>
                Restaurant name
                <input value={restaurantName} onChange={(event) => setRestaurantName(event.target.value)} required />
              </label>
            )}
          </>
        )}
        <ErrorMessage message={error} />
        <button className="primary-button" disabled={submitting}>
          {submitting ? "Please wait..." : isRegistering ? "Create account" : "Log in"}
        </button>
      </form>
      <button className="link-button" type="button" onClick={() => { setIsRegistering(!isRegistering); setError(""); }}>
        {isRegistering ? "Already have an account? Log in" : "New here? Create an account"}
      </button>
    </main>
  );
}

function CustomerDashboard({ user, onLogout }) {
  const [restaurants, setRestaurants] = useState([]);
  const [selectedRestaurant, setSelectedRestaurant] = useState(null);
  const [menu, setMenu] = useState([]);
  const [cart, setCart] = useState({});
  const [orders, setOrders] = useState([]);
  const [error, setError] = useState("");
  const [loading, setLoading] = useState(true);

  const loadRestaurants = async () => {
    setLoading(true);
    try {
      setRestaurants(await api.restaurants());
    } catch (loadError) {
      setError(loadError.message);
    } finally {
      setLoading(false);
    }
  };

  const loadOrders = async () => {
    try {
      setOrders(await api.orders());
    } catch (loadError) {
      setError(loadError.message);
    }
  };

  useEffect(() => {
    loadRestaurants();
    loadOrders();
  }, []);

  const chooseRestaurant = async (restaurant) => {
    setError("");
    setSelectedRestaurant(restaurant);
    setCart({});
    try {
      setMenu(await api.menu(restaurant.id));
    } catch (loadError) {
      setError(loadError.message);
    }
  };

  const updateQuantity = (item, quantity) => {
    setCart((previous) => {
      const next = { ...previous };
      if (quantity < 1) delete next[item.id];
      else next[item.id] = quantity;
      return next;
    });
  };

  const cartLines = useMemo(
    () => menu.filter((item) => cart[item.id]).map((item) => ({ item, quantity: cart[item.id] })),
    [cart, menu]
  );
  const total = cartLines.reduce((sum, line) => sum + Number(line.item.price) * line.quantity, 0);

  const placeOrder = async () => {
    setError("");
    try {
      const order = await api.placeOrder(selectedRestaurant.id, cartLines.map(({ item, quantity }) => ({
        menuItemId: item.id,
        quantity
      })));
      setOrders((previous) => [order, ...previous]);
      setCart({});
    } catch (orderError) {
      setError(orderError.message);
    }
  };

  return (
    <main className="app-shell">
      <header className="topbar">
        <div>
          <p className="eyebrow">MIDNIGHT MUNCHIES</p>
          <h1>What are you craving, {user.username}?</h1>
        </div>
        <button className="secondary-button" type="button" onClick={onLogout}>Log out</button>
      </header>
      <ErrorMessage message={error} />
      <section>
        <div className="section-heading"><h2>Restaurants</h2>{loading && <span className="muted">Loading...</span>}</div>
        <div className="restaurant-grid">
          {restaurants.map((restaurant) => (
            <button className={`restaurant-card ${selectedRestaurant?.id === restaurant.id ? "selected" : ""}`} key={restaurant.id} onClick={() => chooseRestaurant(restaurant)}>
              <span className="restaurant-icon">🍽️</span>
              <strong>{restaurant.name}</strong>
              <span className="muted">{restaurant.status.toLowerCase()}</span>
            </button>
          ))}
        </div>
        {!loading && restaurants.length === 0 && <p className="muted">No restaurants are available right now.</p>}
      </section>
      {selectedRestaurant && (
        <section className="menu-section">
          <div className="section-heading"><h2>{selectedRestaurant.name} menu</h2><span className="muted">{formatPrice(total)} in cart</span></div>
          <div className="menu-list">
            {menu.map((item) => (
              <div className="menu-item" key={item.id}>
                <div><strong>{item.name}</strong><p className="muted">{formatPrice(item.price)}</p></div>
                <div className="quantity-control">
                  <button type="button" onClick={() => updateQuantity(item, (cart[item.id] || 0) - 1)}>-</button>
                  <span>{cart[item.id] || 0}</span>
                  <button type="button" onClick={() => updateQuantity(item, (cart[item.id] || 0) + 1)}>+</button>
                </div>
              </div>
            ))}
          </div>
          <button className="primary-button order-button" disabled={!cartLines.length} onClick={placeOrder}>Place order · {formatPrice(total)}</button>
        </section>
      )}
      <section>
        <div className="section-heading"><h2>Your orders</h2><button className="link-button" type="button" onClick={loadOrders}>Refresh status</button></div>
        {orders.length === 0 ? <p className="muted">Your placed orders will appear here.</p> : (
          <div className="orders-list">
            {orders.map((order) => <article className="order-card" key={order.id}><div><strong>Order #{order.id.slice(0, 8)}</strong><p className="muted">{new Date(order.createdAt).toLocaleString()}</p></div><span className="status-pill">{order.status}</span></article>)}
          </div>
        )}
      </section>
    </main>
  );
}

export default function CustomerApp() {
  const [user, setUser] = useState(null);
  const [checkingSession, setCheckingSession] = useState(true);

  useEffect(() => {
    api.currentUser().then(setUser).catch(() => {}).finally(() => setCheckingSession(false));
  }, []);

  if (checkingSession) return <main className="auth-card"><p className="muted">Loading...</p></main>;
  if (!user) return <AuthScreen onAuthenticated={setUser} />;
  return <CustomerDashboard user={user} onLogout={async () => { await api.logout(); setUser(null); }} />;
}
