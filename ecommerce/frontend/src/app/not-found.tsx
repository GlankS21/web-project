import { useTranslations } from "next-intl";
import { ButtonLink, EmptyState } from "@/components/ui";

export default function NotFound() {
  const t = useTranslations("notFound");
  return (
    <div className="container-page py-16">
      <EmptyState title={t("title")}>
        <p className="text-sm text-slate-500">{t("text")}</p>
        <ButtonLink href="/">{t("home")}</ButtonLink>
      </EmptyState>
    </div>
  );
}
