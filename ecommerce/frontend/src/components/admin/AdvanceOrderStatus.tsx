"use client";

import { useRouter } from "next/navigation";
import { useState } from "react";
import { Alert, Button } from "@/components/ui";
import { api } from "@/lib/client/api";
import { errorMessage } from "@/lib/errors";

const LABELS = { SHIPPED: "Đánh dấu đã giao cho vận chuyển", DELIVERED: "Đánh dấu đã giao hàng" } as const;

/** Moves a paid order along the fulfilment steps (PAID → SHIPPED → DELIVERED). */
export function AdvanceOrderStatus({ orderId, target }: { orderId: string; target: keyof typeof LABELS }) {
  const router = useRouter();
  const [note, setNote] = useState("");
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const submit = async () => {
    setSaving(true);
    setError(null);
    try {
      await api.patch(`/orders/admin/${orderId}/status`, { status: target, note: note.trim() || null });
      setNote("");
      router.refresh();
    } catch (caught) {
      setError(errorMessage(caught));
    } finally {
      setSaving(false);
    }
  };

  return (
    <div className="flex w-full max-w-md flex-col gap-2">
      <div className="flex gap-2">
        <input
          value={note}
          onChange={(event) => setNote(event.target.value)}
          maxLength={255}
          placeholder={target === "SHIPPED" ? "Ghi chú, ví dụ mã vận đơn" : "Ghi chú (không bắt buộc)"}
          className="input"
          aria-label="Ghi chú"
        />
        <Button onClick={submit} loading={saving} className="whitespace-nowrap">
          {LABELS[target]}
        </Button>
      </div>
      {error && <Alert tone="danger">{error}</Alert>}
    </div>
  );
}
