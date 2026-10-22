import { describe, expect, it } from "vitest";
import {
  CART_ITEM_STATUS_TONE,
  discountPercent,
  formatDate,
  formatDateTime,
  formatMoney,
  labelled,
  NOTIFICATION_STATUS_TONE,
  ORDER_STATUS_TONE,
  PRODUCT_STATUS_TONE,
} from "./format";

// Intl separates the amount and the symbol with a no-break space
const normalize = (text: string) => text.replace(/\s/g, " ");

describe("formatMoney", () => {
  it("formats roubles the Russian way", () => {
    expect(normalize(formatMoney(1234.5, "RUB"))).toBe("1 234,50 ₽");
    expect(normalize(formatMoney(66_690))).toBe("66 690,00 ₽");
  });

  it("omits decimals for currencies without minor units", () => {
    expect(normalize(formatMoney(1_500, "JPY"))).toBe("1 500 ¥");
  });

  it("shows a dash when there is no amount", () => {
    expect(formatMoney(null)).toBe("—");
  });
});

describe("formatDateTime", () => {
  it("uses the shop's time zone", () => {
    // 21:30 UTC is 00:30 the next day in Moscow (UTC+3)
    expect(formatDateTime("2026-10-20T21:30:00Z")).toBe("21.10.2026, 00:30");
  });
});

describe("formatDate", () => {
  it("uses dd.MM.yyyy", () => {
    expect(formatDate("2026-03-05T10:00:00Z")).toBe("05.03.2026");
  });
});

describe("discountPercent", () => {
  it("rounds the saving and ignores missing or lower compare-at prices", () => {
    expect(discountPercent(750, 1000)).toBe(25);
    expect(discountPercent(1000, 1000)).toBeNull();
    expect(discountPercent(1000, null)).toBeNull();
  });
});

describe("labelled", () => {
  it("pairs each status with its tone and translated label", () => {
    const orderStatus = labelled(ORDER_STATUS_TONE, (value) => `label:${value}`);
    expect(orderStatus.CANCELLED).toEqual({ label: "label:CANCELLED", tone: "danger" });
    expect(Object.keys(orderStatus)).toEqual(Object.keys(ORDER_STATUS_TONE));
  });

  it("covers every status of the label maps", () => {
    expect(Object.keys(PRODUCT_STATUS_TONE)).toEqual(["DRAFT", "ACTIVE", "ARCHIVED"]);
    expect(Object.keys(NOTIFICATION_STATUS_TONE)).toEqual(["PENDING", "SENT", "FAILED"]);
    expect(labelled(CART_ITEM_STATUS_TONE, (value) => value).OUT_OF_STOCK.tone).toBe("danger");
  });
});
