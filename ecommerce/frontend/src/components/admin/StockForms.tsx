"use client";

import { useRouter } from "next/navigation";
import { useState, type FormEvent } from "react";
import { Alert, Button, Field } from "@/components/ui";
import { api } from "@/lib/client/api";
import { errorMessage } from "@/lib/errors";
import type { InventoryItem } from "@/lib/types";

type Result = { form: "adjust" | "set"; ok: true } | { form: "adjust" | "set"; ok: false; message: string } | null;

/**
 * Two ways to change stock: an adjustment (goods received, damaged…) relative to the current
 * quantity, or a stock count that sets the physical quantity.
 */
export function StockForms({ item }: { item: InventoryItem }) {
  const router = useRouter();
  const [busy, setBusy] = useState<"adjust" | "set" | null>(null);
  const [result, setResult] = useState<Result>(null);

  const run = async (form: "adjust" | "set", target: HTMLFormElement, action: () => Promise<unknown>) => {
    setBusy(form);
    setResult(null);
    try {
      await action();
      target.reset();
      setResult({ form, ok: true });
      router.refresh();
    } catch (caught) {
      setResult({ form, ok: false, message: errorMessage(caught) });
    } finally {
      setBusy(null);
    }
  };

  const adjust = (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    const form = new FormData(event.currentTarget);
    const quantityChange = Number(form.get("quantityChange"));
    if (!Number.isInteger(quantityChange) || quantityChange === 0) {
      setResult({ form: "adjust", ok: false, message: "Số lượng thay đổi phải là số nguyên khác 0." });
      return;
    }
    const body = { quantityChange, reason: String(form.get("reason") ?? "").trim() };
    void run("adjust", event.currentTarget, () => api.post(`/inventory/${item.productId}/adjustments`, body));
  };

  const set = (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    const form = new FormData(event.currentTarget);
    const threshold = String(form.get("lowStockThreshold") ?? "").trim();
    const body = {
      quantityOnHand: Number(form.get("quantityOnHand")),
      lowStockThreshold: threshold ? Number(threshold) : null,
      reason: String(form.get("reason") ?? "").trim() || null,
    };
    void run("set", event.currentTarget, () => api.put(`/inventory/${item.productId}`, body));
  };

  const feedback = (form: "adjust" | "set") =>
    result?.form === form &&
    (result.ok ? <Alert tone="success">Đã cập nhật tồn kho.</Alert> : <Alert tone="danger">{result.message}</Alert>);

  return (
    <div className="grid gap-6 lg:grid-cols-2">
      <form onSubmit={adjust} className="card flex flex-col gap-4 p-5">
        <div>
          <h2 className="font-semibold text-slate-900">Nhập / xuất kho</h2>
          <p className="text-xs text-slate-500">Số dương khi nhập hàng, số âm khi hàng hỏng, thất lạc.</p>
        </div>
        <Field label="Số lượng thay đổi" htmlFor="quantityChange" required>
          <input
            id="quantityChange"
            name="quantityChange"
            type="number"
            required
            step={1}
            min={-item.availableQuantity}
            max={1_000_000}
            className="input"
          />
        </Field>
        <Field label="Lý do" htmlFor="adjust-reason" required>
          <input id="adjust-reason" name="reason" required maxLength={255} placeholder="Nhập theo phiếu PO-..." className="input" />
        </Field>
        {feedback("adjust")}
        <div>
          <Button type="submit" loading={busy === "adjust"} disabled={busy !== null}>
            Ghi nhận
          </Button>
        </div>
      </form>

      <form onSubmit={set} className="card flex flex-col gap-4 p-5">
        <div>
          <h2 className="font-semibold text-slate-900">Kiểm kê</h2>
          <p className="text-xs text-slate-500">
            Đặt tồn thực tế theo số đếm được; không thấp hơn số đang giữ ({item.quantityReserved}).
          </p>
        </div>
        <div className="grid grid-cols-2 gap-3">
          <Field label="Tồn thực tế" htmlFor="quantityOnHand" required>
            <input
              id="quantityOnHand"
              name="quantityOnHand"
              type="number"
              required
              step={1}
              min={item.quantityReserved}
              max={1_000_000}
              defaultValue={item.quantityOnHand}
              className="input"
            />
          </Field>
          <Field label="Ngưỡng cảnh báo" htmlFor="lowStockThreshold">
            <input
              id="lowStockThreshold"
              name="lowStockThreshold"
              type="number"
              step={1}
              min={0}
              max={1_000_000}
              defaultValue={item.lowStockThreshold}
              className="input"
            />
          </Field>
        </div>
        <Field label="Lý do" htmlFor="set-reason">
          <input id="set-reason" name="reason" maxLength={255} placeholder="Kiểm kê tháng..." className="input" />
        </Field>
        {feedback("set")}
        <div>
          <Button type="submit" variant="secondary" loading={busy === "set"} disabled={busy !== null}>
            Lưu kiểm kê
          </Button>
        </div>
      </form>
    </div>
  );
}
