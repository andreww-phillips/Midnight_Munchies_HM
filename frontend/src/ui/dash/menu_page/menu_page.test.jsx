// @vitest-environment jsdom
import React, { act } from "react";
import { createRoot } from "react-dom/client";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { api } from "../../../api";
import RegistrationPage from "../registration_page/registration_page";
import RestaurantMenu from "./menu_page";

vi.mock("../../../api", () => ({
  api: {
    menu: vi.fn(), addMenuItem: vi.fn(), editMenuItem: vi.fn(), removeMenuItem: vi.fn(),
    changeMenuItemAvailability: vi.fn(), placeOrder: vi.fn(),
    currentUser: vi.fn(), restaurants: vi.fn(), orders: vi.fn()
  }
}));

const restaurant = { id: "restaurant-1", ownerId: "owner-1", name: "Diner", status: "OPEN" };
const staff = { id: "owner-1", username: "staff@example.com", role: "RESTAURANT_STAFF" };
const customer = { id: "customer-1", username: "customer@example.com", role: "CUSTOMER" };
const available = { id: "item-1", restaurantId: restaurant.id, name: "Poutine", price: "12.35", status: "AVAILABLE" };
const soldOut = { id: "item-2", restaurantId: restaurant.id, name: "Fries", price: "5.49", status: "SOLD_OUT" };
let container;
let root;

beforeEach(() => {
  vi.resetAllMocks();
  vi.stubGlobal("IS_REACT_ACT_ENVIRONMENT", true);
  api.menu.mockResolvedValue([available, soldOut]);
  container = document.createElement("div");
  document.body.append(container);
  root = createRoot(container);
});

afterEach(async () => {
  await act(async () => root.unmount());
  container.remove();
  vi.unstubAllGlobals();
});

async function renderMenu(user = staff, selectedRestaurant = restaurant, onOrderPlaced) {
  await act(async () => root.render(<RestaurantMenu key={selectedRestaurant.id} restaurant={selectedRestaurant} user={user} onOrderPlaced={onOrderPlaced} />));
}

function button(label) {
  return [...container.querySelectorAll("button")].find((element) =>
    (element.getAttribute("aria-label") || element.textContent.trim()) === label);
}

function input(label) {
  return [...container.querySelectorAll("label")].find((element) => element.textContent.trim() === label)?.querySelector("input");
}

async function click(element) {
  expect(element).toBeDefined();
  await act(async () => element.click());
}

async function change(element, value) {
  expect(element).toBeDefined();
  await act(async () => {
    Object.getOwnPropertyDescriptor(HTMLInputElement.prototype, "value").set.call(element, value);
    element.dispatchEvent(new Event("input", { bubbles: true }));
  });
}

async function submit() {
  await act(async () => container.querySelector("form").dispatchEvent(new Event("submit", { bubbles: true, cancelable: true })));
}

