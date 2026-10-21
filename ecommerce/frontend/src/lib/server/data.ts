import { cookies, headers } from "next/headers";
import { notFound, redirect } from "next/navigation";
import { ApiError } from "@/lib/errors";
import { decodeAccessToken, type Session } from "@/lib/session";
import type { ApiResponse, ErrorResponse } from "@/lib/types";
import { ACCESS_COOKIE } from "./auth-cookies";
import { callGateway } from "./gateway";

/**
 * Data access for server components. The access token was renewed by the proxy if needed; it is
 * sent only for calls that need the caller's identity, so public catalogue reads are identical for
 * everyone (an administrator browsing the shop does not see draft products).
 */

export async function getSession(): Promise<Session | null> {
  const token = (await cookies()).get(ACCESS_COOKIE)?.value;
  const session = decodeAccessToken(token);
  return session && session.expiresAt > Date.now() / 1000 ? session : null;
}

/** The session of a page that requires one (the proxy already redirected anonymous visitors). */
export async function requireSession(): Promise<Session> {
  const session = await getSession();
  if (!session) {
    redirect("/login");
  }
  return session;
}

interface GetOptions {
  /** Send the access token of the signed-in user. */
  auth?: boolean;
}

export async function apiGet<T>(path: string, options: GetOptions = {}): Promise<T> {
  const accessToken = options.auth ? (await cookies()).get(ACCESS_COOKIE)?.value : undefined;
  const forwardedFor = (await headers()).get("x-forwarded-for");
  let response: Response;
  try {
    response = await callGateway(path, { accessToken, forwardedFor });
  } catch {
    throw new ApiError(503, { code: "SERVICE_UNAVAILABLE", message: "The API gateway cannot be reached" });
  }
  if (!response.ok) {
    const body = (await response.json().catch(() => null)) as Partial<ErrorResponse> | null;
    throw new ApiError(response.status, body);
  }
  return ((await response.json()) as ApiResponse<T>).data;
}

/** Like {@link apiGet}, but a 404 renders the not-found page. */
export async function apiGetOrNotFound<T>(path: string, options: GetOptions = {}): Promise<T> {
  try {
    return await apiGet<T>(path, options);
  } catch (error) {
    if (error instanceof ApiError && (error.status === 404 || error.status === 400)) {
      notFound();
    }
    throw error;
  }
}

/** Builds a query string from defined, non-empty values. */
export function query(params: Record<string, string | number | boolean | null | undefined>): string {
  const search = new URLSearchParams();
  for (const [key, value] of Object.entries(params)) {
    if (value !== undefined && value !== null && value !== "") {
      search.set(key, String(value));
    }
  }
  const text = search.toString();
  return text ? `?${text}` : "";
}
