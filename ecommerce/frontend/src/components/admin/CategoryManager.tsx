"use client";

import { useRouter } from "next/navigation";
import { useState, type FormEvent } from "react";
import { Alert, Button, Field } from "@/components/ui";
import { api } from "@/lib/client/api";
import { ApiError, errorMessage } from "@/lib/errors";
import type { Category } from "@/lib/types";

type Editing = { kind: "new"; parentId: number | null } | { kind: "edit"; category: Category } | null;

/** Two-level category tree (root categories and their children), edited in place. */
export function CategoryManager({ categories }: { categories: Category[] }) {
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
    const text = (name: string) => String(form.get(name) ?? "").trim();
    const parent = text("parentId");
    const body = {
      name: text("name"),
      slug: text("slug") || null,
      description: text("description") || null,
      parentId: parent ? Number(parent) : null,
      sortOrder: text("sortOrder") ? Number(text("sortOrder")) : null,
    };
    void run(() => (editing?.kind === "edit" ? api.put(`/categories/${editing.category.id}`, body) : api.post("/categories", body)));
  };

  const remove = (category: Category) => {
    if (window.confirm(`Xoá danh mục “${category.name}”?`)) {
      void run(() => api.delete(`/categories/${category.id}`));
    }
  };

  const fieldError = (name: string) => (error instanceof ApiError ? error.fieldError(name) : undefined);

  const form = editing && (
    <form onSubmit={submit} className="card mb-6 grid gap-4 p-6 sm:grid-cols-2">
      <h2 className="font-semibold text-slate-900 sm:col-span-2">
        {editing.kind === "edit" ? `Sửa “${editing.category.name}”` : "Thêm danh mục"}
      </h2>
      <Field label="Tên" htmlFor="category-name" error={fieldError("name")} required>
        <input
          id="category-name"
          name="name"
          required
          maxLength={100}
          defaultValue={editing.kind === "edit" ? editing.category.name : ""}
          className="input"
        />
      </Field>
      <Field label="Slug" htmlFor="category-slug" error={fieldError("slug")} hint="Để trống để tạo từ tên">
        <input
          id="category-slug"
          name="slug"
          pattern="[a-z0-9]+(-[a-z0-9]+)*"
          maxLength={120}
          defaultValue={editing.kind === "edit" ? editing.category.slug : ""}
          className="input"
        />
      </Field>
      <Field label="Danh mục cha" htmlFor="category-parent" error={fieldError("parentId")}>
        <select
          id="category-parent"
          name="parentId"
          defaultValue={(editing.kind === "edit" ? editing.category.parentId : editing.parentId) ?? ""}
          className="input"
        >
          <option value="">(Danh mục gốc)</option>
          {categories
            .filter((root) => editing.kind !== "edit" || root.id !== editing.category.id)
            .map((root) => (
              <option key={root.id} value={root.id}>
                {root.name}
              </option>
            ))}
        </select>
      </Field>
      <Field label="Thứ tự" htmlFor="category-sort" error={fieldError("sortOrder")}>
        <input
          id="category-sort"
          name="sortOrder"
          type="number"
          min={0}
          max={10000}
          defaultValue={editing.kind === "edit" ? editing.category.sortOrder : ""}
          className="input"
        />
      </Field>
      <Field label="Mô tả" htmlFor="category-description" error={fieldError("description")} className="sm:col-span-2">
        <textarea
          id="category-description"
          name="description"
          rows={2}
          maxLength={500}
          defaultValue={editing.kind === "edit" ? (editing.category.description ?? "") : ""}
          className="input"
        />
      </Field>
      {error !== null && (
        <div className="sm:col-span-2">
          <Alert tone="danger">{errorMessage(error)}</Alert>
        </div>
      )}
      <div className="flex gap-2 sm:col-span-2">
        <Button type="submit" loading={busy}>
          Lưu
        </Button>
        <Button type="button" variant="ghost" onClick={() => setEditing(null)} disabled={busy}>
          Huỷ
        </Button>
      </div>
    </form>
  );

  const row = (category: Category, child: boolean) => (
    <div className={`flex items-center gap-3 py-2 ${child ? "pl-6" : ""}`}>
      <div className="min-w-0 flex-1">
        <span className={child ? "text-slate-700" : "font-medium text-slate-900"}>{category.name}</span>
        <span className="ml-2 text-xs text-slate-400">/{category.slug}</span>
      </div>
      <div className="flex gap-3 text-sm">
        {!child && (
          <button type="button" className="link" disabled={busy} onClick={() => setEditing({ kind: "new", parentId: category.id })}>
            + Danh mục con
          </button>
        )}
        <button type="button" className="link" disabled={busy} onClick={() => setEditing({ kind: "edit", category })}>
          Sửa
        </button>
        <button type="button" className="text-rose-600 hover:underline" disabled={busy} onClick={() => remove(category)}>
          Xoá
        </button>
      </div>
    </div>
  );

  return (
    <>
      {form}
      {!editing && error !== null && (
        <div className="mb-4">
          <Alert tone="danger">{errorMessage(error)}</Alert>
        </div>
      )}
      <div className="card divide-y divide-slate-100 px-4">
        {categories.map((root) => (
          <div key={root.id}>
            {row(root, false)}
            {root.children.map((child) => (
              <div key={child.id}>{row(child, true)}</div>
            ))}
          </div>
        ))}
        {categories.length === 0 && <p className="py-4 text-sm text-slate-500">Chưa có danh mục.</p>}
      </div>
      {!editing && (
        <div className="mt-4">
          <Button onClick={() => setEditing({ kind: "new", parentId: null })}>Thêm danh mục gốc</Button>
        </div>
      )}
      <p className="mt-3 text-xs text-slate-500">Không xoá được danh mục còn sản phẩm hoặc danh mục con.</p>
    </>
  );
}
