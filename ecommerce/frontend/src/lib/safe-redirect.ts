/**
 * Target of a "?next=" redirect after login. Only paths of this site are accepted, so a crafted link
 * cannot send the user to another site once signed in.
 */
export function safeNext(value: string | null | undefined, fallback = "/"): string {
  if (!value || !value.startsWith("/") || value.startsWith("//") || value.startsWith("/\\")) {
    return fallback;
  }
  if (/[\u0000-\u001f]/.test(value)) {
    return fallback;
  }
  // The login and register pages would loop
  if (/^\/(login|register)(?:[/?#]|$)/.test(value)) {
    return fallback;
  }
  return value;
}
