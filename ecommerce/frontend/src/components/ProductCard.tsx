import Link from "next/link";
import { useTranslations } from "next-intl";
import { RatingStars } from "@/components/product/RatingStars";
import type { ProductSummary } from "@/lib/types";
import { Price } from "./Price";

export function ProductCard({ product }: { product: ProductSummary }) {
  const t = useTranslations("reviews");
  const tCommon = useTranslations("common");
  return (
    <Link
      href={`/products/${product.slug}`}
      className="card group flex flex-col overflow-hidden transition-shadow hover:shadow-md"
    >
      <div className="aspect-square overflow-hidden bg-slate-100">
        {product.thumbnailUrl ? (
          <img
            src={product.thumbnailUrl}
            alt={product.name}
            loading="lazy"
            className="h-full w-full object-cover transition-transform duration-300 group-hover:scale-105"
          />
        ) : (
          <div role="img" aria-label={product.name} className="flex h-full items-center justify-center text-sm text-slate-400">
            {tCommon("noImage")}
          </div>
        )}
      </div>
      <div className="flex flex-1 flex-col gap-2 p-4">
        {product.brand && <p className="text-xs font-medium tracking-wide text-slate-500 uppercase">{product.brand}</p>}
        <h3 className="line-clamp-2 text-sm font-medium text-slate-900 group-hover:text-brand-600">{product.name}</h3>
        {product.ratingCount > 0 && (
          <p className="flex items-center gap-1.5 text-xs text-slate-500">
            <RatingStars value={product.ratingAverage} size="sm" />
            {t("count", { count: product.ratingCount })}
          </p>
        )}
        <div className="mt-auto">
          <Price price={product.price} compareAtPrice={product.compareAtPrice} currency={product.currency} />
        </div>
      </div>
    </Link>
  );
}
