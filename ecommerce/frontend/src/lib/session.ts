import type { RoleName } from "./types";

/**
 * Who is signed in, as read from the access token. The token is NOT verified here: this only drives
 * the user interface (menus, redirects). Every API call is authorized by the gateway and the
 * services, which do verify the signature.
 */
export interface Session {
  userId: string;
  email: string;
  roles: RoleName[];
  /** Expiry of the access token, epoch seconds. */
  expiresAt: number;
}

/** Tokens about to expire are treated as expired, so they are refreshed before the request. */
export const EXPIRY_SKEW_SECONDS = 30;

export function decodeAccessToken(token: string | undefined | null): Session | null {
  if (!token) {
    return null;
  }
  const parts = token.split(".");
  if (parts.length !== 3) {
    return null;
  }
  try {
    const payload = JSON.parse(base64UrlDecode(parts[1])) as Record<string, unknown>;
    const { sub, email, roles, exp, token_type: tokenType } = payload;
    if (typeof sub !== "string" || typeof exp !== "number" || tokenType !== "access") {
      return null;
    }
    return {
      userId: sub,
      email: typeof email === "string" ? email : "",
      roles: Array.isArray(roles) ? (roles.filter((role) => typeof role === "string") as RoleName[]) : [],
      expiresAt: exp,
    };
  } catch {
    return null;
  }
}

/** Valid session from a token that does not expire within the skew, else null. */
export function activeSession(token: string | undefined | null, nowSeconds = Date.now() / 1000): Session | null {
  const session = decodeAccessToken(token);
  return session && session.expiresAt - EXPIRY_SKEW_SECONDS > nowSeconds ? session : null;
}

export function isAdmin(session: Session | null | undefined): boolean {
  return session?.roles.includes("ROLE_ADMIN") ?? false;
}

function base64UrlDecode(value: string): string {
  const base64 = value.replace(/-/g, "+").replace(/_/g, "/");
  const padded = base64 + "=".repeat((4 - (base64.length % 4)) % 4);
  const binary = atob(padded);
  const bytes = Uint8Array.from(binary, (char) => char.charCodeAt(0));
  return new TextDecoder().decode(bytes);
}
