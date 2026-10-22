"use client";

import { useTranslations } from "next-intl";
import { useState } from "react";
import type { ProductImage } from "@/lib/types";

export function ImageGallery({ images, name }: { images: ProductImage[]; name: string }) {
  const t = useTranslations("product");
  const tCommon = useTranslations("common");
  const [selected, setSelected] = useState(0);
  const current = images[selected] ?? images[0];

  return (
    <div className="flex flex-col gap-3">
      <div className="card aspect-square overflow-hidden">
        {current ? (
          <img src={current.url} alt={current.altText ?? name} className="h-full w-full object-cover" />
        ) : (
          <div role="img" aria-label={name} className="flex h-full items-center justify-center text-slate-400">
            {tCommon("noImage")}
          </div>
        )}
      </div>
      {images.length > 1 && (
        <div className="flex gap-2 overflow-x-auto">
          {images.map((image, index) => (
            <button
              key={image.id}
              type="button"
              onClick={() => setSelected(index)}
              aria-label={t("imageThumb", { number: index + 1, total: images.length })}
              aria-pressed={index === selected}
              className={`h-20 w-20 shrink-0 overflow-hidden rounded-lg border-2 ${
                index === selected ? "border-brand-500" : "border-transparent opacity-70 hover:opacity-100"
              }`}
            >
              <img src={image.url} alt="" className="h-full w-full object-cover" />
            </button>
          ))}
        </div>
      )}
    </div>
  );
}
