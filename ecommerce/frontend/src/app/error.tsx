"use client";

import { Button, EmptyState } from "@/components/ui";

/** Unexpected errors of a page (a service down, a bug); the message itself is not shown to users. */
export default function ErrorPage({ error, reset }: { error: Error & { digest?: string }; reset: () => void }) {
  return (
    <div className="container-page py-16">
      <EmptyState title="Đã có lỗi khi tải trang">
        <p className="max-w-md text-sm text-slate-500">
          Một dịch vụ có thể đang tạm gián đoạn. Vui lòng thử lại sau giây lát.
          {error.digest && <span className="mt-2 block text-xs text-slate-400">Mã lỗi: {error.digest}</span>}
        </p>
        <Button onClick={reset}>Thử lại</Button>
      </EmptyState>
    </div>
  );
}
