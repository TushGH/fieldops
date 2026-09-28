"use client";

import { useEffect, useRef, useState, type FormEvent } from "react";
import { api, destination, setIntent, type User } from "@/lib/identity-api";
import { Heading, IdentityShell } from "./identity-shell";

type Mode = "login" | "signup" | "complete" | "verify";
export function AccountForm({ mode }: { mode: Mode }) {
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const [notice, setNotice] = useState("");
  const [hasToken, setHasToken] = useState(false);
  const token = useRef("");
  useEffect(() => {
    function captureFragment() {
      const incoming = new URLSearchParams(window.location.hash.slice(1)).get("token");
      if (incoming) {
        token.current = incoming;
        setHasToken(true);
        history.replaceState(null, "", window.location.pathname + window.location.search);
      }
    }
    // Capture browser-only input after hydration and when a mail link targets this already-open page.
    captureFragment();
    window.addEventListener("hashchange", captureFragment);
    if (new URLSearchParams(window.location.search).get("intent") === "create-business") setIntent("create-business");
    return () => window.removeEventListener("hashchange", captureFragment);
  }, []);
  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault(); setBusy(true); setError("");
    const data = Object.fromEntries(new FormData(event.currentTarget));
    try {
      if (mode === "login") {
        await api("auth/login", { method: "POST", data, form: true });
        window.location.assign(await destination(await api<User>("auth/me")));
      } else if (mode === "signup") {
        await api("auth/signup", { method: "POST", data });
        setNotice("Check your email for a link to choose your password. If you already have an account, sign in instead.");
      } else if (mode === "complete") {
        await api("auth/signup/complete", { method: "POST", data: { token: token.current, password: data.password } });
        token.current = ""; setNotice("Your account is ready. Sign in to continue.");
      } else {
        await api(`auth/email-verification/${hasToken ? "confirm" : "request"}`, {
          method: "POST", data: hasToken ? { token: token.current } : undefined,
        });
        if (hasToken) window.location.assign(await destination(await api<User>("auth/me")));
        else setNotice("Check your email for a verification link. Open it while signed in to this account.");
      }
    } catch (error) { setError(error instanceof Error ? error.message : "Please try again."); }
    finally { setBusy(false); }
  }
  const title = { login: "Welcome back.", signup: "One account. Every business.", complete: "Choose your password.", verify: "Verify your email." }[mode];
  return <IdentityShell signedIn={mode === "verify"}><div className="account-grid">
    <Heading eyebrow="Your FieldOps account" title={title}>
      {mode === "signup" ? "Create your account first. Then start a business or join a team that invited you." : mode === "verify" ? "Confirm your email address before creating or entering a business." : "Your account connects you to the service businesses you work with."}
    </Heading>
    <section className="panel">
      {error && <p role="alert" className="error">{error}</p>}
      {notice ? <div role="status"><p>{notice}</p><a className="button mt-6" href="/login">Continue to sign in</a><button className="secondary mt-6" onClick={() => setNotice("")}>Back</button></div>
        : <form onSubmit={submit} className="stack">
          {mode === "signup" && <label>Your name<input name="displayName" autoComplete="name" required maxLength={200} /></label>}
          {(mode === "signup" || mode === "login") && <label>Email address<input name="email" type="email" autoComplete="email" required maxLength={254} /></label>}
          {(mode === "login" || mode === "complete") && <label>Password<input name="password" type="password" autoComplete={mode === "login" ? "current-password" : "new-password"} required minLength={mode === "complete" ? 15 : undefined} aria-describedby={mode === "complete" ? "password-hint" : undefined} /></label>}
          {mode === "complete" && <p id="password-hint" className="hint">At least 15 characters; at most 72 UTF-8 bytes.</p>}
          {mode === "complete" && !hasToken && <p className="muted">Open the link from your email. If it expired, request another from the signup page.</p>}
          <button disabled={busy || (mode === "complete" && !hasToken)}>{busy ? "Please wait…" : { login: "Sign in", signup: "Send verification email", complete: "Create account", verify: hasToken ? "Confirm email" : "Send verification email" }[mode]}</button>
          {mode === "login" && <p className="muted">New to FieldOps? <a href="/signup">Create an account</a></p>}
          {mode === "signup" && <p className="muted">Already registered? <a href="/login">Sign in</a></p>}
          {mode === "complete" && <a href="/signup">Request another link</a>}
          {mode === "verify" && <p className="hint">Using the wrong account? Sign out, sign in with the address that received the link, then reopen the email.</p>}
        </form>}
    </section>
  </div></IdentityShell>;
}
