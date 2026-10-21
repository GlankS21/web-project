"use client";

import { useRouter } from "next/navigation";
import { useState, type FormEvent } from "react";
import { Alert, Button, Field } from "@/components/ui";
import { api } from "@/lib/client/api";
import { ApiError, errorMessage } from "@/lib/errors";
import { PRODUCT_STATUS } from "@/lib/format";
import type { Category, Product, ProductRequest, ProductStatus } from "@/lib/types";

const MAX_IMAGES = 10;

interface ImageRow {
  /** Stable React key; not sent. */
  key: number;
  url: string;
  altText: string;
}

interface Props {
  categories: Category[];
  /** Absent when creating. */
  product?: Product;
}

export function ProductForm({ categories, product }: Props) {
  const router = useRouter();
  const [images, setImages] = useState<ImageRow[]>(
    () => product?.images.map((image, index) => ({ key: index, url: image.url, altText: image.altText ?? "" })) ?? [],
  );
  const [nextKey, setNextKey] = useState(images.length);
  const [saving, setSaving] = useState(false);
  const [result, setResult] = useState<{ ok: true } | { ok: false; error: unknown } | null>(null);

  const submit = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    const form = new FormData(event.currentTarget);
    const text = (name: string) => String(form.get(name) ?? "").trim();
    const optional = (name: string) => text(name) || null;
    const body: ProductRequest = {
      sku: text("sku").toUpperCase(),
      name: text("name"),
      slug: optional("slug"),
      shortDescription: optional("shortDescription"),
      description: optional("description"),
      brand: optional("brand"),
      price: Number(text("price")),
      compareAtPrice: optional("compareAtPrice") ? Number(text("compareAtPrice")) : null,
      categoryId: Number(text("categoryId")),
      status: text("status") as ProductStatus,
      images: images
        .filter((image) => image.url.trim())
        .map((image) => ({ url: image.url.trim(), altText: image.altText.trim() || null })),
    };

    setSaving(true);
    setResult(null);
    try {
      if (product) {
        await api.put(`/products/${product.id}`, body);
        setResult({ ok: true });
        router.refresh();
      } else {
        const created = await api.post<Product>("/products", body);
        router.push(`/admin/products/${created.id}`);
      }
    } catch (error) {
      setResult({ ok: false, error });
    } finally {
      setSaving(false);
    }
  };

  const fieldError = (name: string) =>
    result && !result.ok && result.error instanceof ApiError ? result.error.fieldError(name) : undefined;

  return (
    <form onSubmit={submit} className="flex flex-col gap-6">
      <section className="card grid gap-4 p-6 sm:grid-cols-2">
        <Field label="Tên sản phẩm" htmlFor="name" error={fieldError("name")} required className="sm:col-span-2">
          <input id="name" name="name" required maxLength={200} defaultValue={product?.name} className="input" />
        </Field>
        <Field label="SKU" htmlFor="sku" error={fieldError("sku")} hint="3–64 ký tự: chữ in hoa, số, dấu gạch ngang" required>
          <input
            id="sku"
            name="sku"
            required
            pattern="[A-Za-z0-9][A-Za-z0-9\-]{2,63}"
            maxLength={64}
            defaultValue={product?.sku}
            className="input uppercase"
          />
        </Field>
        <Field label="Slug (đường dẫn)" htmlFor="slug" error={fieldError("slug")} hint="Để trống để tạo từ tên">
          <input
            id="slug"
            name="slug"
            pattern="[a-z0-9]+(-[a-z0-9]+)*"
            maxLength={220}
            defaultValue={product?.slug}
            className="input"
          />
        </Field>
        <Field label="Danh mục" htmlFor="categoryId" error={fieldError("categoryId")} required>
          <select id="categoryId" name="categoryId" required defaultValue={product?.category.id ?? ""} className="input">
            <option value="" disabled>
              Chọn danh mục
            </option>
            {categories.map((root) => (
              <optgroup key={root.id} label={root.name}>
                <option value={root.id}>{root.name}</option>
                {root.children.map((child) => (
                  <option key={child.id} value={child.id}>
                    {root.name} › {child.name}
                  </option>
                ))}
              </optgroup>
            ))}
          </select>
        </Field>
        <Field label="Thương hiệu" htmlFor="brand" error={fieldError("brand")}>
          <input id="brand" name="brand" maxLength={100} defaultValue={product?.brand ?? ""} className="input" />
        </Field>
        <Field label="Giá bán" htmlFor="price" error={fieldError("price")} required>
          <input id="price" name="price" type="number" required min={0.01} step="0.01" defaultValue={product?.price} className="input" />
        </Field>
        <Field label="Giá gốc (gạch ngang)" htmlFor="compareAtPrice" error={fieldError("compareAtPrice")} hint="Phải lớn hơn giá bán">
          <input
            id="compareAtPrice"
            name="compareAtPrice"
            type="number"
            min={0.01}
            step="0.01"
            defaultValue={product?.compareAtPrice ?? ""}
            className="input"
          />
        </Field>
        <Field label="Trạng thái" htmlFor="status" error={fieldError("status")} hint="Chỉ sản phẩm “Đang bán” hiển thị cho khách">
          <select id="status" name="status" defaultValue={product?.status ?? "DRAFT"} className="input">
            {(Object.keys(PRODUCT_STATUS) as ProductStatus[]).map((value) => (
              <option key={value} value={value}>
                {PRODUCT_STATUS[value].label}
              </option>
            ))}
          </select>
        </Field>
        <Field label="Mô tả ngắn" htmlFor="shortDescription" error={fieldError("shortDescription")} className="sm:col-span-2">
          <textarea
            id="shortDescription"
            name="shortDescription"
            rows={2}
            maxLength={500}
            defaultValue={product?.shortDescription ?? ""}
            className="input"
          />
        </Field>
        <Field label="Mô tả chi tiết" htmlFor="description" error={fieldError("description")} className="sm:col-span-2">
          <textarea
            id="description"
            name="description"
            rows={8}
            maxLength={10_000}
            defaultValue={product?.description ?? ""}
            className="input"
          />
        </Field>
      </section>

      <section className="card flex flex-col gap-3 p-6" aria-labelledby="images-title">
        <div className="flex items-center justify-between">
          <h2 id="images-title" className="font-semibold text-slate-900">
            Hình ảnh
          </h2>
          <span className="text-xs text-slate-500">
            {images.length}/{MAX_IMAGES} · ảnh đầu tiên là ảnh đại diện
          </span>
        </div>
        {images.map((image, index) => (
          <div key={image.key} className="flex flex-wrap items-start gap-2">
            {/* Preview only renders https images (Content-Security-Policy) */}
            <div className="h-10 w-10 shrink-0 overflow-hidden rounded bg-slate-100">
              {image.url.startsWith("https://") && <img src={image.url} alt="" className="h-full w-full object-cover" />}
            </div>
            <input
              value={image.url}
              onChange={(event) => setImages((rows) => rows.map((row) => (row.key === image.key ? { ...row, url: event.target.value } : row)))}
              type="url"
              required
              maxLength={1000}
              placeholder="https://..."
              className="input min-w-60 flex-1"
              aria-label={`URL ảnh ${index + 1}`}
            />
            <input
              value={image.altText}
              onChange={(event) =>
                setImages((rows) => rows.map((row) => (row.key === image.key ? { ...row, altText: event.target.value } : row)))
              }
              maxLength={255}
              placeholder="Mô tả ảnh"
              className="input w-48"
              aria-label={`Mô tả ảnh ${index + 1}`}
            />
            <div className="flex gap-1">
              <Button
                type="button"
                variant="ghost"
                size="sm"
                disabled={index === 0}
                onClick={() => setImages((rows) => move(rows, index, index - 1))}
                aria-label="Lên"
              >
                ↑
              </Button>
              <Button
                type="button"
                variant="ghost"
                size="sm"
                onClick={() => setImages((rows) => rows.filter((row) => row.key !== image.key))}
                aria-label="Xoá ảnh"
              >
                ✕
              </Button>
            </div>
            {fieldError(`images[${index}].url`) && <p className="w-full text-xs text-rose-600">{fieldError(`images[${index}].url`)}</p>}
          </div>
        ))}
        <div>
          <Button
            type="button"
            variant="secondary"
            size="sm"
            disabled={images.length >= MAX_IMAGES}
            onClick={() => {
              setImages((rows) => [...rows, { key: nextKey, url: "", altText: "" }]);
              setNextKey((key) => key + 1);
            }}
          >
            Thêm ảnh
          </Button>
        </div>
      </section>

      {result?.ok && <Alert tone="success">Đã lưu sản phẩm.</Alert>}
      {result && !result.ok && <Alert tone="danger">{errorMessage(result.error)}</Alert>}
      <div>
        <Button type="submit" size="lg" loading={saving}>
          {product ? "Lưu thay đổi" : "Tạo sản phẩm"}
        </Button>
      </div>
    </form>
  );
}

function move<T>(rows: T[], from: number, to: number): T[] {
  const copy = [...rows];
  const [row] = copy.splice(from, 1);
  copy.splice(to, 0, row);
  return copy;
}
