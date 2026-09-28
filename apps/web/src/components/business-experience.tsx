"use client";

import { useRouter } from "next/navigation";
import { useEffect, useRef, useState, type FormEvent } from "react";
import { api, ApiError, destination, rememberUser, setIntent, type Business, type Invitation, type Readiness, type User } from "@/lib/identity-api";
import { Heading, IdentityShell } from "./identity-shell";

type View = "entry" | "select" | "create" | "workspace" | "setup" | "invitations" | "accept" | "platform";
type State = { user: User; businesses?: Business[]; business?: Business; invitations: Invitation[]; readiness?: Readiness; invitation?: Invitation };
export function BusinessExperience({ view, tenant }: { view: View; tenant?: string }) {
  const router = useRouter();
  const linkToken = useRef("");
  const [state, setState] = useState<State>();
  const [error, setError] = useState("");
  const [notice, setNotice] = useState("");
  const [busy, setBusy] = useState(false);
  const [revision, setRevision] = useState(0);
  useEffect(() => {
    const controller = new AbortController();
    const signal = controller.signal;
    async function load() {
      let invitationId = new URLSearchParams(window.location.search).get("id");
      if (view === "accept") {
        const token = new URLSearchParams(window.location.hash.slice(1)).get("token") ?? linkToken.current;
        if (token) {
          linkToken.current = token;
          history.replaceState(null, "", window.location.pathname + window.location.search);
          const resolved = await api<{ id: string }>("invitations/resolve", { method: "POST", data: { token }, signal });
          linkToken.current = "";
          invitationId = resolved.id;
          setIntent(`invitation:${resolved.id}`);
          history.replaceState(null, "", `/invitations/accept?id=${resolved.id}`);
        } else if (invitationId) setIntent(`invitation:${invitationId}`);
      }
      const user = await api<User>("auth/me", { signal });
      rememberUser(user.id);
      if (!user.emailVerifiedAt) { window.location.replace("/verify-email"); return; }
      if (view === "entry") { window.location.replace(await destination(user, signal)); return; }
      const next: State = { user, invitations: [] };
      if (view === "select") next.businesses = await api<Business[]>("businesses", { signal });
      if (["select", "invitations", "workspace", "setup"].includes(view)) next.invitations = await api<Invitation[]>("invitations", { signal });
      if (view === "accept") {
        if (!invitationId || !/^[0-9a-f-]{36}$/.test(invitationId)) throw new Error("Open an invitation from your email, or check your invitations inbox.");
        next.invitation = await api<Invitation>(`invitations/${invitationId}`, { signal });
      }
      if (tenant) {
        const context = await api<{ role: string }>("tenant/context", { tenant, signal });
        next.business = { ...await api<Business>("tenant", { tenant, signal }), role: context.role };
        next.readiness = await api<Readiness>("tenant/onboarding", { tenant, signal });
        sessionStorage.setItem(`fieldops:tenant:${user.id}`, tenant);
        if (view === "workspace" && next.readiness.businessSetupRequired) {
          window.location.replace(`/app/${tenant}/onboarding/business`); return;
        }
        if (context.role === "BUSINESS_OWNER") next.invitations = await api<Invitation[]>("tenant/invitations", { tenant, signal });
      }
      if (view === "platform") await api("platform/access", { signal });
      if (!signal.aborted) setState(next);
    }
    load().catch(error => {
      if (signal.aborted) return;
      if (error instanceof ApiError && error.status === 401) {
        if (view === "create") setIntent("create-business");
        window.location.replace("/login");
      } else setError(error instanceof Error ? error.message : "Unable to load your account.");
    });
    const fragmentChanged = () => setRevision(value => value + 1);
    window.addEventListener("hashchange", fragmentChanged);
    return () => { controller.abort(); window.removeEventListener("hashchange", fragmentChanged); };
  }, [view, tenant, revision]);

  async function action(work: () => Promise<void>) {
    setBusy(true); setError(""); setNotice("");
    try { await work(); } catch (error) { setError(error instanceof Error ? error.message : "Please try again."); }
    finally { setBusy(false); }
  }
  function create(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const data = Object.fromEntries(new FormData(event.currentTarget));
    void action(async () => {
      if (view === "platform") {
        const invitation = await api<Invitation>("platform/businesses", { method: "POST", data });
        setNotice(`Owner invitation created for ${invitation.businessName}. Business reference: ${invitation.tenantId}. Access remains closed until the owner accepts.`);
      } else {
        const business = await api<Business>("businesses", { method: "POST", data });
        sessionStorage.removeItem("fieldops:intent");
        router.push(`/app/${business.id}`);
      }
    });
  }
  function invite(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const form = event.currentTarget;
    const data = Object.fromEntries(new FormData(form));
    void action(async () => {
      await api("tenant/invitations", { method: "POST", data, tenant });
      form.reset(); setNotice("Invitation created. Your teammate can accept after verifying their email."); setRevision(value => value + 1);
    });
  }
  function accept(invitation: Invitation) {
    void action(async () => {
      const result = await api<{ tenantId: string }>(`invitations/${invitation.id}/accept`, { method: "POST" });
      sessionStorage.removeItem("fieldops:intent"); router.push(`/app/${result.tenantId}`);
    });
  }
  return <IdentityShell signedIn>
    {error && <div className="panel error" role="alert"><p>{error}</p>{view === "accept" && <p className="mt-4">If this invitation is for another email, sign out and sign in to the matching account.</p>}<a href="/select-business">Choose a business</a> · <a href="/invitations">Check invitations</a></div>}
    {notice && <p role="status" className="notice">{notice}</p>}
    {!state && !error && <p role="status">Loading your account…</p>}
    {state && <>
      {(view === "create" || view === "platform") && <div className="account-grid">
        <Heading eyebrow={view === "platform" ? "Platform administration" : "Start your business"} title={view === "platform" ? "Invite a business owner." : "Make room for your team."}>
          {view === "platform" ? "The owner receives an invitation and sets up their own account. This does not give you membership in their business." : `Signed in as ${state.user.email}. Your existing account will own this business.`}
        </Heading>
        <div className="stack"><form className="panel stack" onSubmit={create}>
          <label>Business name<input name="name" required maxLength={200} autoComplete="organization" /></label>
          <label>Business slug<input name="slug" required minLength={3} maxLength={63} pattern="[a-z0-9]+(-[a-z0-9]+)*" /><span className="hint">A stable identifier, such as smith-plumbing.</span></label>
          {view === "platform" && <label>Owner email<input name="email" type="email" required maxLength={254} /></label>}
          <button disabled={busy}>{busy ? "Creating…" : view === "platform" ? "Create and invite owner" : "Create business"}</button>
        </form>
        {view === "platform" && <form className="panel stack" onSubmit={event => {
          event.preventDefault(); const data = new FormData(event.currentTarget);
          void action(async () => { await api(`platform/businesses/${data.get("tenant")}/owner-invitation`, { method: "POST", data: { email: data.get("email") } }); setNotice("Replacement owner invitation created. Previous pending offers are revoked."); });
        }}><h2>Reissue an owner invitation</h2><label>Business reference<input name="tenant" required pattern="[0-9a-f-]{36}" /></label><label>Owner email<input name="email" type="email" required /></label><button disabled={busy}>Reissue invitation</button></form>}
        </div>
      </div>}
      {view === "select" && <>
        <Heading eyebrow={`Hello, ${state.user.displayName}`} title="Choose your business.">Each business has its own team and workspace.</Heading>
        <div className="business-grid">{state.businesses?.map(business => <a className="panel business-card" key={business.id} href={`/app/${business.id}`}><span className="badge">{business.role.replaceAll("_", " ")}</span><h2>{business.name}</h2><p className="muted">Open workspace →</p></a>)}</div>
        {!state.businesses?.length && <p className="panel">You haven’t joined a business yet. Accept an invitation below or create your own.</p>}
        <div className="actions"><a className="button" href="/create-business">Create another business</a>{state.user.platformAdmin && <a href="/platform">Platform administration</a>}</div>
      </>}
      {(view === "workspace" || view === "setup") && state.business && <>
        <Heading eyebrow={state.business.role.replaceAll("_", " ")} title={state.business.name}>
          {view === "setup" ? "Review your business identity, then continue. Inviting teammates is optional." : "Your business workspace is ready. Operational workflows will arrive in future milestones."}
        </Heading>
        <section className="panel stack"><h2>{view === "setup" ? "Business setup" : "Business details"}</h2>
          <dl><dt>Business name</dt><dd>{state.business.name}</dd><dt>Business slug</dt><dd>{state.business.slug}</dd></dl>
          {view === "setup" && state.business.role === "BUSINESS_OWNER" && <button disabled={busy} onClick={() => void action(async () => {
            await api("tenant/onboarding/business/complete", { method: "POST", tenant }); router.push(`/app/${tenant}`);
          })}>Confirm business and continue</button>}
          {view === "workspace" && <p className="muted">{state.readiness?.businessComplete ? "Business setup completed." : "Your business owner can complete business setup."}</p>}
        </section>
        {state.business.role === "BUSINESS_OWNER" && <section className="panel mt-6"><h2>Invite your team <span className="hint">Optional</span></h2>
          <form onSubmit={invite} className="invite-form"><label>Email address<input name="email" type="email" required maxLength={254} /></label>
            <label>Role<select name="role" defaultValue="TECHNICIAN"><option value="TECHNICIAN">Technician</option><option value="DISPATCHER">Dispatcher</option><option value="BUSINESS_OWNER">Business owner</option></select></label><button disabled={busy}>Send invitation</button></form>
          <ul className="invitation-list">{state.invitations.map(invitation => <li key={invitation.id}><div><strong>{invitation.email}</strong><p className="hint">{invitation.role.replaceAll("_", " ")} · {new Date(invitation.expiresAt) <= new Date() && invitation.status === "PENDING" ? "EXPIRED" : invitation.status}</p></div>
            {invitation.status !== "ACCEPTED" && <div className="actions"><button className="secondary" disabled={busy} onClick={() => void action(async () => { await api(`tenant/invitations/${invitation.id}/resend`, { method: "POST", tenant }); setRevision(value => value + 1); })}>Resend</button>
              {invitation.status === "PENDING" && <button className="secondary" disabled={busy} onClick={() => void action(async () => { await api(`tenant/invitations/${invitation.id}/revoke`, { method: "POST", tenant }); setRevision(value => value + 1); })}>Revoke</button>}</div>}</li>)}</ul>
        </section>}
        <p className="mt-6"><a href="/invitations">View invitations to other businesses</a>{state.user.platformAdmin && <> · <a href="/platform">Platform administration</a></>}</p>
      </>}
      {view === "accept" && state.invitation && <div className="account-grid">
        <Heading eyebrow="You’re invited" title={`Join ${state.invitation.businessName}.`}>You are signed in as {state.user.email}. Accepting adds this business to your account.</Heading>
        <section className="panel stack"><h2>{state.invitation.role.replaceAll("_", " ")}</h2><p>Invitation for {state.invitation.email}</p><p className="hint">Expires {new Date(state.invitation.expiresAt).toLocaleDateString()}</p>
          <button disabled={busy || !["PENDING", "ACCEPTED"].includes(state.invitation.status)} onClick={() => accept(state.invitation!)}>Accept invitation</button><a href="/invitations">View all invitations</a></section>
      </div>}
      {(view === "invitations" || view === "select") && <section className="mt-6">
        {view === "invitations" ? <Heading eyebrow="Your teams" title="Business invitations.">Only invitations addressed to {state.user.email} appear here.</Heading> : <h2>Pending invitations</h2>}
        {!state.invitations.length && <p className="panel muted">No pending invitations.</p>}
        <div className="business-grid">{state.invitations.map(invitation => <div className="panel stack" key={invitation.id}><h2>{invitation.businessName}</h2><p className="muted">{invitation.role.replaceAll("_", " ")}</p><button disabled={busy} onClick={() => accept(invitation)}>Accept invitation</button></div>)}</div>
      </section>}
    </>}
  </IdentityShell>;
}
