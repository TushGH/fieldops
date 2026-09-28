import type { Metadata } from "next";
import "./globals.css";

export const metadata: Metadata = {
  title: "FieldOps | Your business, connected",
  description: "Connect your account, business, and field service team.",
};

export default function RootLayout({ children }: Readonly<{ children: React.ReactNode }>) {
  return (
    <html lang="en">
      <body>{children}</body>
    </html>
  );
}
