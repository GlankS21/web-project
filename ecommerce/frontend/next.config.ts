import type { NextConfig } from "next";
import createNextIntlPlugin from "next-intl/plugin";

const withNextIntl = createNextIntlPlugin("./src/i18n/request.ts");

const isProduction = process.env.NODE_ENV === "production";

/**
 * The browser only talks to this server (pages and /api/*), so scripts, styles and requests stay on
 * 'self', except for the YooKassa payment widget: its script and the frames of its card form
 * (3-D Secure included) come from YooKassa / YooMoney. Product images may come from any https host
 * chosen by administrators. Inline scripts are needed by the React Server Components payload;
 * development additionally needs eval for Fast Refresh.
 */
const YOOKASSA = "https://yookassa.ru https://*.yookassa.ru https://yoomoney.ru https://*.yoomoney.ru";

const contentSecurityPolicy = [
  "default-src 'self'",
  `script-src 'self' 'unsafe-inline' ${YOOKASSA}${isProduction ? "" : " 'unsafe-eval'"}`,
  "style-src 'self' 'unsafe-inline'",
  "img-src 'self' https: data:",
  "font-src 'self'",
  `connect-src 'self' ${YOOKASSA}`,
  `frame-src ${YOOKASSA}`,
  "frame-ancestors 'none'",
  "base-uri 'self'",
  "form-action 'self'",
  "object-src 'none'",
].join("; ");

const nextConfig: NextConfig = {
  output: "standalone",
  poweredByHeader: false,
  async headers() {
    return [
      {
        source: "/:path*",
        headers: [
          { key: "Content-Security-Policy", value: contentSecurityPolicy },
          { key: "X-Content-Type-Options", value: "nosniff" },
          { key: "X-Frame-Options", value: "DENY" },
          { key: "Referrer-Policy", value: "strict-origin-when-cross-origin" },
          { key: "Permissions-Policy", value: "camera=(), microphone=(), geolocation=()" },
        ],
      },
    ];
  },
};

export default withNextIntl(nextConfig);
