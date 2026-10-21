const IP_LITERAL = /^[0-9A-Fa-f:.]{2,45}$/;

/**
 * Address of the browser, from X-Forwarded-For.
 *
 * Next.js only sets X-Forwarded-For to the socket address when the request does not already carry
 * one. With {@code hops} trusted reverse proxies in front, each appending the address it saw, the
 * browser's address is the entry added by the outermost one: {@code hops} positions from the end.
 * With no proxy (hops = 0) the last entry is used; a browser talking to the frontend directly could
 * then choose it, which is why production deployments should sit behind a proxy that sets the header.
 */
export function clientIp(forwardedFor: string | null | undefined, hops: number): string | undefined {
  if (!forwardedFor) {
    return undefined;
  }
  const entries = forwardedFor
    .split(",")
    .map((entry) => normalize(entry.trim()))
    .filter((entry) => entry.length > 0);
  if (entries.length === 0) {
    return undefined;
  }
  const index = Math.max(0, entries.length - Math.max(hops, 1));
  const candidate = entries[index];
  return IP_LITERAL.test(candidate) ? candidate : undefined;
}

/** "::ffff:172.18.0.1" (IPv4-mapped IPv6, as reported by Node sockets) → "172.18.0.1". */
function normalize(address: string): string {
  return address.toLowerCase().startsWith("::ffff:") && address.includes(".") ? address.slice(7) : address;
}
