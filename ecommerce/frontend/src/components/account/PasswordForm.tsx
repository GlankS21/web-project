"use client";

import { useTranslations } from "next-intl";
import { useRouter } from "next/navigation";
import { useState, type FormEvent } from "react";
import { Alert, Button, Field } from "@/components/ui";
import { api } from "@/lib/client/api";
import { useErrorMessage } from "@/lib/client/use-error-message";
import { ApiError } from "@/lib/errors";

/**
 * auth-service revokes every session after a password change, so the user is signed out here and
 * asked to sign in with the new password.
 */
export function PasswordForm() {
  const t = useTranslations("password");
  const tAuth = useTranslations("auth");
  const errorMessage = useErrorMessage();
  const router = useRouter();
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<unknown>(null);

  const submit = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    const form = new FormData(event.currentTarget);
    const currentPassword = String(form.get("currentPassword") ?? "");
    const newPassword = String(form.get("newPassword") ?? "");
    if (newPassword !== String(form.get("confirmPassword") ?? "")) {
      setError(tAuth("passwordMismatch"));
      return;
    }
    setSaving(true);
    setError(null);
    try {
      await api.put("/auth/me/password", { currentPassword, newPassword });
      await fetch("/api/auth/logout", { method: "POST" }).catch(() => undefined);
      router.replace("/login?next=/account");
      router.refresh();
    } catch (caught) {
      setSaving(false);
      setError(caught);
    }
  };

  // A wrong current password and an unchanged one are 400 answers whose message explains the problem
  const message =
    typeof error === "string"
      ? error
      : error instanceof ApiError && error.status === 400 && error.violations.length === 0
        ? error.message
        : error
          ? errorMessage(error)
          : null;

  return (
    <form onSubmit={submit} className="flex flex-col gap-4">
      {message && <Alert tone="danger">{message}</Alert>}
      <Field label={t("current")} htmlFor="currentPassword" required>
        <input
          id="currentPassword"
          name="currentPassword"
          type="password"
          required
          maxLength={72}
          autoComplete="current-password"
          className="input"
        />
      </Field>
      <Field
        label={tAuth("newPassword")}
        htmlFor="newPassword"
        hint={tAuth("passwordHint")}
        error={error instanceof ApiError ? error.fieldError("newPassword") : undefined}
        required
      >
        <input
          id="newPassword"
          name="newPassword"
          type="password"
          required
          minLength={8}
          maxLength={72}
          pattern="(?=.*[A-Za-z])(?=.*\d).{8,72}"
          title={tAuth("passwordHint")}
          autoComplete="new-password"
          className="input"
        />
      </Field>
      <Field label={t("confirm")} htmlFor="confirmPassword" required>
        <input
          id="confirmPassword"
          name="confirmPassword"
          type="password"
          required
          maxLength={72}
          autoComplete="new-password"
          className="input"
        />
      </Field>
      <p className="text-xs text-slate-500">{t("signOutNotice")}</p>
      <div>
        <Button type="submit" loading={saving}>
          {t("submit")}
        </Button>
      </div>
    </form>
  );
}
