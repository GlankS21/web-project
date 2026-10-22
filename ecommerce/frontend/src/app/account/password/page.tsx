import type { Metadata } from "next";
import { getTranslations } from "next-intl/server";
import { LogoutAllButton } from "@/components/account/LogoutAllButton";
import { PasswordForm } from "@/components/account/PasswordForm";
import { PageTitle } from "@/components/ui";

export async function generateMetadata(): Promise<Metadata> {
  return { title: (await getTranslations("password"))("title") };
}

export default async function PasswordPage() {
  const [t, tPassword] = await Promise.all([getTranslations("sessions"), getTranslations("password")]);
  return (
    <>
      <PageTitle>{tPassword("title")}</PageTitle>
      <div className="flex max-w-md flex-col gap-6">
        <div className="card p-6">
          <PasswordForm />
        </div>
        <section className="card p-6" aria-labelledby="sessions-title">
          <h2 id="sessions-title" className="font-semibold text-slate-900">
            {t("title")}
          </h2>
          <p className="mt-1 mb-4 text-sm text-slate-500">{t("text")}</p>
          <LogoutAllButton />
        </section>
      </div>
    </>
  );
}
