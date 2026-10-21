import { NextResponse, type NextRequest } from "next/server";
import type { ApiResponse, AuthResponse } from "@/lib/types";
import { isSecureRequest, setAuthCookies } from "./auth-cookies";
import { callGateway } from "./gateway";
import { copyHeaders, forbiddenCrossSite, gatewayUnreachable, isSameOrigin } from "./route-utils";

/**
 * Login and registration: the credentials go to auth-service, the issued tokens are stored in
 * HttpOnly cookies and only the account is returned to the browser.
 */
export async function signIn(request: NextRequest, gatewayPath: "/api/auth/login" | "/api/auth/register") {
  const path = request.nextUrl.pathname;
  if (!isSameOrigin(request)) {
    return forbiddenCrossSite(path);
  }
  let response: Response;
  try {
    response = await callGateway(gatewayPath, {
      method: "POST",
      body: await request.text(),
      headers: { "Content-Type": "application/json" },
      forwardedFor: request.headers.get("x-forwarded-for"),
    });
  } catch {
    return gatewayUnreachable(path);
  }

  if (!response.ok) {
    return new NextResponse(response.body, { status: response.status, headers: copyHeaders(response.headers) });
  }
  const body = (await response.json()) as ApiResponse<AuthResponse>;
  const result = NextResponse.json(
    { success: true, message: body.message, data: body.data.account, timestamp: body.timestamp },
    { status: response.status },
  );
  setAuthCookies(result.cookies, body.data, isSecureRequest(request));
  return result;
}
