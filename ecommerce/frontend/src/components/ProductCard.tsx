import Link from "next/link";
import type { ProductSummary } from "@/lib/types";
import { Price } from "./Price";

export function ProductCard({ product }: { product: ProductSummary }) {
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
          <div className="flex h-full items-center justify-center text-sm text-slate-400">Không có ảnh</div>
        )}
      </div>
      <div className="flex flex-1 flex-col gap-2 p-4">
        {product.brand && <p className="text-xs font-medium tracking-wide text-slate-500 uppercase">{product.brand}</p>}
        <h3 className="line-clamp-2 text-sm font-medium text-slate-900 group-hover:text-brand-600">{product.name}</h3>
        <div className="mt-auto">
          <Price price={product.price} compareAtPrice={product.compareAtPrice} currency={product.currency} />
        </div>
      </div>
    </Link>
  );
}
