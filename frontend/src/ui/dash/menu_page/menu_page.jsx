import React, { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { api } from "../../../api";
import { formatPrice } from "./menu_format";
import { dishImageUrl } from "../storage";
import "./menu_page.css";
import { useCommandLog } from "../output_console/command_log";

export default function RestaurantMenu({ restaurant, user, onBack, onOrderPlaced }) {
  const { record } = useCommandLog();
  const commandBusy = useRef(false);
  const [menuItems, setMenuItems] = useState([]);
  const [cart, setCart] = useState({});
  const [loading, setLoading] = useState(true);
  const [pending, setPending] = useState(null);
  const [error, setError] = useState("");
  const [editingItemId, setEditingItemId] = useState(null);
  const [name, setName] = useState("");
  const [price, setPrice] = useState("");
  const canManage = user?.role === "RESTAURANT_STAFF" && Boolean(user.id) && restaurant.ownerId === user.id;
  const canOrder = user?.role === "CUSTOMER";
  const busy = loading || pending !== null;

  const applyMenu = useCallback((items) => {
    setMenuItems(items);
    setCart((previous) => Object.fromEntries(items
      .filter((item) => item.status === "AVAILABLE" && previous[item.id] > 0)
      .map((item) => [item.id, previous[item.id]])));
  }, []);

  useEffect(() => {
    let active = true;
    api.menu(restaurant.id)
      .then((items) => { if (active) applyMenu(items); })
      .catch((loadError) => { if (active) setError(loadError.message); })
      .finally(() => { if (active) setLoading(false); });
    return () => { active = false; };
  }, [restaurant.id, applyMenu]);

  const refreshMenu = async () => {
    setLoading(true);
    try {
      applyMenu(await api.menu(restaurant.id));
    } finally {
      setLoading(false);
    }
  };

  const resetEditor = () => {
    setEditingItemId(null);
    setName("");
    setPrice("");
  };

  const changeMenu = async (action, operation, onSuccess) => {
    if (!canManage || busy || commandBusy.current) return;
    commandBusy.current = true;
    setError("");
    setPending("menu");
    try {
      let result;
      try {
        result = await operation();
      } catch (changeError) {
        setError(changeError.message);
        record({ action, outcome: 'error', restaurantId: restaurant.id, message: changeError.message, httpStatus: changeError.status });
        return;
      }
      record({ action, outcome: 'success', restaurantId: restaurant.id, status: result?.status, message: `${action} completed.` });
      onSuccess?.();
      try {
        await refreshMenu();
      } catch (refreshError) {
        setError(`Menu change succeeded, but the menu could not refresh: ${refreshError.message}`);
        record({ action: 'Refresh menu', outcome: 'error', restaurantId: restaurant.id, message: refreshError.message, httpStatus: refreshError.status });
      }
    } finally {
      commandBusy.current = false;
      setPending(null);
    }
  };

  const saveItem = async (event) => {
    event.preventDefault();
    if (!canManage || busy) return;
    if (!name.trim()) {
      setError("Menu item name is required.");
      record({ action: editingItemId ? 'Edit menu item' : 'Add menu item', outcome: 'error', restaurantId: restaurant.id, message: 'Menu item name is required.' });
      return;
    }
    if (!price.trim() || !Number.isFinite(Number(price)) || Number(price) < 0) {
      setError("Price must be a nonnegative number.");
      record({ action: editingItemId ? 'Edit menu item' : 'Add menu item', outcome: 'error', restaurantId: restaurant.id, message: 'Price must be a nonnegative number.' });
      return;
    }
    // Send the decimal text to BigDecimal without rounding it through a JavaScript number.
    await changeMenu(editingItemId ? 'Edit menu item' : 'Add menu item', () => editingItemId
      ? api.editMenuItem(restaurant.id, editingItemId, name.trim(), price.trim())
      : api.addMenuItem(restaurant.id, name.trim(), price.trim()), resetEditor);
  };

  const updateQuantity = (item, quantity) => {
    if (!canOrder || busy || item.status !== "AVAILABLE") return;
    setCart((previous) => {
      const next = { ...previous };
      if (quantity < 1) delete next[item.id];
      else next[item.id] = quantity;
      return next;
    });
  };

  const cartLines = useMemo(
    () => menuItems.filter((item) => item.status === "AVAILABLE" && cart[item.id])
      .map((item) => ({ item, quantity: cart[item.id] })),
    [cart, menuItems]
  );
  const total = cartLines.reduce((sum, line) => sum + Number(line.item.price) * line.quantity, 0);

  const placeOrder = async () => {
    if (!canOrder || busy || commandBusy.current || !cartLines.length) return;
    commandBusy.current = true;
    setError("");
    setPending("order");
    try {
      let order;
      try {
        order = await api.placeOrder(restaurant.id, cartLines.map(({ item, quantity }) => ({
          menuItemId: item.id,
          quantity
        })));
      } catch (orderError) {
        setError(orderError.message);
        record({ action: 'Place order', outcome: 'error', restaurantId: restaurant.id, message: orderError.message, httpStatus: orderError.status });
        return;
      }
      record({ action: 'Place order', outcome: 'success', restaurantId: restaurant.id, orderId: order.id, status: order.status,
        message: `Order #${order.id} placed successfully.` });
      setCart({});
      onOrderPlaced?.(order);
    } finally {
      commandBusy.current = false;
      setPending(null);
    }
  };

  return (
    <section className="menu-section restaurant-menu" aria-busy={busy}>
      {onBack && <button type="button" onClick={onBack} disabled={busy}>Back to restaurants</button>}
      <div className="section-heading">
        <h2>{restaurant.name ?? restaurant.restaurant_name} menu</h2>
        <div className="menu-toolbar">
          {canOrder && <span className="muted">{formatPrice(total)} in cart</span>}
          <button className="link-button" type="button" disabled={busy} onClick={async () => {
            setError("");
            try {
              await refreshMenu();
              record({ action: 'Refresh menu', outcome: 'success', restaurantId: restaurant.id, message: 'Menu refreshed.' });
            } catch (loadError) {
              setError(loadError.message);
              record({ action: 'Refresh menu', outcome: 'error', restaurantId: restaurant.id, message: loadError.message, httpStatus: loadError.status });
            }
          }}>Refresh menu</button>
        </div>
      </div>
      {error && <p className="error-message" role="alert">{error}</p>}
      {loading && <p className="muted" role="status">Loading menu...</p>}
      {pending === "menu" && <p className="muted" role="status">Updating menu...</p>}
      {user?.role === "RESTAURANT_STAFF" && !canManage && (
        <p className="muted">Only staff assigned to this restaurant can manage its menu.</p>
      )}
      {canManage && (
        <form className="menu-editor" onSubmit={saveItem}>
          <h3>{editingItemId ? "Edit menu item" : "Add menu item"}</h3>
          <fieldset disabled={busy}>
            <div className="menu-fields">
              <label>Item name<input value={name} onChange={(event) => setName(event.target.value)} required /></label>
              <label>Price ($)<input type="number" min="0" step="any" value={price} onChange={(event) => setPrice(event.target.value)} required /></label>
            </div>
            <div className="menu-actions">
              <button className="primary-button" type="submit">{editingItemId ? "Save changes" : "Add item"}</button>
              {editingItemId && <button className="secondary-button" type="button" onClick={resetEditor}>Cancel edit</button>}
            </div>
          </fieldset>
        </form>
      )}
      {!loading && !error && menuItems.length === 0 && <p className="muted">No menu items available.</p>}
      <div className="menu-list">
        {menuItems.map((item) => {
          const available = item.status === "AVAILABLE";
          const quantity = available ? cart[item.id] || 0 : 0;
          return (
            <article className="menu-item" key={item.id}>
              <div>
                {item.image && <img src={dishImageUrl(restaurant.name, item.image)} alt={item.name} />}
                <strong>{item.name}</strong>
                {item.description?.length > 0 && <p>{item.description.join(", ")}</p>}
                <p className="muted">{formatPrice(item.price)}</p>
                <span className={"status-pill" + (available ? "" : " sold-out")}>
                  {available ? "Available" : item.status === "SOLD_OUT" ? "Sold out" : "Unavailable"}
                </span>
              </div>
              {canManage && (
                <div className="menu-actions">
                  <button className="secondary-button" type="button" disabled={busy} aria-label={"Edit " + item.name} onClick={() => {
                    setError("");
                    setEditingItemId(item.id);
                    setName(item.name);
                    setPrice(String(item.price));
                  }}>Edit</button>
                  <button className="secondary-button" type="button" disabled={busy} aria-label={(available ? "Mark sold out: " : "Mark available: ") + item.name} onClick={() => changeMenu('Change menu availability', () => api.changeMenuItemAvailability(
                    restaurant.id, item.id, available ? "SOLD_OUT" : "AVAILABLE"
                  ))}>{available ? "Mark sold out" : "Mark available"}</button>
                  <button className="remove-menu-item" type="button" disabled={busy} aria-label={"Remove " + item.name} onClick={() => changeMenu(
                    'Remove menu item',
                    () => api.removeMenuItem(restaurant.id, item.id),
                    () => { if (editingItemId === item.id) resetEditor(); }
                  )}>Remove</button>
                </div>
              )}
              {canOrder && (
                <div className="quantity-control">
                  <button type="button" disabled={busy || !available || quantity === 0} aria-label={"Remove one " + item.name} onClick={() => updateQuantity(item, quantity - 1)}>-</button>
                  <span aria-label={"Quantity of " + item.name}>{quantity}</span>
                  <button type="button" disabled={busy || !available} aria-label={"Add one " + item.name} onClick={() => updateQuantity(item, quantity + 1)}>+</button>
                </div>
              )}
            </article>
          );
        })}
      </div>
      {canOrder && (
        <button className="primary-button order-button" type="button" disabled={busy || !cartLines.length} onClick={placeOrder}>
          {pending === "order" ? "Placing order..." : "Place order · " + formatPrice(total)}
        </button>
      )}
    </section>
  );
}
