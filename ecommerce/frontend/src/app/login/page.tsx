import type { Metadata } from "next";
import { redirect } from "next/navigation";
import { AuthForm } from "@/components/auth/AuthForm";
import { safeNext } from "@/lib/safe-redirect";
import { getSession } from "@/lib/server/data";
import { param, type SearchParams } from "@/lib/search-params";

export const metadata: Metadata = { title: "Đăng nhập" };

export default async function LoginPage({ searchParams }: { searchParams: Promise<SearchParams> }) {
  const next = safeNext(param(await searchParams, "next"));
  if (await getSession()) {
    redirect(next);
  }
  return <AuthForm mode="login" next={next} />;
}
