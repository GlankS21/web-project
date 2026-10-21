import Link from "next/link";

interface PaginationProps {
  /** Zero-based, as in the API. */
  page: number;
  totalPages: number;
  /** Path of the list page. */
  basePath: string;
  /** Current query parameters, kept on every page link. */
  params: Record<string, string | number | undefined>;
}

/** Page links (1-based in the URL and on screen) around the current page. */
export function Pagination({ page, totalPages, basePath, params }: PaginationProps) {
  if (totalPages <= 1) {
    return null;
  }
  const href = (target: number) => {
    const search = new URLSearchParams();
    for (const [key, value] of Object.entries(params)) {
      if (value !== undefined && value !== "" && key !== "page") {
        search.set(key, String(value));
      }
    }
    if (target > 0) {
      search.set("page", String(target + 1));
    }
    const text = search.toString();
    return text ? `${basePath}?${text}` : basePath;
  };

  const pages: (number | "gap")[] = [];
  for (let index = 0; index < totalPages; index++) {
    if (index === 0 || index === totalPages - 1 || Math.abs(index - page) <= 1) {
      pages.push(index);
    } else if (pages[pages.length - 1] !== "gap") {
      pages.push("gap");
    }
  }

  const itemClass = "min-w-9 rounded-lg px-3 py-1.5 text-center text-sm";
  return (
    <nav aria-label="Phân trang" className="mt-8 flex flex-wrap items-center justify-center gap-1">
      {page > 0 && (
        <Link href={href(page - 1)} className={`${itemClass} text-slate-600 hover:bg-slate-100`}>
          ‹ Trước
        </Link>
      )}
      {pages.map((item, index) =>
        item === "gap" ? (
          <span key={`gap-${index}`} className="px-1 text-slate-400">
            …
          </span>
        ) : (
          <Link
            key={item}
            href={href(item)}
            aria-current={item === page ? "page" : undefined}
            className={`${itemClass} ${item === page ? "bg-brand-600 font-medium text-white" : "text-slate-700 hover:bg-slate-100"}`}
          >
            {item + 1}
          </Link>
        ),
      )}
      {page < totalPages - 1 && (
        <Link href={href(page + 1)} className={`${itemClass} text-slate-600 hover:bg-slate-100`}>
          Sau ›
        </Link>
      )}
    </nav>
  );
}

/** "?page=3" (1-based) → 2; invalid → 0. */
export function pageIndex(value: string | undefined): number {
  const page = Number.parseInt(value ?? "1", 10);
  return Number.isFinite(page) && page > 1 ? Math.min(page - 1, 10_000) : 0;
}
