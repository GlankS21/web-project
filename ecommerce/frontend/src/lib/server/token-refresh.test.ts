import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { refreshTokens, resetRefreshCache } from "./token-refresh";

const callGateway = vi.hoisted(() => vi.fn());
vi.mock("./gateway", () => ({ callGateway }));

const AUTH = { accessToken: "new-access", refreshToken: "new-refresh", expiresIn: 900, refreshExpiresIn: 604800 };

function respond(status: number, body?: unknown): Response {
  return new Response(body === undefined ? null : JSON.stringify(body), {
    status,
    headers: { "content-type": "application/json" },
  });
}

describe("refreshTokens", () => {
  beforeEach(() => {
    resetRefreshCache();
    callGateway.mockReset();
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  it("calls auth-service once for concurrent callers with the same token", async () => {
    let resolve!: (response: Response) => void;
    callGateway.mockReturnValue(new Promise<Response>((done) => (resolve = done)));

    const first = refreshTokens("rt-1", null);
    const second = refreshTokens("rt-1", null);
    resolve(respond(200, { success: true, data: AUTH }));

    expect(await first).toEqual({ kind: "refreshed", auth: AUTH });
    expect(await second).toBe(await first);
    expect(callGateway).toHaveBeenCalledTimes(1);
    expect(callGateway).toHaveBeenCalledWith(
      "/api/auth/refresh",
      expect.objectContaining({ method: "POST", body: { refreshToken: "rt-1" } }),
    );
  });

  it("hands the same result to late callers within the reuse window, then forgets it", async () => {
    vi.useFakeTimers();
    callGateway.mockResolvedValue(respond(200, { success: true, data: AUTH }));

    await refreshTokens("rt-1", null);
    vi.advanceTimersByTime(10_000);
    await refreshTokens("rt-1", null);
    expect(callGateway).toHaveBeenCalledTimes(1);

    vi.advanceTimersByTime(30_001);
    callGateway.mockResolvedValue(respond(401));
    expect(await refreshTokens("rt-1", null)).toEqual({ kind: "rejected" });
    expect(callGateway).toHaveBeenCalledTimes(2);
  });

  it("reports a revoked token as rejected", async () => {
    callGateway.mockResolvedValue(respond(401));
    expect(await refreshTokens("rt-1", null)).toEqual({ kind: "rejected" });
  });

  it("does not cache an unreachable auth-service, so the next request retries", async () => {
    callGateway.mockRejectedValueOnce(new TypeError("fetch failed")).mockResolvedValueOnce(respond(503));
    expect(await refreshTokens("rt-1", null)).toEqual({ kind: "unavailable" });
    expect(await refreshTokens("rt-1", null)).toEqual({ kind: "unavailable" });
    expect(callGateway).toHaveBeenCalledTimes(2);
  });

  it("keeps different tokens apart", async () => {
    callGateway.mockResolvedValue(respond(200, { success: true, data: AUTH }));
    await refreshTokens("rt-1", null);
    await refreshTokens("rt-2", null);
    expect(callGateway).toHaveBeenCalledTimes(2);
  });
});
