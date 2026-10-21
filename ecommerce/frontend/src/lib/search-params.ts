/** Raw searchParams of a page, as given by Next.js. */
export type SearchParams = Record<string, string | string[] | undefined>;

/** First value of a parameter, trimmed; undefined when absent or empty. */
export function param(params: SearchParams, name: string): string | undefined {
  const raw = params[name];
  const value = (Array.isArray(raw) ? raw[0] : raw)?.trim();
  return value ? value : undefined;
}

export function positiveInt(value: string | undefined): number | undefined {
  if (!value || !/^\d{1,9}$/.test(value)) {
    return undefined;
  }
  const number = Number.parseInt(value, 10);
  return number > 0 ? number : undefined;
}

export function nonNegativeNumber(value: string | undefined): number | undefined {
  if (!value || !/^\d{1,13}(\.\d{1,2})?$/.test(value)) {
    return undefined;
  }
  return Number(value);
}

/** The value if it is one of the allowed ones. */
export function oneOf<T extends string>(value: string | undefined, allowed: readonly T[]): T | undefined {
  return allowed.find((candidate) => candidate === value);
}

export function isUuid(value: string): boolean {
  return /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i.test(value);
}
