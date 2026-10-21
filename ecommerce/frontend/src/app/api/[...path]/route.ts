import { NextResponse, type NextRequest } from "next/server";
import { readTokens } from "@/lib/server/auth-cookies";
import { callGateway } from "@/lib/server/gateway";
import {
  copyHeaders,
  errorJson,
  forbiddenCrossSite,
  gatewayUnreachable,
  isSameOrigin,
} from "@/lib/server/route-utils";

/**
 * Relays browser calls to /api/** to the API gateway with the access token from the session cookie
 * (already renewed by the proxy when needed). The browser never sees a token.
 */

/** Token endpoints are handled by dedicated routes (login, register, logout) or not exposed at all. */
const BLOCKED = new Set(["auth/login", "auth/register", "auth/logout", "auth/refresh"]);
const FORWARDED_REQUEST_HEADERS = ["content-type", "idempotency-key"];
const MAX_BODY_BYTES = 1_000_000;

interface Context {
  params: Promise<{ path: string[] }>;
}

async function relay(request: NextRequest, { params }: Context): Promise<NextResponse> {
  const { path } = await params;
  const requestPath = request.nextUrl.pathname;
  if (path.some((segment) => segment === "." || segment === ".." || segment.length === 0)) {
    return errorJson(400, "BAD_REQUEST", "Invalid path", requestPath);
  }
  const target = path.join("/");
  if (BLOCKED.has(target)) {
    return errorJson(404, "RESOURCE_NOT_FOUND", "Not found", requestPath);
  }
  const method = request.method.toUpperCase();
  const hasBody = method !== "GET" && method !== "HEAD";
  if (hasBody && !isSameOrigin(request)) {
    return forbiddenCrossSite(requestPath);
  }

  const headers: Record<string, string> = {};
  for (const name of FORWARDED_REQUEST_HEADERS) {
    const value = request.headers.get(name);
    if (value) {
      headers[name] = value;
    }
  }
  let body: ArrayBuffer | undefined;
  if (hasBody) {
    body = await request.arrayBuffer();
    if (body.byteLength > MAX_BODY_BYTES) {
      return errorJson(413, "BAD_REQUEST", "Request body too large", requestPath);
    }
    if (body.byteLength === 0) {
      body = undefined;
    }
  }

  let response: Response;
  try {
    response = await callGateway(`/api/${path.map(encodeURIComponent).join("/")}${request.nextUrl.search}`, {
      method,
      body,
      headers,
      accessToken: readTokens(request).accessToken,
      forwardedFor: request.headers.get("x-forwarded-for"),
    });
  } catch {
    return gatewayUnreachable(requestPath);
  }
  const noBody = response.status === 204 || response.status === 304 || method === "HEAD";
  return new NextResponse(noBody ? null : response.body, {
    status: response.status,
    headers: copyHeaders(response.headers),
  });
}

export const GET = relay;
export const POST = relay;
export const PUT = relay;
export const PATCH = relay;
export const DELETE = relay;
