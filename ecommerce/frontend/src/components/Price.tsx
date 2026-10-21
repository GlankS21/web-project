import { discountPercent, formatMoney } from "@/lib/format";

interface PriceProps {
  price: number;
  compareAtPrice?: number | null;
  currency: string;
  size?: "md" | "lg";
}

export function Price({ price, compareAtPrice, currency, size = "md" }: PriceProps) {
  const discount = discountPercent(price, compareAtPrice);
  return (
    <div className="flex flex-wrap items-baseline gap-x-2 gap-y-1">
      <span className={`font-semibold text-accent-500 ${size === "lg" ? "text-3xl" : "text-base"}`}>
        {formatMoney(price, currency)}
      </span>
      {discount !== null && (
        <>
          <span className={`text-slate-400 line-through ${size === "lg" ? "text-base" : "text-xs"}`}>
            {formatMoney(compareAtPrice, currency)}
          </span>
          <span className="rounded bg-rose-50 px-1.5 py-0.5 text-xs font-semibold text-accent-500">-{discount}%</span>
        </>
      )}
    </div>
  );
}
