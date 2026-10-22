import type { Metadata } from "next";
import { getTranslations } from "next-intl/server";
import { ResendButton } from "@/components/admin/ResendButton";
import { Pagination, pageIndex } from "@/components/Pagination";
import { PageTitle, StatusBadge } from "@/components/ui";
import { formatDateTime, labelled, NOTIFICATION_STATUS_TONE, NOTIFICATION_TYPES } from "@/lib/format";
import { apiGet, query } from "@/lib/server/data";
import { oneOf, param, type SearchParams } from "@/lib/search-params";
import type { Notification, NotificationStatus, NotificationType, PageResponse } from "@/lib/types";

export async function generateMetadata(): Promise<Metadata> {
  return { title: (await getTranslations("adminNotifications"))("metaTitle") };
}

const STATUSES = Object.keys(NOTIFICATION_STATUS_TONE) as NotificationStatus[];
const TYPES: NotificationType[] = NOTIFICATION_TYPES;

export default async function AdminNotificationsPage({ searchParams }: { searchParams: Promise<SearchParams> }) {
  const params = await searchParams;
  const status = oneOf(param(params, "status"), STATUSES);
  const type = oneOf(param(params, "type"), TYPES);
  const page = pageIndex(param(params, "page"));
  const [notifications, t, tType, tStatus] = await Promise.all([
    apiGet<PageResponse<Notification>>(`/api/notifications/admin${query({ status, type, page, size: 20 })}`, { auth: true }),
    getTranslations("adminNotifications"),
    getTranslations("notificationType"),
    getTranslations("notificationStatus"),
  ]);
  const statuses = labelled(NOTIFICATION_STATUS_TONE, (value) => tStatus(value));

  return (
    <>
      <PageTitle>{t("title")}</PageTitle>
      <form method="get" className="mb-4 flex flex-wrap gap-2">
        <select name="type" defaultValue={type ?? ""} className="input w-auto" aria-label={t("type")}>
          <option value="">{t("allTypes")}</option>
          {TYPES.map((value) => (
            <option key={value} value={value}>
              {tType(value)}
            </option>
          ))}
        </select>
        <select name="status" defaultValue={status ?? ""} className="input w-auto" aria-label={t("status")}>
          <option value="">{t("allStatuses")}</option>
          {STATUSES.map((value) => (
            <option key={value} value={value}>
              {statuses[value].label}
            </option>
          ))}
        </select>
        <button type="submit" className="rounded-lg bg-brand-600 px-4 py-2 text-sm font-medium text-white hover:bg-brand-700">
          {t("filter")}
        </button>
      </form>
      <div className="card overflow-x-auto">
        <table className="table">
          <thead>
            <tr>
              <th>{t("recipient")}</th>
              <th>{t("subject")}</th>
              <th>{t("status")}</th>
              <th className="text-right">{t("attempts")}</th>
              <th>{t("time")}</th>
              <th />
            </tr>
          </thead>
          <tbody>
            {notifications.content.map((notification) => (
              <tr key={notification.id}>
                <td className="max-w-48 truncate">{notification.recipient}</td>
                <td>
                  <p className="max-w-80 truncate">{notification.subject}</p>
                  <p className="text-xs text-slate-500">{tType(notification.type)}</p>
                  {notification.lastError && notification.status !== "SENT" && (
                    <p className="max-w-80 truncate text-xs text-rose-600" title={notification.lastError}>
                      {notification.lastError}
                    </p>
                  )}
                </td>
                <td>
                  <StatusBadge value={notification.status} labels={statuses} />
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
        {notifications.content.length === 0 && <p className="p-4 text-sm text-slate-500">{t("empty")}</p>}
      </div>
      <Pagination
        page={notifications.page}
        totalPages={notifications.totalPages}
        basePath="/admin/notifications"
        params={{ status, type }}
      />
    </>
  );
}
