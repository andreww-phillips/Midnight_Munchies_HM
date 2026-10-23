import { describe, expect, it } from "vitest";
import { formatPrice } from "./menu_page/menu_format";

describe("customer ordering display", () => {
  it("formats menu prices as currency", () => {
    expect(formatPrice("12.5")).toBe("$12.50");
  });
});
