import { ApiError } from "@/lib/errors";
import type { ApiResponse, ErrorResponse } from "@/lib/types";

/**
 * Browser-side calls. Everything goes to this site's /api/* routes, which relay to the gateway with
 * the session cookie; the response's "data" is returned, errors are thrown as ApiError.
 */
async function request<T>(method: string, path: string, body?: unknown, headers?: Record<string, string>): Promise<T> {
  // Multipart bodies get their Content-Type (with the boundary) from the browser
  const multipart = body instanceof FormData;
  let response: Response;
  try {
    response = await fetch(`/api${path}`, {
      method,
      headers: {
        Accept: "application/json",
        ...(body !== undefined && !multipart ? { "Content-Type": "application/json" } : {}),
        ...headers,
      },
      body: multipart ? body : body !== undefined ? JSON.stringify(body) : undefined,
      credentials: "same-origin",
      cache: "no-store",
    });
  } catch {
    throw new ApiError(0, { code: "NETWORK_ERROR", message: "Network error" });
  }
  if (response.status === 204) {
    return undefined as T;
  }
  const json = (await response.json().catch(() => null)) as ApiResponse<T> | Partial<ErrorResponse> | null;
  if (!response.ok) {
    throw new ApiError(response.status, json as Partial<ErrorResponse> | null);
  }
  return (json as ApiResponse<T> | null)?.data as T;
}

export const api = {
  get: <T>(path: string) => request<T>("GET", path),
  post: <T>(path: string, body?: unknown, headers?: Record<string, string>) => request<T>("POST", path, body, headers),
  put: <T>(path: string, body?: unknown) => request<T>("PUT", path, body),
  patch: <T>(path: string, body?: unknown) => request<T>("PATCH", path, body),
  delete: <T>(path: string) => request<T>("DELETE", path),
  /** multipart/form-data upload, e.g. an image file in the part "file". */
  upload: <T>(path: string, form: FormData) => request<T>("POST", path, form),
};
