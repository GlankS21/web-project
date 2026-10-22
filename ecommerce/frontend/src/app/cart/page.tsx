import type { Metadata } from "next";
import { getTranslations } from "next-intl/server";
import { CartView } from "@/components/cart/CartView";
import { Alert, PageTitle } from "@/components/ui";
import { describeError } from "@/lib/errors";
import { apiGet } from "@/lib/server/data";
import type { Cart } from "@/lib/types";

export async function generateMetadata(): Promise<Metadata> {
  return { title: (await getTranslations("cart"))("title") };
}

export default async function CartPage() {
  const [t, tErrors] = await Promise.all([getTranslations("cart"), getTranslations("errors")]);
  let cart: Cart | null = null;
  let error: string | null = null;
  try {
    cart = await apiGet<Cart>("/api/cart", { auth: true });
  } catch (caught) {
    error = describeError(caught, tErrors);
  }

  return (
    <div className="container-page py-8">
      <PageTitle>{t("title")}</PageTitle>
      {cart ? <CartView cart={cart} /> : <Alert tone="danger">{error}</Alert>}
    </div>
  );
}
