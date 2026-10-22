"use client";

import { useTranslations } from "next-intl";
import { Button, EmptyState } from "@/components/ui";

/** Unexpected errors of a page (a service down, a bug); the message itself is not shown to users. */
export default function ErrorPage({ error, reset }: { error: Error & { digest?: string }; reset: () => void }) {
  const t = useTranslations("errorPage");
  return (
    <div className="container-page py-16">
      <EmptyState title={t("title")}>
        <p className="max-w-md text-sm text-slate-500">
          {t("text")}
          {error.digest && <span className="mt-2 block text-xs text-slate-400">{t("code", { digest: error.digest })}</span>}
        </p>
        <Button onClick={reset}>{t("retry")}</Button>
      </EmptyState>
    </div>
  );
}
