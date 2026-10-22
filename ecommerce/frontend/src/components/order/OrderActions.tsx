"use client";

import { useTranslations } from "next-intl";
import { useRouter } from "next/navigation";
import { useEffect, useState } from "react";
import { Alert, Button } from "@/components/ui";
import { api } from "@/lib/client/api";
import { useErrorMessage } from "@/lib/client/use-error-message";

const POLL_INTERVAL_MS = 2000;
const POLL_DURATION_MS = 90_000;

/**
 * While the saga runs (stock reservation, payment, refund), re-renders the page from the server
 * every couple of seconds so the customer sees the outcome without reloading.
 */
export function OrderAutoRefresh({ active }: { active: boolean }) {
  const router = useRouter();
  useEffect(() => {
    if (!active) {
      return;
    }
    const started = Date.now();
    const timer = setInterval(() => {
      if (Date.now() - started > POLL_DURATION_MS) {
        clearInterval(timer);
        return;
      }
      router.refresh();
    }, POLL_INTERVAL_MS);
    return () => clearInterval(timer);
  }, [active, router]);
  return null;
}

interface CancelOrderProps {
  orderId: string;
  adminView?: boolean;
  /** The order is paid: cancelling it refunds the customer. */
  paid?: boolean;
}

/** Cancellation, possible until the order is shipped. */
export function CancelOrder({ orderId, adminView = false, paid = false }: CancelOrderProps) {
  const t = useTranslations("cancelOrder");
  const errorMessage = useErrorMessage();
  const router = useRouter();
  const [open, setOpen] = useState(false);
  const [reason, setReason] = useState("");
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  if (!open) {
    return (
      <Button variant="secondary" onClick={() => setOpen(true)}>
        {t("open")}
      </Button>
    );
  }

  const cancel = async () => {
    setSubmitting(true);
    setError(null);
    try {
      await api.post(`/orders/${orderId}/cancel`, { reason: reason.trim() || null });
      setOpen(false);
      router.refresh();
    } catch (caught) {
      setError(errorMessage(caught));
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div className="card flex w-full max-w-md flex-col gap-3 p-4">
      <label htmlFor="cancel-reason" className="label">
        {adminView ? t("reasonAdmin") : t("reasonCustomer")}
      </label>
      <textarea id="cancel-reason" rows={2} maxLength={255} value={reason} onChange={(event) => setReason(event.target.value)} className="input" />
      {paid && <Alert tone="warning">{t("refundWarning")}</Alert>}
      {error && <Alert tone="danger">{error}</Alert>}
      <div className="flex flex-wrap gap-2">
        <Button variant="danger" onClick={cancel} loading={submitting}>
          {t("confirm")}
        </Button>
        <Button variant="ghost" onClick={() => setOpen(false)} disabled={submitting}>
          {t("keep")}
        </Button>
      </div>
    </div>
  );
}
