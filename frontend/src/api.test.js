import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { api } from "./api";

describe("menu management API", () => {
  beforeEach(() => vi.stubGlobal("fetch", vi.fn()));
  afterEach(() => vi.unstubAllGlobals());

  it.each([
    ["add", () => api.addMenuItem("restaurant-1", "Fries", "5.49"), "POST", "/menu", { name: "Fries", price: "5.49" }],
    ["edit", () => api.editMenuItem("restaurant-1", "item-1", "Loaded Fries", "7.99"), "PUT", "/menu/item-1", { name: "Loaded Fries", price: "7.99" }],
    ["remove", () => api.removeMenuItem("restaurant-1", "item-1"), "DELETE", "/menu/item-1", undefined],
    ["sold out", () => api.changeMenuItemAvailability("restaurant-1", "item-1", "SOLD_OUT"), "PATCH", "/menu/item-1/status", { status: "SOLD_OUT" }],
    ["available", () => api.changeMenuItemAvailability("restaurant-1", "item-1", "AVAILABLE"), "PATCH", "/menu/item-1/status", { status: "AVAILABLE" }]
  ])("sends the %s request with session credentials and only operation fields", async (_, operation, method, path, body) => {
    const result = { id: "server-generated-id" };
    fetch.mockResolvedValue(method === "DELETE"
      ? new Response(null, { status: 204 })
      : new Response(JSON.stringify(result), { status: method === "POST" ? 201 : 200 }));

    await expect(operation()).resolves.toEqual(method === "DELETE" ? null : result);

    const [url, options] = fetch.mock.calls[0];
    expect(url).toContain("/api/ord/restaurants/restaurant-1" + path);
    expect(options.credentials).toBe("include");
    expect(options.method).toBe(method);
    expect(options.headers["Content-Type"]).toBe("application/json");
    expect(options.body === undefined ? undefined : JSON.parse(options.body)).toEqual(body);
  });

  it.each([400, 401, 403, 404])("preserves the backend error message for HTTP %s", async (status) => {
    fetch.mockResolvedValue(new Response(JSON.stringify({ message: "Backend menu error" }), { status }));

    await expect(api.addMenuItem("restaurant-1", "Fries", "5.49")).rejects.toThrow("Backend menu error");
  });

  it.each([
    [401, "Please log in to continue."],
    [403, "You do not have permission to perform this action."]
  ])("provides a readable fallback for HTTP %s without a JSON error body", async (status, message) => {
    fetch.mockResolvedValue(new Response("", { status }));

    await expect(api.removeMenuItem("restaurant-1", "item-1")).rejects.toThrow(message);
  });
});
