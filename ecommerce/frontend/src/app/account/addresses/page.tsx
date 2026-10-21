import type { Metadata } from "next";
import { AddressBook } from "@/components/account/AddressBook";
import { PageTitle } from "@/components/ui";
import { apiGet } from "@/lib/server/data";
import type { Address } from "@/lib/types";

export const metadata: Metadata = { title: "Sổ địa chỉ" };

export default async function AddressesPage() {
  const addresses = await apiGet<Address[]>("/api/users/me/addresses", { auth: true });
  return (
    <>
      <PageTitle>Sổ địa chỉ</PageTitle>
      <AddressBook addresses={addresses} />
    </>
  );
}
