// Server-side settings, read from the environment at request time (never bundled for the browser:
// this module is only imported by route handlers, the proxy and server components).

export function gatewayUrl(): string {
  return (process.env.API_GATEWAY_URL ?? "http://localhost:8080").replace(/\/+$/, "");
}

export function trustedProxySecret(): string | undefined {
  const secret = process.env.TRUSTED_PROXY_SECRET?.trim();
  return secret ? secret : undefined;
}

export function trustedProxyHops(): number {
  const hops = Number.parseInt(process.env.TRUSTED_PROXY_HOPS ?? "0", 10);
  return Number.isFinite(hops) && hops > 0 ? hops : 0;
}
