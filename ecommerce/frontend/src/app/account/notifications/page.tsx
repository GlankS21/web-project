import type { Metadata } from "next";
import { getTranslations } from "next-intl/server";
import { Pagination, pageIndex } from "@/components/Pagination";
import { Badge, EmptyState, PageTitle } from "@/components/ui";
import { formatDateTime } from "@/lib/format";
import { apiGet, query } from "@/lib/server/data";
import { param, type SearchParams } from "@/lib/search-params";
import type { Notification, PageResponse } from "@/lib/types";

export async function generateMetadata(): Promise<Metadata> {
  return { title: (await getTranslations("notifications"))("title") };
}

export default async function NotificationsPage({ searchParams }: { searchParams: Promise<SearchParams> }) {
  const page = pageIndex(param(await searchParams, "page"));
  const [notifications, t, tType] = await Promise.all([
    apiGet<PageResponse<Notification>>(`/api/notifications${query({ page, size: 10 })}`, { auth: true }),
    getTranslations("notifications"),
    getTranslations("notificationType"),
  ]);

  return (
    <>
      <PageTitle>{t("title")}</PageTitle>
      <p className="-mt-4 mb-6 text-sm text-slate-500">{t("intro")}</p>
      {notifications.content.length === 0 ? (
        <EmptyState title={t("empty")} />
      ) : (
        <ul className="flex flex-col gap-3">
          {notifications.content.map((notification) => (
            <li key={notification.id} className="card">
              <details className="group p-4">
                <summary className="flex cursor-pointer list-none flex-wrap items-center gap-2">
                  <Badge tone="info">{tType(notification.type)}</Badge>
                  <span className="flex-1 font-medium text-slate-900">{notification.subject}</span>
                  <time className="text-xs text-slate-500">{formatDateTime(notification.sentAt ?? notification.createdAt)}</time>
                </summary>
                <pre className="mt-3 font-sans text-sm leading-relaxed whitespace-pre-wrap text-slate-700">{notification.body ?? t("hiddenBody")}</pre>
              </details>
            </li>
          ))}
        </ul>
      )}
      <Pagination page={notifications.page} totalPages={notifications.totalPages} basePath="/account/notifications" params={{}} />
    </>
  );
}
