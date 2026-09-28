"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { useState, type ReactNode } from "react";
import { api, clearAccountState } from "@/lib/identity-api";

export function IdentityShell({ children, signedIn = false }: { children: ReactNode; signedIn?: boolean }) {
  const router = useRouter();
  const [error, setError] = useState("");
  async function logout() {
    try { await api("auth/logout", { method: "POST" }); clearAccountState(); router.replace("/login"); }
    catch { setError("Sign out failed. Please try again."); }
  }
  return <div className="identity-shell">
    <header className="identity-header"><Link href="/" className="brand">FieldOps<span>.</span></Link>
      {signedIn ? <nav aria-label="Account"><a href="/select-business">Switch business</a><a href="/invitations">Invitations</a><button onClick={logout}>Sign out</button></nav>
        : <a href="/login">Sign in</a>}
    </header>
    <main className="identity-main">{error && <p role="alert" className="error">{error}</p>}{children}</main>
    <footer className="identity-footer">Field service, thoughtfully organized.</footer>
  </div>;
}
export function Heading({ eyebrow, title, children }: { eyebrow: string; title: string; children?: ReactNode }) {
  return <div className="identity-heading"><p className="eyebrow">{eyebrow}</p><h1>{title}</h1>{children && <p className="muted">{children}</p>}</div>;
}
