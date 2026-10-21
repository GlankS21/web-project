import type { Metadata } from "next";
import { CartView } from "@/components/cart/CartView";
import { Alert, PageTitle } from "@/components/ui";
import { errorMessage } from "@/lib/errors";
import { apiGet } from "@/lib/server/data";
import type { Cart } from "@/lib/types";

export const metadata: Metadata = { title: "Giỏ hàng" };

export default async function CartPage() {
  let cart: Cart | null = null;
  let error: string | null = null;
  try {
    cart = await apiGet<Cart>("/api/cart", { auth: true });
  } catch (caught) {
    error = errorMessage(caught);
  }

  return (
    <div className="container-page py-8">
      <PageTitle>Giỏ hàng</PageTitle>
      {cart ? <CartView cart={cart} /> : <Alert tone="danger">{error}</Alert>}
    </div>
  );
}
