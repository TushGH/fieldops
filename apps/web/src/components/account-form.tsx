"use client";

import Link from "next/link";
import { useState, type FormEvent } from "react";
import { useRouter } from "next/navigation";
import { submit } from "@/lib/api";

export function AccountForm({ onboarding = false, created = false }: { onboarding?: boolean; created?: boolean }) {
  const router = useRouter();
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const [visible, setVisible] = useState(false);

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (busy) return;
    const form = event.currentTarget;
    const data = new FormData(form);
    const password = String(data.get("password"));
    if (onboarding && (Array.from(password).length < 15 || new TextEncoder().encode(password).length > 72)) {
      setError("Choose a password with at least 15 characters and at most 72 UTF-8 bytes.");
      return;
    }
    setBusy(true);
    setError("");
    try {
      const response = onboarding
        ? await submit("onboarding", JSON.stringify(Object.fromEntries(data)), "application/json")
        : await submit("auth/login", new URLSearchParams({ email: String(data.get("email")), password }), "application/x-www-form-urlencoded");
      if (!response.ok) {
        const message = response.status === 409
          ? "We couldn’t create a business with those details. Try another business identifier, or sign in if you already have an account."
          : response.status === 401 ? "The email or password is incorrect. Please try again."
          : response.status === 400 ? "Check your details, including your email and business identifier."
          : response.status === 403 ? "Your session could not be verified. Please try again."
          : "We couldn’t complete your request. Please try again. If you submitted business setup, try signing in first.";
        throw new Error(message);
      }
      form.reset();
      // Reset the form before navigation so credentials do not survive in client state.
      if (onboarding) router.replace("/login?created=1");
      else { router.replace("/workspace"); router.refresh(); }
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : "Something went wrong. Please try again.");
      setBusy(false);
    }
  }

  return <div className="form-content">
    <p className="eyebrow">{onboarding ? "LET’S GET YOU SET UP" : "YOUR BUSINESS STARTS HERE"}</p>
    <h2>{onboarding ? "Make it your business." : "Welcome back."}</h2>
    <p className="muted">{onboarding ? "Create your business and your owner account. All in one step." : "Sign in to your FieldOps account."}</p>
    {created && <p className="notice success" role="status">Your business is ready. Sign in with your new account to continue.</p>}
    <form onSubmit={handleSubmit} className="account-form">
      <fieldset disabled={busy}>
        {onboarding && <>
          <label htmlFor="businessName">Business name</label>
          <input id="businessName" name="businessName" autoComplete="organization" maxLength={200} placeholder="e.g. Oak & Pine Plumbing" required />
          <label htmlFor="slug">Business identifier</label>
          <input id="slug" name="slug" autoCapitalize="none" spellCheck={false} minLength={3} maxLength={63}
            pattern="[a-z0-9]+(-[a-z0-9]+)*" placeholder="oak-pine-plumbing" aria-describedby="slug-hint" required />
          <p className="field-hint" id="slug-hint">A unique, permanent identifier. Use 3–63 lowercase letters, numbers, or single hyphens between words.</p>
          <label htmlFor="ownerName">Your name</label>
          <input id="ownerName" name="ownerName" autoComplete="name" maxLength={200} placeholder="Full name" required />
        </>}
        <label htmlFor="email">Email address</label>
        <input id="email" name="email" type="email" autoComplete="username" autoCapitalize="none" maxLength={254} placeholder="you@yourbusiness.com" required />
        <label htmlFor="password">Password</label>
        <div className="password-field">
          <input id="password" name="password" type={visible ? "text" : "password"} autoComplete={onboarding ? "new-password" : "current-password"}
            aria-describedby={onboarding ? "password-hint" : undefined} required />
          <button type="button" aria-label={visible ? "Hide password" : "Show password"} aria-pressed={visible} onClick={() => setVisible(!visible)}>{visible ? "Hide" : "Show"}</button>
        </div>
        {onboarding && <p className="field-hint" id="password-hint">At least 15 characters. A memorable passphrase works well. Maximum 72 UTF-8 bytes.</p>}
        {error && <p className="notice error" role="alert">{error}</p>}
        <button className="primary-button" type="submit">{busy ? "Please wait…" : onboarding ? "Create your business" : "Sign in"}<span aria-hidden="true">↗</span></button>
      </fieldset>
    </form>
    <p className="form-switch">{onboarding ? "Already have an account?" : "New to FieldOps?"} <Link href={onboarding ? "/login" : "/onboarding"}>{onboarding ? "Sign in" : "Create your business"}</Link></p>
  </div>;
}
