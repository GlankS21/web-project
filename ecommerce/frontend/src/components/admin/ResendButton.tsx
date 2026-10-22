"use client";

import { useTranslations } from "next-intl";
import { useRouter } from "next/navigation";
import { useState } from "react";
import { api } from "@/lib/client/api";
import { useErrorMessage } from "@/lib/client/use-error-message";

export function ResendButton({ notificationId }: { notificationId: string }) {
  const t = useTranslations("adminNotifications");
  const errorMessage = useErrorMessage();
  const router = useRouter();
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const resend = async () => {
    setBusy(true);
    setError(null);
    try {
      await api.post(`/notifications/admin/${notificationId}/resend`);
      router.refresh();
    } catch (caught) {
      setError(errorMessage(caught));
    } finally {
      setBusy(false);
    }
  };

  return (
    <div className="flex flex-col items-end">
      <button type="button" className="link text-xs whitespace-nowrap" disabled={busy} onClick={resend}>
        {busy ? t("resending") : t("resend")}
      </button>
      {error && <p className="mt-1 max-w-40 text-xs text-rose-600">{error}</p>}
    </div>
  );
}
