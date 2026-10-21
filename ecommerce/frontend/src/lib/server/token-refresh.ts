import type { ApiResponse, AuthResponse } from "@/lib/types";
import { callGateway } from "./gateway";

export type RefreshResult =
  | { kind: "refreshed"; auth: AuthResponse }
  /** The refresh token is invalid, expired or revoked: the session is over. */
  | { kind: "rejected" }
  /** auth-service could not be reached; the refresh token may still be valid. */
  | { kind: "unavailable" };

/**
 * auth-service rotates refresh tokens and treats the reuse of a rotated token as theft, revoking
 * every session of the account. A page load fires several requests at once (document, RSC payloads,
 * prefetches, API calls) that all carry the same expired access token, so the refresh must happen
 * once: concurrent callers share one in-flight call, and callers arriving shortly after (still
 * holding the old cookie) get the same result instead of reusing the rotated token.
 *
 * The cache is per process; run a single frontend instance or make the refresh sticky per user.
 */
const REUSE_WINDOW_MS = 30_000;

interface Entry {
  result: Promise<RefreshResult>;
  settledAt?: number;
}

const inFlight = new Map<string, Entry>();

export function refreshTokens(refreshToken: string, forwardedFor: string | null): Promise<RefreshResult> {
  evictExpired(Date.now());
  const existing = inFlight.get(refreshToken);
  if (existing) {
    return existing.result;
  }
  const entry: Entry = {
    result: doRefresh(refreshToken, forwardedFor).then((result) => {
      if (result.kind === "unavailable") {
        // Let the next request try again
        inFlight.delete(refreshToken);
      } else {
        entry.settledAt = Date.now();
      }
      return result;
    }),
  };
  inFlight.set(refreshToken, entry);
  return entry.result;
}

async function doRefresh(refreshToken: string, forwardedFor: string | null): Promise<RefreshResult> {
  try {
    const response = await callGateway("/api/auth/refresh", {
      method: "POST",
      body: { refreshToken },
      forwardedFor,
    });
    if (response.ok) {
      const body = (await response.json()) as ApiResponse<AuthResponse>;
      return { kind: "refreshed", auth: body.data };
    }
    if (response.status === 401 || response.status === 403 || response.status === 400) {
      return { kind: "rejected" };
    }
    return { kind: "unavailable" };
  } catch {
    return { kind: "unavailable" };
  }
}

function evictExpired(now: number): void {
  for (const [token, entry] of inFlight) {
    if (entry.settledAt !== undefined && now - entry.settledAt > REUSE_WINDOW_MS) {
      inFlight.delete(token);
    }
  }
}

/** For tests. */
export function resetRefreshCache(): void {
  inFlight.clear();
}