describe("staff menu management", () => {
  it.each([
    ["customer", customer],
    ["courier", { id: "owner-1", role: "COURIER" }],
    ["operator", { id: "owner-1", role: "PLATFORM_OPERATOR" }],
    ["unassigned staff", { ...staff, id: "other-owner" }],
    ["missing identity", { role: "RESTAURANT_STAFF" }],
    ["anonymous", null]
  ])("hides all management controls from %s", async (_, user) => {
    await renderMenu(user);

    expect(container.querySelector("form")).toBeNull();
    expect(button("Edit Poutine")).toBeUndefined();
    expect(button("Remove Poutine")).toBeUndefined();
    expect(button("Mark sold out: Poutine")).toBeUndefined();
    expect(button("Mark available: Fries")).toBeUndefined();
  });

  it("hides controls for restaurants without an owner", async () => {
    await renderMenu(staff, { ...restaurant, ownerId: null });
    expect(container.querySelector("form")).toBeNull();
    expect(button("Edit Poutine")).toBeUndefined();
  });

  it("shows management controls only to the owning staff account", async () => {
    await renderMenu();
    expect(input("Item name")).toBeDefined();
    expect(input("Price ($)")).toBeDefined();
    expect(container.querySelectorAll('.menu-editor input')).toHaveLength(2);
    expect(button("Edit Poutine")).toBeDefined();
    expect(button("Remove Poutine")).toBeDefined();
    expect(button("Mark sold out: Poutine")).toBeDefined();
    expect(button("Mark available: Fries")).toBeDefined();
    expect(container.querySelector(".order-button")).toBeNull();
  });

  it("adds a zero-priced item and refreshes the menu after success", async () => {
    const added = { ...available, id: "new-item", name: "Water", price: "0" };
    api.menu.mockResolvedValueOnce([available]).mockResolvedValueOnce([available, added]);
    api.addMenuItem.mockResolvedValue(added);
    await renderMenu();
    await change(input("Item name"), "Water");
    await change(input("Price ($)"), "0");
    await submit();

    expect(api.addMenuItem).toHaveBeenCalledWith(restaurant.id, "Water", "0");
    expect(api.menu).toHaveBeenCalledTimes(2);
    expect(button("Edit Water")).toBeDefined();
    expect(input("Item name").value).toBe("");
  });

  it("edits the selected item without rounding the entered decimal price", async () => {
    const precisePrice = "0.12345678901234567890";
    const edited = { ...available, name: "Loaded Poutine", price: precisePrice };
    api.menu.mockResolvedValueOnce([available]).mockResolvedValueOnce([edited]);
    api.editMenuItem.mockResolvedValue(edited);
    await renderMenu();
    await click(button("Edit Poutine"));
    expect(input("Item name").value).toBe("Poutine");
    expect(input("Price ($)").value).toBe("12.35");
    await change(input("Item name"), edited.name);
    await change(input("Price ($)"), precisePrice);
    await submit();

    expect(api.editMenuItem).toHaveBeenCalledWith(restaurant.id, available.id, edited.name, precisePrice);
    expect(api.menu).toHaveBeenCalledTimes(2);
    expect(button("Edit Loaded Poutine")).toBeDefined();
    expect(api.addMenuItem).not.toHaveBeenCalled();
  });

  it("cancels editing without changing the menu", async () => {
    await renderMenu();
    await click(button("Edit Poutine"));
    await change(input("Item name"), "Draft");
    await click(button("Cancel edit"));

    expect(input("Item name").value).toBe("");
    expect(button("Add item")).toBeDefined();
    expect(api.editMenuItem).not.toHaveBeenCalled();
    expect(api.menu).toHaveBeenCalledTimes(1);
  });

  it("removes an item and refreshes the menu", async () => {
    api.menu.mockResolvedValueOnce([available, soldOut]).mockResolvedValueOnce([soldOut]);
    await renderMenu();
    await click(button("Remove Poutine"));

    expect(api.removeMenuItem).toHaveBeenCalledWith(restaurant.id, available.id);
    expect(api.menu).toHaveBeenCalledTimes(2);
    expect(button("Edit Poutine")).toBeUndefined();
    expect(button("Edit Fries")).toBeDefined();
  });

  it.each(["AVAILABLE", "SOLD_OUT"])("changes availability from %s and refreshes", async (status) => {
    const item = { ...available, status };
    const targetStatus = status === "AVAILABLE" ? "SOLD_OUT" : "AVAILABLE";
    api.menu.mockResolvedValueOnce([item]).mockResolvedValueOnce([{ ...item, status: targetStatus }]);
    await renderMenu();
    await click(button((status === "AVAILABLE" ? "Mark sold out: " : "Mark available: ") + item.name));

    expect(api.changeMenuItemAvailability).toHaveBeenCalledWith(restaurant.id, item.id, targetStatus);
    expect(api.menu).toHaveBeenCalledTimes(2);
    expect(button((targetStatus === "AVAILABLE" ? "Mark sold out: " : "Mark available: ") + item.name)).toBeDefined();
  });

  it.each([
    ["   ", "1", "Menu item name is required."],
    ["Fries", "-0.01", "Price must be a nonnegative number."],
    ["Fries", "", "Price must be a nonnegative number."]
  ])("rejects invalid form input (%s, %s)", async (name, price, message) => {
    await renderMenu();
    await change(input("Item name"), name);
    await change(input("Price ($)"), price);
    await submit();

    expect(container.querySelector('[role="alert"]').textContent).toBe(message);
    expect(api.addMenuItem).not.toHaveBeenCalled();
    expect(api.editMenuItem).not.toHaveBeenCalled();
  });

  it.each(["Invalid price", "Authentication is required", "Staff can only manage their own restaurant's menu"])(
    "displays the backend rejection and preserves the draft: %s", async (message) => {
      api.addMenuItem.mockRejectedValue(new Error(message));
      await renderMenu();
      await change(input("Item name"), "Water");
      await change(input("Price ($)"), "0");
      await submit();

      expect(container.querySelector('[role="alert"]').textContent).toBe(message);
      expect(input("Item name").value).toBe("Water");
      expect(container.querySelector("fieldset").disabled).toBe(false);
      expect(api.menu).toHaveBeenCalledTimes(1);
    }
  );

  it("disables management actions while a mutation is pending", async () => {
    let finish;
    api.addMenuItem.mockImplementation(() => new Promise((resolve) => { finish = resolve; }));
    await renderMenu();
    await change(input("Item name"), "Water");
    await change(input("Price ($)"), "0");
    await submit();

    expect(container.querySelector("fieldset").disabled).toBe(true);
    expect(button("Remove Poutine").disabled).toBe(true);
    expect(button("Refresh menu").disabled).toBe(true);
    await act(async () => finish({ id: "new-item" }));
    expect(container.querySelector("fieldset").disabled).toBe(false);
  });

  it("displays initial menu loading errors", async () => {
    api.menu.mockRejectedValue(new Error("Please log in to continue."));
    await renderMenu();
    expect(container.querySelector('[role="alert"]').textContent).toBe("Please log in to continue.");
  });
});

