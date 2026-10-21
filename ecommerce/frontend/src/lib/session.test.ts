import { describe, expect, it } from "vitest";
import { activeSession, decodeAccessToken, EXPIRY_SKEW_SECONDS, isAdmin } from "./session";

function token(payload: Record<string, unknown>): string {
  const encode = (value: object) => Buffer.from(JSON.stringify(value)).toString("base64url");
  return `${encode({ alg: "HS256", typ: "JWT" })}.${encode(payload)}.signature`;
}

const NOW = 1_800_000_000;
const ACCESS = {
  sub: "user-1",
  email: "nguyễn@example.com",
  roles: ["ROLE_CUSTOMER", "ROLE_ADMIN"],
  exp: NOW + 600,
  token_type: "access",
};

describe("decodeAccessToken", () => {
  it("reads the claims of an access token, including non-ASCII text", () => {
    expect(decodeAccessToken(token(ACCESS))).toEqual({
      userId: "user-1",
      email: "nguyễn@example.com",
      roles: ["ROLE_CUSTOMER", "ROLE_ADMIN"],
      expiresAt: NOW + 600,
    });
  });

  it("rejects refresh tokens and malformed input", () => {
    expect(decodeAccessToken(token({ ...ACCESS, token_type: "refresh" }))).toBeNull();
    expect(decodeAccessToken(token({ ...ACCESS, sub: undefined }))).toBeNull();
    expect(decodeAccessToken("not-a-jwt")).toBeNull();
    expect(decodeAccessToken("a.!!!.c")).toBeNull();
    expect(decodeAccessToken(undefined)).toBeNull();
  });
});

describe("activeSession", () => {
  it("treats tokens expiring within the skew as expired", () => {
    expect(activeSession(token(ACCESS), NOW)).not.toBeNull();
    expect(activeSession(token({ ...ACCESS, exp: NOW + EXPIRY_SKEW_SECONDS }), NOW)).toBeNull();
    expect(activeSession(token({ ...ACCESS, exp: NOW - 1 }), NOW)).toBeNull();
  });
});

describe("isAdmin", () => {
  it("requires the admin role", () => {
    expect(isAdmin(decodeAccessToken(token(ACCESS)))).toBe(true);
    expect(isAdmin(decodeAccessToken(token({ ...ACCESS, roles: ["ROLE_CUSTOMER"] })))).toBe(false);
    expect(isAdmin(null)).toBe(false);
  });
});
