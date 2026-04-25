import type { Metadata } from "next";

export const metadata: Metadata = {
  title: "DMS — 문서 관리 시스템",
  description: "Research document management system",
};

export default function RootLayout({ children }: { children: React.ReactNode }) {
  return (
    <html lang="ko">
      <body>{children}</body>
    </html>
  );
}
