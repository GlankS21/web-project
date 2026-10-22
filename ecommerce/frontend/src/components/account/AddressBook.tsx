"use client";

import { useTranslations } from "next-intl";
import { useRouter } from "next/navigation";
import { useState, type FormEvent } from "react";
import { AddressFields, formatAddress, readAddress } from "@/components/address/AddressFields";
import { Alert, Badge, Button, EmptyState } from "@/components/ui";
import { api } from "@/lib/client/api";
import { useErrorMessage } from "@/lib/client/use-error-message";
import { ApiError } from "@/lib/errors";
import { formatPhone } from "@/lib/phone";
import type { Address } from "@/lib/types";

const MAX_ADDRESSES = 10;

type Editing = { kind: "new" } | { kind: "edit"; address: Address } | null;

export function AddressBook({ addresses }: { addresses: Address[] }) {
  const t = useTranslations("addresses");
  const tCommon = useTranslations("common");
  const errorMessage = useErrorMessage();
  const router = useRouter();
  const [editing, setEditing] = useState<Editing>(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<unknown>(null);

  const run = async (action: () => Promise<unknown>) => {
    setBusy(true);
    setError(null);
    try {
      await action();
      setEditing(null);
      router.refresh();
    } catch (caught) {
      setError(caught);
    } finally {
      setBusy(false);
    }
  };

  const submit = (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    const form = new FormData(event.currentTarget);
    const body = { ...readAddress(form), defaultAddress: form.get("defaultAddress") === "on" };
    void run(() =>
      editing?.kind === "edit" ? api.put(`/users/me/addresses/${editing.address.id}`, body) : api.post("/users/me/addresses", body),
    );
  };

  const fieldError = (field: string) => (error instanceof ApiError ? error.fieldError(field) : undefined);

  if (editing) {
    const current = editing.kind === "edit" ? editing.address : undefined;
    return (
      <form onSubmit={submit} className="card flex flex-col gap-4 p-6">
        <h2 className="text-lg font-semibold text-slate-900">{current ? t("editTitle") : t("newTitle")}</h2>
        <AddressFields idPrefix="address" defaults={current ?? { countryCode: "RU" }} error={fieldError} />
        {!current?.defaultAddress && (
          <label className="flex items-center gap-2 text-sm text-slate-700">
            <input type="checkbox" name="defaultAddress" defaultChecked={addresses.length === 0} />
            {t("makeDefaultCheckbox")}
          </label>
        )}
        {error !== null && <Alert tone="danger">{errorMessage(error)}</Alert>}
        <div className="flex gap-2">
          <Button type="submit" loading={busy}>
            {t("save")}
          </Button>
          <Button type="button" variant="ghost" onClick={() => setEditing(null)} disabled={busy}>
            {tCommon("cancel")}
          </Button>
        </div>
      </form>
    );
  }

  return (
    <div className="flex flex-col gap-4">
      {error !== null && <Alert tone="danger">{errorMessage(error)}</Alert>}
      {addresses.length === 0 ? (
        <EmptyState title={t("empty")}>
          <Button onClick={() => setEditing({ kind: "new" })}>{t("add")}</Button>
        </EmptyState>
      ) : (
        <>
          <ul className="grid gap-3 md:grid-cols-2">
            {addresses.map((address) => (
              <li key={address.id} className="card flex flex-col gap-2 p-4 text-sm">
                <div className="flex items-center gap-2">
                  <span className="font-medium text-slate-900">{address.recipientName}</span>
                  {address.defaultAddress && <Badge tone="info">{t("default")}</Badge>}
                </div>
                <p className="text-slate-600">{formatPhone(address.phoneNumber)}</p>
                <p className="text-slate-600">{formatAddress(address)}</p>
                <div className="mt-auto flex flex-wrap gap-3 pt-2 text-sm">
                  <button type="button" className="link" disabled={busy} onClick={() => setEditing({ kind: "edit", address })}>
                    {tCommon("edit")}
                  </button>
                  {!address.defaultAddress && (
                    <button
                      type="button"
                      className="link"
                      disabled={busy}
                      onClick={() => run(() => api.patch(`/users/me/addresses/${address.id}/default`))}
                    >
                      {t("makeDefault")}
                    </button>
                  )}
                  <button
                    type="button"
                    className="text-rose-600 hover:underline"
                    disabled={busy}
                    onClick={() => {
                      if (window.confirm(t("deleteConfirm"))) {
                        void run(() => api.delete(`/users/me/addresses/${address.id}`));
                      }
                    }}
                  >
                    {tCommon("delete")}
                  </button>
                </div>
              </li>
            ))}
          </ul>
          <div>
            <Button onClick={() => setEditing({ kind: "new" })} disabled={addresses.length >= MAX_ADDRESSES}>
              {t("add")}
            </Button>
            {addresses.length >= MAX_ADDRESSES && (
              <p className="mt-2 text-xs text-slate-500">{t("limit", { max: MAX_ADDRESSES })}</p>
            )}
          </div>
        </>
      )}
    </div>
  );
}
