import { NextResponse, type NextRequest } from "next/server";
import { activeSession, isAdmin, type Session } from "@/lib/session";
import {
  ACCESS_COOKIE,
  clearAuthCookies,
  isSecureRequest,
  readTokens,
  REFRESH_COOKIE,
  setAuthCookies,
} from "@/lib/server/auth-cookies";
import { refreshTokens, type RefreshResult } from "@/lib/server/token-refresh";

/** Pages that need a signed-in customer; /admin additionally needs ROLE_ADMIN. */
const PROTECTED_PREFIXES = ["/cart", "/checkout", "/orders", "/account", "/admin"];

/**
 * Runs before every page and API route:
 * 1. renews an expired access token with the refresh token, so pages and API calls always see a
 *    valid one (the renewed cookies are applied to this request and sent back to the browser);
 * 2. sends visitors of protected pages to the login page.
 * Authorization itself is enforced by the gateway and the services.
 */
export async function proxy(request: NextRequest) {
  const { accessToken, refreshToken } = readTokens(request);
  let session: Session | null = activeSession(accessToken);
  let refresh: RefreshResult | null = null;

  if (!session && refreshToken) {
    refresh = await refreshTokens(refreshToken, request.headers.get("x-forwarded-for"));
    if (refresh.kind === "refreshed") {
      session = activeSession(refresh.auth.accessToken);
      request.cookies.set(ACCESS_COOKIE, refresh.auth.accessToken);
      request.cookies.set(REFRESH_COOKIE, refresh.auth.refreshToken);
    } else if (refresh.kind === "rejected") {
      request.cookies.delete(ACCESS_COOKIE);
      request.cookies.delete(REFRESH_COOKIE);
    }
  }

  const { pathname, search } = request.nextUrl;
  let response: NextResponse;
  if (isProtected(pathname) && !session) {
    const login = new URL("/login", request.url);
    login.searchParams.set("next", pathname + search);
    response = NextResponse.redirect(login);
  } else if (pathname.startsWith("/admin") && !isAdmin(session)) {
    response = NextResponse.redirect(new URL("/", request.url));
  } else {
    response = NextResponse.next({ request: { headers: request.headers } });
  }

  if (refresh?.kind === "refreshed") {
    setAuthCookies(response.cookies, refresh.auth, isSecureRequest(request));
  } else if (refresh?.kind === "rejected") {
    clearAuthCookies(response.cookies);
  }
  return response;
}

function isProtected(pathname: string): boolean {
  return PROTECTED_PREFIXES.some((prefix) => pathname === prefix || pathname.startsWith(`${prefix}/`));
}

export const config = {
  matcher: [
    // Everything except build assets, images and the endpoints that issue or end sessions
    "/((?!_next/static|_next/image|favicon.ico|api/auth/login|api/auth/register|api/auth/logout|api/health|.*\\.(?:png|jpg|jpeg|svg|ico|webp)$).*)",
  ],
};
