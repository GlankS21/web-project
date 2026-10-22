"use client";

import { useTranslations } from "next-intl";
import { useRouter } from "next/navigation";
import { useState, type FormEvent } from "react";
import { useToast } from "@/components/toast";
import { Alert, Button, Field } from "@/components/ui";
import { api } from "@/lib/client/api";
import { useErrorMessage } from "@/lib/client/use-error-message";
import type { InventoryItem } from "@/lib/types";

type Result = { form: "adjust" | "set"; ok: true } | { form: "adjust" | "set"; ok: false; message: string } | null;

/**
 * Two ways to change stock: an adjustment (goods received, damaged…) relative to the current
 * quantity, or a stock count that sets the physical quantity.
 */
export function StockForms({ item }: { item: InventoryItem }) {
  const t = useTranslations("stockForms");
  const errorMessage = useErrorMessage();
  const router = useRouter();
  const toast = useToast();
  const [busy, setBusy] = useState<"adjust" | "set" | null>(null);
  const [result, setResult] = useState<Result>(null);

  const run = async (form: "adjust" | "set", target: HTMLFormElement, action: () => Promise<unknown>) => {
    setBusy(form);
    setResult(null);
    try {
      await action();
      target.reset();
      setResult({ form, ok: true });
      toast.success(t("saved"));
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
      setResult({ form: "adjust", ok: false, message: t("nonZero") });
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
    result?.form === form && !result.ok && <Alert tone="danger">{result.message}</Alert>;

  return (
    <div className="grid gap-6 lg:grid-cols-2">
      <form onSubmit={adjust} className="card flex flex-col gap-4 p-5">
        <div>
          <h2 className="font-semibold text-slate-900">{t("adjustTitle")}</h2>
          <p className="text-xs text-slate-500">{t("adjustHint")}</p>
        </div>
        <Field label={t("quantityChange")} htmlFor="quantityChange" required>
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
        <Field label={t("reason")} htmlFor="adjust-reason" required>
          <input
            id="adjust-reason"
            name="reason"
            required
            maxLength={255}
            placeholder={t("adjustReasonPlaceholder")}
            className="input"
          />
        </Field>
        {feedback("adjust")}
        <div>
          <Button type="submit" loading={busy === "adjust"} disabled={busy !== null}>
            {t("adjustSubmit")}
          </Button>
        </div>
      </form>

      <form onSubmit={set} className="card flex flex-col gap-4 p-5">
        <div>
          <h2 className="font-semibold text-slate-900">{t("countTitle")}</h2>
          <p className="text-xs text-slate-500">
            {t("countHint", { reserved: item.quantityReserved })}
          </p>
        </div>
        <div className="grid grid-cols-2 gap-3">
          <Field label={t("onHand")} htmlFor="quantityOnHand" required>
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
          <Field label={t("threshold")} htmlFor="lowStockThreshold">
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
        <Field label={t("reason")} htmlFor="set-reason">
          <input id="set-reason" name="reason" maxLength={255} placeholder={t("countReasonPlaceholder")} className="input" />
        </Field>
        {feedback("set")}
        <div>
          <Button type="submit" variant="secondary" loading={busy === "set"} disabled={busy !== null}>
            {t("countSubmit")}
          </Button>
        </div>
      </form>
    </div>
  );
}
