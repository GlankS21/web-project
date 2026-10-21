import { NextResponse, type NextRequest } from "next/server";
import { clearAuthCookies, readTokens } from "@/lib/server/auth-cookies";
import { callGateway } from "@/lib/server/gateway";
import { forbiddenCrossSite, isSameOrigin } from "@/lib/server/route-utils";

/**
 * Revokes the refresh token at auth-service and removes the session cookies. The cookies are
 * removed even when auth-service cannot be reached: the browser is signed out either way.
 */
export async function POST(request: NextRequest) {
  if (!isSameOrigin(request)) {
    return forbiddenCrossSite(request.nextUrl.pathname);
  }
  const { refreshToken } = readTokens(request);
  if (refreshToken) {
    try {
      await callGateway("/api/auth/logout", {
        method: "POST",
        body: { refreshToken },
        forwardedFor: request.headers.get("x-forwarded-for"),
      });
    } catch {
      // Signed out locally; the refresh token expires on its own
    }
  }
  const response = NextResponse.json({ success: true, message: "Logged out", timestamp: new Date().toISOString() });
  clearAuthCookies(response.cookies);
  return response;
}
