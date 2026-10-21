import { describe, expect, it } from "vitest";
import { isUuid, nonNegativeNumber, oneOf, param, positiveInt } from "./search-params";

describe("search parameter parsing", () => {
  it("takes the first trimmed value", () => {
    expect(param({ q: ["  a ", "b"] }, "q")).toBe("a");
    expect(param({ q: "   " }, "q")).toBeUndefined();
    expect(param({}, "q")).toBeUndefined();
  });

  it("accepts only plain positive integers", () => {
    expect(positiveInt("12")).toBe(12);
    expect(positiveInt("0")).toBeUndefined();
    expect(positiveInt("-1")).toBeUndefined();
    expect(positiveInt("1e3")).toBeUndefined();
    expect(positiveInt("9999999999")).toBeUndefined();
  });

  it("accepts prices with up to two decimals", () => {
    expect(nonNegativeNumber("0")).toBe(0);
    expect(nonNegativeNumber("199.99")).toBe(199.99);
    expect(nonNegativeNumber("1.234")).toBeUndefined();
    expect(nonNegativeNumber("abc")).toBeUndefined();
  });

  it("restricts values to a known set", () => {
    expect(oneOf("PAID", ["PAID", "SHIPPED"] as const)).toBe("PAID");
    expect(oneOf("paid", ["PAID"] as const)).toBeUndefined();
  });

  it("recognises UUIDs", () => {
    expect(isUuid("0b7e7dee-87ac-4b8d-9d5c-0b1c2a3d4e5f")).toBe(true);
    expect(isUuid("../admin")).toBe(false);
  });
});
