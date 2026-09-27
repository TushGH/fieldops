"use client";

import { useEffect, useState } from "react";
import { api, submit } from "@/lib/api";

type Business = { id: string; name: string; slug: string; role: string };
type User = { displayName: string; email: string };

export default function Workspace() {
  const [user, setUser] = useState<User | null>(null);
  const [businesses, setBusinesses] = useState<Business[]>([]);
  const [selected, setSelected] = useState<Business | null>(null);
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(true);

  useEffect(() => {
    let active = true;
    async function load() {
      try {
        const me = await api("auth/me");
        if (me.status === 401) { window.location.replace("/login"); return; }
        if (!me.ok) throw new Error("We couldn’t load your account. Please reload to try again.");
        const list = await api("businesses");
        if (!list.ok) throw new Error("We couldn’t load your businesses. Please reload to try again.");
        const profile: User = await me.json();
        const rows: Business[] = await list.json();
        if (active) { setUser(profile); setBusinesses(rows); }
      } catch (cause) {
        if (active) setError(cause instanceof Error ? cause.message : "Unable to connect. Please reload to try again.");
      } finally { if (active) setBusy(false); }
    }
    void load();
    return () => { active = false; };
  }, []);

  async function openBusiness(business: Business) {
    setBusy(true); setError(""); setSelected(null);
    try {
      const response = await api("tenant/context", { headers: { "X-Tenant-ID": business.id } });
      if (response.status === 401) { window.location.replace("/login"); return; }
      if (!response.ok) throw new Error("This business is no longer available to your account. Reload to update the list.");
      const context: { role: string } = await response.json();
      setSelected({ ...business, role: context.role });
    } catch (cause) { setError(cause instanceof Error ? cause.message : "Unable to connect. Please try again."); }
    finally { setBusy(false); }
  }

  async function logout() {
    setBusy(true); setError("");
    try {
      const response = await submit("auth/logout");
      if (!response.ok) throw new Error("Sign out failed. Please try again.");
      window.location.replace("/login");
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : "Sign out failed. Please try again."); setBusy(false);
    }
  }

  return <main className="workspace">
    <header><a className="brand" href="/workspace"><span className="brand-icon">F<span>↗</span></span>FieldOps</a>
      <button className="text-button" disabled={busy} onClick={logout}>Sign out</button></header>
    <section className="workspace-content">
      <p className="eyebrow">YOUR HOME BASE</p>
      <h1>{user ? `Welcome, ${user.displayName}.` : "Welcome to FieldOps."}</h1>
      {busy && <p role="status" className="muted">Please wait…</p>}
      {error && <p role="alert" className="notice error">{error}</p>}
      {user && !selected && <>
        <p className="muted">Choose a business to continue.</p>
        <div className="business-grid">{businesses.map(business => <button className="business-card" key={business.id} disabled={busy} onClick={() => openBusiness(business)}>
          <span className="business-mark" aria-hidden="true">{business.name[0]}</span><strong>{business.name}</strong><span>{business.slug}</span><span className="business-action">Open business ↗</span>
        </button>)}</div>
        {!busy && businesses.length === 0 && <p className="notice">You don’t currently have access to an active business. Contact your business owner for help.</p>}
      </>}
      {selected && <article className="welcome-card"><p className="eyebrow">YOU’RE IN</p><h2>{selected.name}</h2>
        <p>Your business account is ready.</p><p className="muted">Signed in as {user?.email} · {selected.role.toLowerCase().replaceAll("_", " ")}</p>
        <button className="text-button" onClick={() => setSelected(null)}>← Back to your businesses</button>
      </article>}
    </section>
  </main>;
}
