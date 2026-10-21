import type { Metadata } from "next";
import { PasswordForm } from "@/components/account/PasswordForm";
import { PageTitle } from "@/components/ui";

export const metadata: Metadata = { title: "Đổi mật khẩu" };

export default function PasswordPage() {
  return (
    <>
      <PageTitle>Đổi mật khẩu</PageTitle>
      <div className="card max-w-md p-6">
        <PasswordForm />
      </div>
    </>
  );
}
