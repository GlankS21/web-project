import type { NextRequest, NextResponse } from "next/server";
import type { AuthResponse } from "@/lib/types";

/**
 * Tokens live only in HttpOnly cookies: scripts in the page (including injected ones) cannot read
 * them. SameSite=Lax keeps them off cross-site POST requests, the proxy route additionally checks
 * the Origin of state-changing requests.
 */
export const ACCESS_COOKIE = "shop_at";
export const REFRESH_COOKIE = "shop_rt";

type CookieJar = NextResponse["cookies"];

/** Secure cookies whenever the site is served over HTTPS (directly or behind a TLS proxy). */
export function isSecureRequest(request: Request): boolean {
  const forwardedProto = request.headers.get("x-forwarded-proto");
  if (forwardedProto) {
    return forwardedProto.split(",")[0].trim() === "https";
  }
  return new URL(request.url).protocol === "https:";
}

export function setAuthCookies(cookies: CookieJar, auth: AuthResponse, secure: boolean): void {
  const common = { httpOnly: true, sameSite: "lax" as const, path: "/", secure };
  cookies.set(ACCESS_COOKIE, auth.accessToken, { ...common, maxAge: auth.expiresIn });
  cookies.set(REFRESH_COOKIE, auth.refreshToken, { ...common, maxAge: auth.refreshExpiresIn });
}

export function clearAuthCookies(cookies: CookieJar): void {
  cookies.delete(ACCESS_COOKIE);
  cookies.delete(REFRESH_COOKIE);
}

export function readTokens(request: NextRequest): { accessToken?: string; refreshToken?: string } {
  return {
    accessToken: request.cookies.get(ACCESS_COOKIE)?.value,
    refreshToken: request.cookies.get(REFRESH_COOKIE)?.value,
  };
}
