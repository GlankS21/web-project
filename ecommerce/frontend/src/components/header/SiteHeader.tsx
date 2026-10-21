import Link from "next/link";
import type { Category } from "@/lib/types";
import { AccountMenu, CartLink, SearchBox } from "./HeaderActions";

export function SiteHeader({ categories }: { categories: Category[] }) {
  return (
    <header className="sticky top-0 z-20 border-b border-slate-200 bg-white/95 backdrop-blur">
      <div className="container-page flex items-center gap-3 py-3 sm:gap-6">
        <Link href="/" className="shrink-0 text-xl font-bold tracking-tight text-brand-700">
          Tech<span className="text-accent-500">Shop</span>
        </Link>
        <SearchBox />
        <nav className="flex shrink-0 items-center gap-1">
          <CartLink />
          <AccountMenu />
        </nav>
      </div>
      {categories.length > 0 && (
        <nav aria-label="Danh mục" className="border-t border-slate-100">
          <ul className="container-page flex gap-1 overflow-x-auto py-1.5 text-sm">
            <li>
              <Link href="/products" className="block rounded-md px-3 py-1.5 whitespace-nowrap text-slate-600 hover:bg-slate-100">
                Tất cả sản phẩm
              </Link>
            </li>
            {categories.map((category) => (
              <li key={category.id}>
                <Link
                  href={`/products?categoryId=${category.id}`}
                  className="block rounded-md px-3 py-1.5 whitespace-nowrap text-slate-600 hover:bg-slate-100"
                >
                  {category.name}
                </Link>
              </li>
            ))}
          </ul>
        </nav>
      )}
    </header>
  );
}
