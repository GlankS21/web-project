import type { Metadata } from "next";
import { getTranslations } from "next-intl/server";
import { ProfileForm } from "@/components/account/ProfileForm";
import { PageTitle } from "@/components/ui";
import { formatDate } from "@/lib/format";
import { apiGet } from "@/lib/server/data";
import type { UserProfile } from "@/lib/types";

export async function generateMetadata(): Promise<Metadata> {
  return { title: (await getTranslations("account"))("metaTitle") };
}

export default async function AccountPage() {
  const [profile, t] = await Promise.all([
    apiGet<UserProfile>("/api/users/me", { auth: true }),
    getTranslations("account"),
  ]);
  return (
    <>
      <PageTitle>{t("profileTitle")}</PageTitle>
      <div className="card max-w-2xl p-6">
        <p className="mb-6 text-sm text-slate-500">
          {t("memberSince", { email: profile.email, date: formatDate(profile.createdAt) })}
        </p>
        <ProfileForm profile={profile} />
      </div>
    </>
  );
}
