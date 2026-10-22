"use client";

import { useTranslations } from "next-intl";
import { useRouter } from "next/navigation";
import { useRef, useState, type ChangeEvent, type FormEvent } from "react";
import { useToast } from "@/components/toast";
import { Alert, Button, Field } from "@/components/ui";
import { api } from "@/lib/client/api";
import { useErrorMessage } from "@/lib/client/use-error-message";
import { ApiError } from "@/lib/errors";
import { PRODUCT_STATUS_TONE } from "@/lib/format";
import type { Category, Product, ProductRequest, ProductStatus, UploadedImage } from "@/lib/types";

const MAX_IMAGES = 10;
const MAX_UPLOAD_BYTES = 5 * 1024 * 1024;
const UPLOAD_TYPES = ["image/jpeg", "image/png", "image/webp", "image/gif"];
/** Images uploaded to product-service are served by this site under /api/products/images/. */
const UPLOADED_IMAGE_PREFIX = "/api/products/images/";
/** Absolute https address, or an uploaded image. */
const IMAGE_URL_PATTERN = "https://.+|/api/products/images/[A-Za-z0-9._-]+";

function previewable(url: string): boolean {
  // The Content-Security-Policy only allows https images and this site's own
  return url.startsWith("https://") || url.startsWith(UPLOADED_IMAGE_PREFIX);
}

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
  const t = useTranslations("imageUpload");
  const tForm = useTranslations("productForm");
  const tStatus = useTranslations("productStatus");
  const tCommon = useTranslations("common");
  const errorMessage = useErrorMessage();
  const toast = useToast();
  const fileInput = useRef<HTMLInputElement>(null);
  const [uploading, setUploading] = useState<string | null>(null);
  const [uploadError, setUploadError] = useState<string | null>(null);
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
        toast.success(tForm("saved"));
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

  /** Uploads the chosen files one after the other and appends them to the image list. */
  const upload = async (event: ChangeEvent<HTMLInputElement>) => {
    const files = Array.from(event.target.files ?? []);
    event.target.value = "";
    setUploadError(null);
    const free = MAX_IMAGES - images.length;
    if (files.length > free) {
      setUploadError(t("limit", { max: MAX_IMAGES }));
    }
    const added: ImageRow[] = [];
    let key = nextKey;
    for (const file of files.slice(0, Math.max(free, 0))) {
      if (!UPLOAD_TYPES.includes(file.type)) {
        setUploadError(t("unsupported", { name: file.name }));
        continue;
      }
      if (file.size > MAX_UPLOAD_BYTES) {
        setUploadError(t("tooLarge", { name: file.name }));
        continue;
      }
      setUploading(file.name);
      try {
        const form = new FormData();
        form.append("file", file);
        const uploaded = await api.upload<UploadedImage>("/products/images", form);
        added.push({ key: key++, url: uploaded.url, altText: "" });
      } catch (error) {
        const reason =
          error instanceof ApiError && error.status === 400 && error.violations.length === 0
            ? error.message
            : errorMessage(error);
        setUploadError(t("failed", { name: file.name, reason }));
      }
    }
    setUploading(null);
    if (added.length > 0) {
      setImages((rows) => [...rows, ...added].slice(0, MAX_IMAGES));
      setNextKey(key);
      toast.success(t("uploaded", { count: added.length }));
    }
  };

  const fieldError = (name: string) =>
    result && !result.ok && result.error instanceof ApiError ? result.error.fieldError(name) : undefined;

  return (
    <form onSubmit={submit} className="flex flex-col gap-6">
      <section className="card grid gap-4 p-6 sm:grid-cols-2">
        <Field label={tForm("name")} htmlFor="name" error={fieldError("name")} required className="sm:col-span-2">
          <input id="name" name="name" required maxLength={200} defaultValue={product?.name} className="input" />
        </Field>
        <Field label={tForm("sku")} htmlFor="sku" error={fieldError("sku")} hint={tForm("skuHint")} required>
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
        <Field label={tForm("slug")} htmlFor="slug" error={fieldError("slug")} hint={tForm("slugHint")}>
          <input
            id="slug"
            name="slug"
            pattern="[a-z0-9]+(-[a-z0-9]+)*"
            maxLength={220}
            defaultValue={product?.slug}
            className="input"
          />
        </Field>
        <Field label={tForm("category")} htmlFor="categoryId" error={fieldError("categoryId")} required>
          <select id="categoryId" name="categoryId" required defaultValue={product?.category.id ?? ""} className="input">
            <option value="" disabled>
              {tForm("chooseCategory")}
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
        <Field label={tForm("brand")} htmlFor="brand" error={fieldError("brand")}>
          <input id="brand" name="brand" maxLength={100} defaultValue={product?.brand ?? ""} className="input" />
        </Field>
        <Field label={tForm("price")} htmlFor="price" error={fieldError("price")} required>
          <input id="price" name="price" type="number" required min={0.01} step="0.01" defaultValue={product?.price} className="input" />
        </Field>
        <Field
          label={tForm("compareAtPrice")}
          htmlFor="compareAtPrice"
          error={fieldError("compareAtPrice")}
          hint={tForm("compareAtPriceHint")}
        >
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
        <Field label={tForm("status")} htmlFor="status" error={fieldError("status")} hint={tForm("statusHint")}>
          <select id="status" name="status" defaultValue={product?.status ?? "DRAFT"} className="input">
            {(Object.keys(PRODUCT_STATUS_TONE) as ProductStatus[]).map((value) => (
              <option key={value} value={value}>
                {tStatus(value)}
              </option>
            ))}
          </select>
        </Field>
        <Field label={tForm("shortDescription")} htmlFor="shortDescription" error={fieldError("shortDescription")} className="sm:col-span-2">
          <textarea
            id="shortDescription"
            name="shortDescription"
            rows={2}
            maxLength={500}
            defaultValue={product?.shortDescription ?? ""}
            className="input"
          />
        </Field>
        <Field label={tForm("description")} htmlFor="description" error={fieldError("description")} className="sm:col-span-2">
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
            {tForm("images")}
          </h2>
          <span className="text-xs text-slate-500">
            {tForm("imagesCount", { count: images.length, max: MAX_IMAGES })}
          </span>
        </div>
        {images.map((image, index) => (
          <div key={image.key} className="flex flex-wrap items-start gap-2">
            <div className="h-10 w-10 shrink-0 overflow-hidden rounded bg-slate-100">
              {previewable(image.url) && <img src={image.url} alt="" className="h-full w-full object-cover" />}
            </div>
            <input
              value={image.url}
              onChange={(event) =>
                setImages((rows) => rows.map((row) => (row.key === image.key ? { ...row, url: event.target.value } : row)))
              }
              type="text"
              inputMode="url"
              required
              maxLength={1000}
              pattern={IMAGE_URL_PATTERN}
              title={t("urlHint")}
              placeholder={tForm("imageUrlPlaceholder")}
              className="input min-w-0 flex-1 basis-60"
              aria-label={tForm("imageUrl", { number: index + 1 })}
            />
            <input
              value={image.altText}
              onChange={(event) =>
                setImages((rows) => rows.map((row) => (row.key === image.key ? { ...row, altText: event.target.value } : row)))
              }
              maxLength={255}
              placeholder={tForm("imageAltPlaceholder")}
              className="input min-w-0 flex-1 basis-40 sm:max-w-48"
              aria-label={tForm("imageAlt", { number: index + 1 })}
            />
            <div className="flex gap-1">
              <Button
                type="button"
                variant="ghost"
                size="sm"
                disabled={index === 0}
                onClick={() => setImages((rows) => move(rows, index, index - 1))}
                aria-label={tForm("moveUp", { number: index + 1 })}
              >
                ↑
              </Button>
              <Button
                type="button"
                variant="ghost"
                size="sm"
                onClick={() => setImages((rows) => rows.filter((row) => row.key !== image.key))}
                aria-label={tForm("removeImage", { number: index + 1 })}
              >
                ✕
              </Button>
            </div>
            {fieldError(`images[${index}].url`) && (
              <p className="w-full text-xs text-rose-600">{fieldError(`images[${index}].url`)}</p>
            )}
          </div>
        ))}
        {uploadError && <Alert tone="danger">{uploadError}</Alert>}
        <div className="flex flex-wrap items-center gap-2">
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
            {tForm("addImage")}
          </Button>
          <Button
            type="button"
            variant="secondary"
            size="sm"
            loading={uploading !== null}
            disabled={images.length >= MAX_IMAGES}
            onClick={() => fileInput.current?.click()}
          >
            {uploading ? t("uploading", { name: uploading }) : t("upload")}
          </Button>
          <input
            ref={fileInput}
            type="file"
            accept={UPLOAD_TYPES.join(",")}
            multiple
            hidden
            onChange={(event) => void upload(event)}
          />
          <span className="text-xs text-slate-500">{t("hint")}</span>
        </div>
      </section>

      {result && !result.ok && <Alert tone="danger">{errorMessage(result.error)}</Alert>}
      <div>
        <Button type="submit" size="lg" loading={saving}>
          {product ? tCommon("saveChanges") : tForm("create")}
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
