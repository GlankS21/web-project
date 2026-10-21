import { NextResponse } from "next/server";
import type { ErrorResponse } from "@/lib/types";

/**
 * Defense against cross-site request forgery on top of SameSite cookies: a state-changing request
 * must come from a page of this site. Browsers send Origin on every cross-origin request and on
 * same-origin non-GET fetches; Sec-Fetch-Site is checked when Origin is missing.
 */
export function isSameOrigin(request: Request): boolean {
  const origin = request.headers.get("origin");
  if (origin) {
    const host = request.headers.get("x-forwarded-host") ?? request.headers.get("host");
    try {
      return host !== null && new URL(origin).host === host.split(",")[0].trim();
    } catch {
      return false;
    }
  }
  const fetchSite = request.headers.get("sec-fetch-site");
  return fetchSite === null || fetchSite === "same-origin" || fetchSite === "none";
}

export function errorJson(status: number, code: string, message: string, path: string): NextResponse {
  const body: ErrorResponse = {
    timestamp: new Date().toISOString(),
    status,
    error: code,
    code,
    message,
    path,
  };
  return NextResponse.json(body, { status });
}

export function forbiddenCrossSite(path: string): NextResponse {
  return errorJson(403, "FORBIDDEN", "Cross-site request rejected", path);
}

export function gatewayUnreachable(path: string): NextResponse {
  return errorJson(503, "SERVICE_UNAVAILABLE", "The API gateway cannot be reached", path);
}

/** Response headers of the gateway that are meaningful to the browser. */
export const FORWARDED_RESPONSE_HEADERS = ["content-type", "retry-after", "x-correlation-id", "x-ratelimit-remaining"];

export function copyHeaders(from: Headers): Headers {
  const headers = new Headers();
  for (const name of FORWARDED_RESPONSE_HEADERS) {
    const value = from.get(name);
    if (value) {
      headers.set(name, value);
    }
  }
  return headers;
}
