"use client";

import { useRouter } from "next/navigation";
import { useState, type FormEvent } from "react";
import { Alert, Button, Field } from "@/components/ui";
import { api } from "@/lib/client/api";
import { ApiError, errorMessage } from "@/lib/errors";

const PASSWORD_HINT = "8–72 ký tự, có ít nhất một chữ cái và một chữ số.";

/** auth-service answers 400 both for a wrong current password and for an unchanged one. */
function describe(error: unknown): string {
  if (error instanceof ApiError && error.status === 400 && error.violations.length === 0) {
    if (/incorrect/i.test(error.message)) {
      return "Mật khẩu hiện tại không đúng.";
    }
    if (/different/i.test(error.message)) {
      return "Mật khẩu mới phải khác mật khẩu hiện tại.";
    }
  }
  return errorMessage(error);
}

/**
 * auth-service revokes every session after a password change, so the user is signed out here and
 * asked to sign in with the new password.
 */
export function PasswordForm() {
  const router = useRouter();
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<unknown>(null);

  const submit = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    const form = new FormData(event.currentTarget);
    const currentPassword = String(form.get("currentPassword") ?? "");
    const newPassword = String(form.get("newPassword") ?? "");
    if (newPassword !== String(form.get("confirmPassword") ?? "")) {
      setError("Mật khẩu nhập lại không khớp.");
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

  const message = typeof error === "string" ? error : error ? describe(error) : null;

  return (
    <form onSubmit={submit} className="flex flex-col gap-4">
      {message && <Alert tone="danger">{message}</Alert>}
      <Field label="Mật khẩu hiện tại" htmlFor="currentPassword" required>
        <input id="currentPassword" name="currentPassword" type="password" required maxLength={72} autoComplete="current-password" className="input" />
      </Field>
      <Field
        label="Mật khẩu mới"
        htmlFor="newPassword"
        hint={PASSWORD_HINT}
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
          title={PASSWORD_HINT}
          autoComplete="new-password"
          className="input"
        />
      </Field>
      <Field label="Nhập lại mật khẩu mới" htmlFor="confirmPassword" required>
        <input id="confirmPassword" name="confirmPassword" type="password" required maxLength={72} autoComplete="new-password" className="input" />
      </Field>
      <p className="text-xs text-slate-500">Sau khi đổi mật khẩu, mọi phiên đăng nhập sẽ kết thúc và bạn cần đăng nhập lại.</p>
      <div>
        <Button type="submit" loading={saving}>
          Đổi mật khẩu
        </Button>
      </div>
    </form>
  );
}
