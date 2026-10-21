import type { Metadata } from "next";
import { ProfileForm } from "@/components/account/ProfileForm";
import { PageTitle } from "@/components/ui";
import { formatDate } from "@/lib/format";
import { apiGet } from "@/lib/server/data";
import type { UserProfile } from "@/lib/types";

export const metadata: Metadata = { title: "Tài khoản" };

export default async function AccountPage() {
  const profile = await apiGet<UserProfile>("/api/users/me", { auth: true });
  return (
    <>
      <PageTitle>Hồ sơ</PageTitle>
      <div className="card max-w-2xl p-6">
        <p className="mb-6 text-sm text-slate-500">
          {profile.email} · thành viên từ {formatDate(profile.createdAt)}
        </p>
        <ProfileForm profile={profile} />
      </div>
    </>
  );
}
