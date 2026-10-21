"use client";

import { useRouter } from "next/navigation";
import { useState, type FormEvent } from "react";
import { Alert, Button, Field } from "@/components/ui";
import { api } from "@/lib/client/api";
import { ApiError, errorMessage } from "@/lib/errors";
import type { UserProfile } from "@/lib/types";

export function ProfileForm({ profile }: { profile: UserProfile }) {
  const router = useRouter();
  const [saving, setSaving] = useState(false);
  const [result, setResult] = useState<{ ok: true } | { ok: false; error: unknown } | null>(null);

  const submit = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    const form = new FormData(event.currentTarget);
    const text = (name: string) => String(form.get(name) ?? "").trim();
    setSaving(true);
    setResult(null);
    try {
      await api.put("/users/me", {
        firstName: text("firstName"),
        lastName: text("lastName"),
        phoneNumber: text("phoneNumber").replace(/[\s.-]/g, "") || null,
        dateOfBirth: text("dateOfBirth") || null,
      });
      setResult({ ok: true });
      router.refresh();
    } catch (error) {
      setResult({ ok: false, error });
    } finally {
      setSaving(false);
    }
  };

  const fieldError = (name: string) =>
    result && !result.ok && result.error instanceof ApiError ? result.error.fieldError(name) : undefined;
  const today = new Date().toISOString().slice(0, 10);

  return (
    <form onSubmit={submit} className="grid gap-4 sm:grid-cols-2">
      <Field label="Họ" htmlFor="lastName" error={fieldError("lastName")} required>
        <input id="lastName" name="lastName" required maxLength={100} defaultValue={profile.lastName ?? ""} className="input" />
      </Field>
      <Field label="Tên" htmlFor="firstName" error={fieldError("firstName")} required>
        <input id="firstName" name="firstName" required maxLength={100} defaultValue={profile.firstName ?? ""} className="input" />
      </Field>
      <Field label="Số điện thoại" htmlFor="phoneNumber" error={fieldError("phoneNumber")}>
        <input
          id="phoneNumber"
          name="phoneNumber"
          type="tel"
          pattern="\+?[0-9]{9,15}"
          maxLength={16}
          defaultValue={profile.phoneNumber ?? ""}
          className="input"
        />
      </Field>
      <Field label="Ngày sinh" htmlFor="dateOfBirth" error={fieldError("dateOfBirth")}>
        <input id="dateOfBirth" name="dateOfBirth" type="date" max={today} defaultValue={profile.dateOfBirth ?? ""} className="input" />
      </Field>
      <div className="flex flex-col gap-3 sm:col-span-2">
        {result?.ok && <Alert tone="success">Đã lưu hồ sơ.</Alert>}
        {result && !result.ok && <Alert tone="danger">{errorMessage(result.error)}</Alert>}
        <div>
          <Button type="submit" loading={saving}>
            Lưu thay đổi
          </Button>
        </div>
      </div>
    </form>
  );
}
