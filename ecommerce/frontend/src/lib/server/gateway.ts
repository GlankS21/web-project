import { clientIp } from "./client-ip";
import { gatewayUrl, trustedProxyHops, trustedProxySecret } from "./config";

export interface GatewayCall {
  method?: string;
  /** JSON-serializable body, or raw bytes already encoded. */
  body?: unknown;
  accessToken?: string;
  /** X-Forwarded-For of the incoming request, to pass the browser's address on to the gateway. */
  forwardedFor?: string | null;
  headers?: Record<string, string>;
  signal?: AbortSignal;
}

const TIMEOUT_MS = 15_000;

/**
 * Calls the API gateway from the server. The caller's access token goes in the Authorization header;
 * with the trusted proxy secret configured, the browser's address is passed on so the gateway
 * rate-limits each browser separately.
 */
export async function callGateway(path: string, call: GatewayCall = {}): Promise<Response> {
  const headers = new Headers({ Accept: "application/json", ...call.headers });
  if (call.accessToken) {
    headers.set("Authorization", `Bearer ${call.accessToken}`);
  }
  const secret = trustedProxySecret();
  const ip = clientIp(call.forwardedFor, trustedProxyHops());
  if (secret && ip) {
    headers.set("X-Proxy-Secret", secret);
    headers.set("X-Client-Ip", ip);
  }

  let body: BodyInit | undefined;
  if (call.body instanceof ArrayBuffer || typeof call.body === "string") {
    body = call.body;
  } else if (call.body !== undefined) {
    body = JSON.stringify(call.body);
    headers.set("Content-Type", "application/json");
  }

  return fetch(`${gatewayUrl()}${path}`, {
    method: call.method ?? "GET",
    headers,
    body,
    cache: "no-store",
    redirect: "manual",
    signal: call.signal ?? AbortSignal.timeout(TIMEOUT_MS),
  });
}
