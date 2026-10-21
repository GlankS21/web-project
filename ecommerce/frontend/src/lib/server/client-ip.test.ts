import { describe, expect, it } from "vitest";
import { clientIp } from "./client-ip";

describe("clientIp", () => {
  it("uses the last entry when no proxy is trusted", () => {
    expect(clientIp("203.0.113.7", 0)).toBe("203.0.113.7");
    expect(clientIp("1.1.1.1, 203.0.113.7", 0)).toBe("203.0.113.7");
  });

  it("skips the entries added by trusted proxies", () => {
    // The browser sends 1.1.1.1; the edge proxy appends the address it saw
    expect(clientIp("1.1.1.1, 203.0.113.7", 1)).toBe("203.0.113.7");
    expect(clientIp("1.1.1.1, 203.0.113.7, 10.0.0.2", 2)).toBe("203.0.113.7");
  });

  it("uses the first entry when there are fewer entries than hops", () => {
    expect(clientIp("203.0.113.7", 3)).toBe("203.0.113.7");
  });

  it("unwraps IPv4-mapped IPv6 addresses and keeps plain IPv6", () => {
    expect(clientIp("::ffff:172.18.0.1", 0)).toBe("172.18.0.1");
    expect(clientIp("2001:db8::1", 0)).toBe("2001:db8::1");
  });

  it("rejects missing or malformed values", () => {
    expect(clientIp(null, 0)).toBeUndefined();
    expect(clientIp("", 0)).toBeUndefined();
    expect(clientIp(" , ", 0)).toBeUndefined();
    expect(clientIp("evil<script>", 0)).toBeUndefined();
    expect(clientIp("203.0.113.7, unknown", 0)).toBeUndefined();
  });
});
