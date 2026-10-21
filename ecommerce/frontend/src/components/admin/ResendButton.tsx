"use client";

import { useRouter } from "next/navigation";
import { useState } from "react";
import { api } from "@/lib/client/api";
import { errorMessage } from "@/lib/errors";

export function ResendButton({ notificationId }: { notificationId: string }) {
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
        {busy ? "Đang gửi…" : "Gửi lại"}
      </button>
      {error && <p className="mt-1 max-w-40 text-xs text-rose-600">{error}</p>}
    </div>
  );
}
