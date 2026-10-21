import type { Metadata } from "next";
import { redirect } from "next/navigation";
import { CheckoutForm } from "@/components/checkout/CheckoutForm";
import { PageTitle } from "@/components/ui";
import { apiGet } from "@/lib/server/data";
import type { Address, Cart, UserProfile } from "@/lib/types";

export const metadata: Metadata = { title: "Đặt hàng" };

export default async function CheckoutPage() {
  const [cart, addresses, profile] = await Promise.all([
    apiGet<Cart>("/api/cart", { auth: true }),
    apiGet<Address[]>("/api/users/me/addresses", { auth: true }).catch(() => [] as Address[]),
    apiGet<UserProfile>("/api/users/me", { auth: true }).catch(() => null),
  ]);
  if (!cart.checkoutReady) {
    redirect("/cart");
  }

  const fullName = [profile?.lastName, profile?.firstName].filter(Boolean).join(" ");
  return (
    <div className="container-page py-8">
      <PageTitle>Đặt hàng</PageTitle>
      <CheckoutForm
        cart={cart}
        addresses={addresses}
        newAddressDefaults={{ recipientName: fullName, phoneNumber: profile?.phoneNumber ?? "", countryCode: "VN" }}
      />
    </div>
  );
}
