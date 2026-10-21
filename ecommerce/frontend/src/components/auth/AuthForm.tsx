"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { useState, type FormEvent } from "react";
import { Alert, Button, Field } from "@/components/ui";
import { api } from "@/lib/client/api";
import { ApiError, errorMessage } from "@/lib/errors";

interface Props {
  mode: "login" | "register";
  /** Already validated by the page. */
  next: string;
}

const PASSWORD_HINT = "8–72 ký tự, có ít nhất một chữ cái và một chữ số.";

export function AuthForm({ mode, next }: Props) {
  const router = useRouter();
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<ApiError | string | null>(null);
  const isLogin = mode === "login";

  const submit = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    const form = new FormData(event.currentTarget);
    const body = Object.fromEntries(
      (isLogin ? ["email", "password"] : ["firstName", "lastName", "email", "password"]).map((name) => [
        name,
        String(form.get(name) ?? "").trim(),
      ]),
    );
    if (!isLogin && body.password !== String(form.get("confirmPassword") ?? "").trim()) {
      setError("Mật khẩu nhập lại không khớp.");
      return;
    }
    setSubmitting(true);
    setError(null);
    try {
      await api.post(`/auth/${mode}`, body);
      router.replace(next);
      router.refresh();
    } catch (caught) {
      setSubmitting(false);
      if (caught instanceof ApiError && isLogin && caught.status === 401) {
        setError("Email hoặc mật khẩu không đúng.");
      } else if (caught instanceof ApiError && isLogin && caught.status === 403) {
        setError("Tài khoản đã bị khoá. Vui lòng liên hệ cửa hàng.");
      } else if (caught instanceof ApiError && caught.status === 409) {
        setError("Email này đã được đăng ký.");
      } else {
        setError(caught instanceof ApiError ? caught : errorMessage(caught));
      }
    }
  };

  const fieldError = (name: string) => (error instanceof ApiError ? error.fieldError(name) : undefined);
  const message = typeof error === "string" ? error : error ? errorMessage(error) : null;
  const switchHref = `${isLogin ? "/register" : "/login"}${next !== "/" ? `?next=${encodeURIComponent(next)}` : ""}`;

  return (
    <div className="container-page flex justify-center py-12">
      <div className="card w-full max-w-md p-8">
        <h1 className="text-2xl font-semibold text-slate-900">{isLogin ? "Đăng nhập" : "Tạo tài khoản"}</h1>
        <p className="mt-1 text-sm text-slate-500">
          {isLogin ? "Chưa có tài khoản? " : "Đã có tài khoản? "}
          <Link href={switchHref} className="link">
            {isLogin ? "Đăng ký" : "Đăng nhập"}
          </Link>
        </p>

        <form onSubmit={submit} className="mt-6 flex flex-col gap-4">
          {message && <Alert tone="danger">{message}</Alert>}
          {!isLogin && (
            <div className="grid grid-cols-2 gap-3">
              <Field label="Họ" htmlFor="lastName" error={fieldError("lastName")} required>
                <input id="lastName" name="lastName" required maxLength={100} autoComplete="family-name" className="input" />
              </Field>
              <Field label="Tên" htmlFor="firstName" error={fieldError("firstName")} required>
                <input id="firstName" name="firstName" required maxLength={100} autoComplete="given-name" className="input" />
              </Field>
            </div>
          )}
          <Field label="Email" htmlFor="email" error={fieldError("email")} required>
            <input id="email" name="email" type="email" required maxLength={255} autoComplete="email" className="input" />
          </Field>
          <Field
            label="Mật khẩu"
            htmlFor="password"
            error={fieldError("password")}
            hint={isLogin ? undefined : PASSWORD_HINT}
            required
          >
            <input
              id="password"
              name="password"
              type="password"
              required
              minLength={isLogin ? undefined : 8}
              maxLength={72}
              pattern={isLogin ? undefined : "(?=.*[A-Za-z])(?=.*\\d).{8,72}"}
              title={isLogin ? undefined : PASSWORD_HINT}
              autoComplete={isLogin ? "current-password" : "new-password"}
              className="input"
            />
          </Field>
          {!isLogin && (
            <Field label="Nhập lại mật khẩu" htmlFor="confirmPassword" required>
              <input id="confirmPassword" name="confirmPassword" type="password" required maxLength={72} autoComplete="new-password" className="input" />
            </Field>
          )}
          <Button type="submit" size="lg" loading={submitting}>
            {isLogin ? "Đăng nhập" : "Đăng ký"}
          </Button>
        </form>
      </div>
    </div>
  );
}
