import type { Metadata } from "next";
import { ResendButton } from "@/components/admin/ResendButton";
import { Pagination, pageIndex } from "@/components/Pagination";
import { PageTitle, StatusBadge } from "@/components/ui";
import { formatDateTime, NOTIFICATION_STATUS, NOTIFICATION_TYPE } from "@/lib/format";
import { apiGet, query } from "@/lib/server/data";
import { oneOf, param, type SearchParams } from "@/lib/search-params";
import type { Notification, NotificationStatus, NotificationType, PageResponse } from "@/lib/types";

export const metadata: Metadata = { title: "E-mail" };

const STATUSES = Object.keys(NOTIFICATION_STATUS) as NotificationStatus[];
const TYPES = Object.keys(NOTIFICATION_TYPE) as NotificationType[];

export default async function AdminNotificationsPage({ searchParams }: { searchParams: Promise<SearchParams> }) {
  const params = await searchParams;
  const status = oneOf(param(params, "status"), STATUSES);
  const type = oneOf(param(params, "type"), TYPES);
  const page = pageIndex(param(params, "page"));
  const notifications = await apiGet<PageResponse<Notification>>(
    `/api/notifications/admin${query({ status, type, page, size: 20 })}`,
    { auth: true },
  );

  return (
    <>
      <PageTitle>E-mail đã gửi</PageTitle>
      <form method="get" className="mb-4 flex flex-wrap gap-2">
        <select name="type" defaultValue={type ?? ""} className="input w-auto" aria-label="Loại">
          <option value="">Mọi loại</option>
          {TYPES.map((value) => (
            <option key={value} value={value}>
              {NOTIFICATION_TYPE[value]}
            </option>
          ))}
        </select>
        <select name="status" defaultValue={status ?? ""} className="input w-auto" aria-label="Trạng thái">
          <option value="">Mọi trạng thái</option>
          {STATUSES.map((value) => (
            <option key={value} value={value}>
              {NOTIFICATION_STATUS[value].label}
            </option>
          ))}
        </select>
        <button type="submit" className="rounded-lg bg-brand-600 px-4 py-2 text-sm font-medium text-white hover:bg-brand-700">
          Lọc
        </button>
      </form>
      <div className="card overflow-x-auto">
        <table className="table">
          <thead>
            <tr>
              <th>Người nhận</th>
              <th>Tiêu đề</th>
              <th>Trạng thái</th>
              <th className="text-right">Lần gửi</th>
              <th>Thời gian</th>
              <th />
            </tr>
          </thead>
          <tbody>
            {notifications.content.map((notification) => (
              <tr key={notification.id}>
                <td className="max-w-48 truncate">{notification.recipient}</td>
                <td>
                  <p className="max-w-80 truncate">{notification.subject}</p>
                  <p className="text-xs text-slate-500">{NOTIFICATION_TYPE[notification.type]}</p>
                  {notification.lastError && notification.status !== "SENT" && (
                    <p className="max-w-80 truncate text-xs text-rose-600" title={notification.lastError}>
                      {notification.lastError}
                    </p>
                  )}
                </td>
                <td>
                  <StatusBadge value={notification.status} labels={NOTIFICATION_STATUS} />
                </td>
                <td className="text-right">{notification.attempts}</td>
                <td className="whitespace-nowrap text-slate-500">{formatDateTime(notification.sentAt ?? notification.createdAt)}</td>
                <td className="text-right">
                  <ResendButton notificationId={notification.id} />
                </td>
              </tr>
            ))}
          </tbody>
        </table>
        {notifications.content.length === 0 && <p className="p-4 text-sm text-slate-500">Không có e-mail nào.</p>}
      </div>
      <Pagination page={notifications.page} totalPages={notifications.totalPages} basePath="/admin/notifications" params={{ status, type }} />
    </>
  );
}
