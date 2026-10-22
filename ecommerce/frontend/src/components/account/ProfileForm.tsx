"use client";

import { useTranslations } from "next-intl";
import { useRouter } from "next/navigation";
import { useState, type FormEvent } from "react";
import { useToast } from "@/components/toast";
import { Alert, Button, Field } from "@/components/ui";
import { api } from "@/lib/client/api";
import { useErrorMessage } from "@/lib/client/use-error-message";
import { ApiError } from "@/lib/errors";
import { formatPhone, normalizePhone, PHONE_INPUT_PATTERN } from "@/lib/phone";
import type { UserProfile } from "@/lib/types";

export function ProfileForm({ profile }: { profile: UserProfile }) {
  const t = useTranslations("profile");
  const tCommon = useTranslations("common");
  const errorMessage = useErrorMessage();
  const router = useRouter();
  const toast = useToast();
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
        phoneNumber: normalizePhone(text("phoneNumber")) || null,
        dateOfBirth: text("dateOfBirth") || null,
      });
      setResult({ ok: true });
      toast.success(t("saved"));
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
      <Field label={t("lastName")} htmlFor="lastName" error={fieldError("lastName")} required>
        <input
          id="lastName"
          name="lastName"
          required
          maxLength={100}
          defaultValue={profile.lastName ?? ""}
          autoComplete="family-name"
          className="input"
        />
      </Field>
      <Field label={t("firstName")} htmlFor="firstName" error={fieldError("firstName")} required>
        <input
          id="firstName"
          name="firstName"
          required
          maxLength={100}
          defaultValue={profile.firstName ?? ""}
          autoComplete="given-name"
          className="input"
        />
      </Field>
      <Field label={t("phone")} htmlFor="phoneNumber" error={fieldError("phoneNumber")} hint={t("phoneHint")}>
        <input
          id="phoneNumber"
          name="phoneNumber"
          type="tel"
          inputMode="tel"
          pattern={PHONE_INPUT_PATTERN}
          maxLength={18}
          placeholder={tCommon("phonePlaceholder")}
          title={t("phoneHint")}
          defaultValue={formatPhone(profile.phoneNumber) ?? ""}
          autoComplete="tel"
          className="input"
        />
      </Field>
      <Field label={t("birthDate")} htmlFor="dateOfBirth" error={fieldError("dateOfBirth")}>
        <input id="dateOfBirth" name="dateOfBirth" type="date" max={today} defaultValue={profile.dateOfBirth ?? ""} className="input" />
      </Field>
      <div className="flex flex-col gap-3 sm:col-span-2">
        {result && !result.ok && <Alert tone="danger">{errorMessage(result.error)}</Alert>}
        <div>
          <Button type="submit" loading={saving}>
            {tCommon("saveChanges")}
          </Button>
        </div>
      </div>
    </form>
  );
}
