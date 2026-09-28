import type { Metadata } from "next";
import "./globals.css";

export const metadata: Metadata = {
  title: "FieldOps | Good work, connected",
  description: "Find services, connect your team, and make room for good work. Explore the FieldOps marketplace preview.",
};

export default function RootLayout({ children }: Readonly<{ children: React.ReactNode }>) {
  return (
    <html lang="en">
      <body>{children}</body>
    </html>
  );
}
