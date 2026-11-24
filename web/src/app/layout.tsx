import type { Metadata } from "next";
import Link from "next/link";
import { SITE_DESCRIPTION, SITE_NAME } from "@/lib/site";
import "./globals.css";

export const metadata: Metadata = {
  title: { default: SITE_NAME, template: `%s · ${SITE_NAME}` },
  description: SITE_DESCRIPTION,
};

export default function RootLayout({ children }: Readonly<{ children: React.ReactNode }>) {
  return (
    <html lang="ko">
      <body className="min-h-screen bg-white text-zinc-900 antialiased">
        <header className="border-b border-zinc-200">
          <div className="mx-auto max-w-3xl px-4 py-3">
            <Link href="/" className="font-semibold">
              {SITE_NAME}
            </Link>
          </div>
        </header>
        <main className="mx-auto max-w-3xl px-4 py-8">{children}</main>
      </body>
    </html>
  );
}
