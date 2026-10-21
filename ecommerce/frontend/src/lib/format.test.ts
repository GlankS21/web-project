import { describe, expect, it } from "vitest";
import { discountPercent, formatDateTime, formatMoney } from "./format";

// Intl separates the amount and the symbol with a no-break space
const normalize = (text: string) => text.replace(/\s/g, " ");

describe("formatMoney", () => {
  it("formats VND without decimals", () => {
    expect(normalize(formatMoney(25_140_000, "VND"))).toBe("25.140.000 ₫");
  });

  it("keeps two decimals for other currencies", () => {
    expect(normalize(formatMoney(12.5, "USD"))).toBe("12,50 US$");
  });

  it("shows a dash when there is no amount", () => {
    expect(formatMoney(null)).toBe("—");
  });
});

describe("formatDateTime", () => {
  it("uses the shop's time zone", () => {
    // 17:30 UTC is 00:30 the next day in Vietnam (UTC+7)
    expect(formatDateTime("2026-10-20T17:30:00Z")).toBe("00:30 21/10/2026");
  });
});

describe("discountPercent", () => {
  it("rounds the saving and ignores missing or lower compare-at prices", () => {
    expect(discountPercent(750, 1000)).toBe(25);
    expect(discountPercent(1000, 1000)).toBeNull();
    expect(discountPercent(1000, null)).toBeNull();
  });
});
