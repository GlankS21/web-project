import type { Metadata } from "next";
import { Pagination, pageIndex } from "@/components/Pagination";
import { Badge, EmptyState, PageTitle } from "@/components/ui";
import { formatDateTime, NOTIFICATION_TYPE } from "@/lib/format";
import { apiGet, query } from "@/lib/server/data";
import { param, type SearchParams } from "@/lib/search-params";
import type { Notification, PageResponse } from "@/lib/types";

export const metadata: Metadata = { title: "Thông báo" };

export default async function NotificationsPage({ searchParams }: { searchParams: Promise<SearchParams> }) {
  const page = pageIndex(param(await searchParams, "page"));
  const notifications = await apiGet<PageResponse<Notification>>(`/api/notifications${query({ page, size: 10 })}`, {
    auth: true,
  });

  return (
    <>
      <PageTitle>Thông báo</PageTitle>
      <p className="-mt-4 mb-6 text-sm text-slate-500">Các e-mail cửa hàng đã gửi cho bạn.</p>
      {notifications.content.length === 0 ? (
        <EmptyState title="Chưa có thông báo nào" />
      ) : (
        <ul className="flex flex-col gap-3">
          {notifications.content.map((notification) => (
            <li key={notification.id} className="card">
              <details className="group p-4">
                <summary className="flex cursor-pointer list-none flex-wrap items-center gap-2">
                  <Badge tone="info">{NOTIFICATION_TYPE[notification.type]}</Badge>
                  <span className="flex-1 font-medium text-slate-900">{notification.subject}</span>
                  <time className="text-xs text-slate-500">{formatDateTime(notification.sentAt ?? notification.createdAt)}</time>
                </summary>
                <pre className="mt-3 font-sans text-sm leading-relaxed whitespace-pre-wrap text-slate-700">{notification.body}</pre>
              </details>
            </li>
          ))}
        </ul>
      )}
      <Pagination page={notifications.page} totalPages={notifications.totalPages} basePath="/account/notifications" params={{}} />
    </>
  );
}
