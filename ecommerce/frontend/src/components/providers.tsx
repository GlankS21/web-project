"use client";

import { createContext, useCallback, useContext, useEffect, useMemo, useState, type ReactNode } from "react";
import { ToastProvider } from "@/components/toast";
import { api } from "@/lib/client/api";
import type { Session } from "@/lib/session";
import type { Cart } from "@/lib/types";

const SessionContext = createContext<Session | null>(null);

interface CartState {
  /** Units in the cart; null while unknown (signed out, or not loaded yet). */
  count: number | null;
  refresh: () => Promise<void>;
}

const CartContext = createContext<CartState>({ count: null, refresh: async () => {} });

/**
 * The session is decided on the server (from the HttpOnly cookie) and handed down; it changes only
 * through a navigation or router.refresh() after login/logout.
 */
export function Providers({ session, children }: { session: Session | null; children: ReactNode }) {
  const [count, setCount] = useState<number | null>(null);
  const userId = session?.userId;

  const refresh = useCallback(async () => {
    if (!userId) {
      setCount(null);
      return;
    }
    try {
      const cart = await api.get<Cart>("/cart");
      setCount(cart.totalQuantity);
    } catch {
      // The badge is optional; the cart page shows the actual error
    }
  }, [userId]);

  useEffect(() => {
    // Loads the badge for the signed-in user (and clears it after logout)
    // eslint-disable-next-line react-hooks/set-state-in-effect
    void refresh();
  }, [refresh]);

  const cart = useMemo(() => ({ count: userId ? count : null, refresh }), [userId, count, refresh]);
  return (
    <SessionContext.Provider value={session}>
      <CartContext.Provider value={cart}>
        <ToastProvider>{children}</ToastProvider>
      </CartContext.Provider>
    </SessionContext.Provider>
  );
}

export function useSession(): Session | null {
  return useContext(SessionContext);
}

export function useCart(): CartState {
  return useContext(CartContext);
}
