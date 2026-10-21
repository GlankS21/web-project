"use client";

import { useRouter } from "next/navigation";
import { useState } from "react";
import { api } from "@/lib/client/api";
import { errorMessage } from "@/lib/errors";
import { PRODUCT_STATUS } from "@/lib/format";
import type { ProductStatus } from "@/lib/types";

/** Inline status switch of a product row; only ACTIVE products are visible to customers. */
export function ProductStatusSelect({ productId, status }: { productId: number; status: ProductStatus }) {
  const router = useRouter();
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const change = async (next: ProductStatus) => {
    setSaving(true);
    setError(null);
    try {
      await api.patch(`/products/${productId}/status`, { status: next });
      router.refresh();
    } catch (caught) {
      setError(errorMessage(caught));
    } finally {
      setSaving(false);
    }
  };

  return (
    <div>
      <select
        value={status}
        disabled={saving}
        onChange={(event) => change(event.target.value as ProductStatus)}
        className="input w-auto py-1 text-xs"
        aria-label="Trạng thái sản phẩm"
      >
        {(Object.keys(PRODUCT_STATUS) as ProductStatus[]).map((value) => (
          <option key={value} value={value}>
            {PRODUCT_STATUS[value].label}
          </option>
        ))}
      </select>
      {error && <p className="mt-1 text-xs text-rose-600">{error}</p>}
    </div>
  );
}