describe("customer ordering", () => {
  it("labels sold-out items, prevents selecting them, and retains normal ordering", async () => {
    const order = { id: "order-1" };
    const onOrderPlaced = vi.fn();
    api.placeOrder.mockResolvedValue(order);
    await renderMenu(customer, restaurant, onOrderPlaced);

    expect(container.querySelector(".sold-out").textContent).toBe("Sold out");
    expect(button("Add one Fries").disabled).toBe(true);
    await click(button("Add one Fries"));
    expect(container.querySelector(".order-button").disabled).toBe(true);
    await click(button("Add one Poutine"));
    await click(button("Add one Poutine"));
    expect(container.querySelector(".order-button").textContent).toBe("Place order · $24.70");
    await click(container.querySelector(".order-button"));

    expect(api.placeOrder).toHaveBeenCalledWith(restaurant.id, [{ menuItemId: available.id, quantity: 2 }]);
    expect(onOrderPlaced).toHaveBeenCalledWith(order);
    expect(container.querySelector(".order-button").disabled).toBe(true);
  });

  it.each(["sold out", "removed"])("removes an item from the cart when refreshed as %s", async (change) => {
    api.menu.mockResolvedValueOnce([available]).mockResolvedValueOnce(change === "removed"
      ? [] : [{ ...available, status: "SOLD_OUT" }]);
    await renderMenu(customer);
    await click(button("Add one Poutine"));
    await click(button("Refresh menu"));

    expect(container.querySelector(".order-button").disabled).toBe(true);
    expect(container.querySelector(".order-button").textContent).toBe("Place order · $0.00");
    expect(api.placeOrder).not.toHaveBeenCalled();
  });

  it("uses the refreshed menu price for the current cart", async () => {
    api.menu.mockResolvedValueOnce([available]).mockResolvedValueOnce([{ ...available, price: "15.00" }]);
    await renderMenu(customer);
    await click(button("Add one Poutine"));
    await click(button("Refresh menu"));
    expect(container.querySelector(".order-button").textContent).toBe("Place order · $15.00");
  });

  it("shows order rejection errors and preserves the cart for correction", async () => {
    api.placeOrder.mockRejectedValue(new Error("Menu item is unavailable for this restaurant"));
    await renderMenu(customer);
    await click(button("Add one Poutine"));
    await click(container.querySelector(".order-button"));
    expect(container.querySelector('[role="alert"]').textContent).toBe("Menu item is unavailable for this restaurant");
    expect(container.querySelector(".order-button").disabled).toBe(false);
  });

  it("ignores a late response from a previously selected restaurant", async () => {
    let finishOldRequest;
    api.menu.mockImplementationOnce(() => new Promise((resolve) => { finishOldRequest = resolve; }))
      .mockResolvedValueOnce([{ ...soldOut, name: "Other Menu" }]);
    await renderMenu(customer);
    await renderMenu(customer, { ...restaurant, id: "restaurant-2", name: "Other Diner" });
    await act(async () => finishOldRequest([available]));

    expect(container.textContent).toContain("Other Menu");
    expect(container.textContent).not.toContain("Poutine");
  });
});

describe("active application integration", () => {
  beforeEach(() => {
    api.restaurants.mockResolvedValue([restaurant, { ...restaurant, id: "restaurant-2", ownerId: "other-owner", name: "Other Diner" }]);
    api.orders.mockResolvedValue([]);
  });

  it("uses the restored session and hides management when staff selects another restaurant", async () => {
    api.currentUser.mockResolvedValue(staff);
    await act(async () => root.render(<RegistrationPage />));
    await click([...container.querySelectorAll(".restaurant-card")].find((element) => element.textContent.includes("Diner") && !element.textContent.includes("Other")).querySelector("button"));
    expect(button("Add item")).toBeDefined();
    await click(button("Back to restaurants"));
    await click([...container.querySelectorAll(".restaurant-card")].find((element) => element.textContent.includes("Other Diner")).querySelector("button"));
    expect(button("Add item")).toBeUndefined();
    expect(button("Edit Poutine")).toBeUndefined();
  });

  it("adds a newly placed customer order to the existing orders display", async () => {
    api.currentUser.mockResolvedValue(customer);
    const placed = { id: "order-12345678", status: "PLACED", orderLines: [{ menuItemId: available.id, menuItemName: "Poutine", quantity: 1, unitPrice: "12.35" }] };
    api.placeOrder.mockResolvedValue(placed);
    api.orders.mockResolvedValueOnce([]).mockResolvedValue([placed]);
    await act(async () => root.render(<RegistrationPage />));
    await click(container.querySelector(".restaurant-card button"));
    await click(button("Add one Poutine"));
    await click(container.querySelector(".order-button"));

    expect(container.querySelector(".order-history").textContent).toContain("PLACED");
    expect(container.querySelector(".order-history").textContent).toContain("Order #order-12");
    expect(container.querySelector(".order-history").textContent).toContain("Poutine");
    expect(button("Add item")).toBeUndefined();
  });
});
