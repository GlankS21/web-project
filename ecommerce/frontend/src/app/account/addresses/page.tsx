import type { Metadata } from "next";
import { getTranslations } from "next-intl/server";
import { AddressBook } from "@/components/account/AddressBook";
import { PageTitle } from "@/components/ui";
import { apiGet } from "@/lib/server/data";
import type { Address } from "@/lib/types";

export async function generateMetadata(): Promise<Metadata> {
  return { title: (await getTranslations("addresses"))("title") };
}

export default async function AddressesPage() {
  const [addresses, t] = await Promise.all([
    apiGet<Address[]>("/api/users/me/addresses", { auth: true }),
    getTranslations("addresses"),
  ]);
  return (
    <>
      <PageTitle>{t("title")}</PageTitle>
      <AddressBook addresses={addresses} />
    </>
  );
}
