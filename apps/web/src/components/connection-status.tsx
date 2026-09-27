"use client";

import { useEffect, useState } from "react";

type Status = "checking" | "connected" | "unavailable";

export function ConnectionStatus() {
  const [status, setStatus] = useState<Status>("checking");
  const [attempt, setAttempt] = useState(0);

  useEffect(() => {
    let active = true;
    const controller = new AbortController();
    const timeout = setTimeout(() => controller.abort(), 7000);

    async function checkConnection() {
      try {
        const response = await fetch("/api/health", {
          cache: "no-store",
          signal: controller.signal,
        });
        const data = await response.json();
        if (!response.ok || data.application !== "fieldops-api" || data.status !== "UP") {
          throw new Error("Unexpected health response");
        }
        if (active) setStatus("connected");
      } catch {
        if (active) setStatus("unavailable");
      } finally {
        clearTimeout(timeout);
      }
    }

    void checkConnection();
    return () => {
      active = false;
      clearTimeout(timeout);
      controller.abort();
    };
  }, [attempt]);

  const connected = status === "connected";
  const checking = status === "checking";

  return (
    <section aria-labelledby="connection-heading" className="rounded-2xl border border-emerald-950/10 bg-white p-7 shadow-sm sm:p-9">
      <p className="text-xs font-semibold tracking-widest text-slate-500 uppercase">Development environment</p>
      <h2 id="connection-heading" className="mt-4 text-2xl font-semibold tracking-tight">A connected foundation.</h2>
      <p className="mt-3 text-sm leading-6 text-slate-600">Check the connection between this web application and the FieldOps API.</p>

      <div role="status" aria-live="polite" aria-atomic="true" className="my-7 flex items-center gap-3 rounded-xl bg-slate-50 p-4">
        <span aria-hidden="true" className={`size-2.5 shrink-0 rounded-full ${connected ? "bg-emerald-600" : checking ? "bg-slate-400" : "bg-amber-600"}`} />
        <div>
          <p className="text-sm font-semibold">{checking ? "Checking connection…" : connected ? "API connected" : "API unavailable"}</p>
          <p className="mt-1 text-xs leading-5 text-slate-500">
            {checking ? "Waiting for the backend health response." : connected ? "The application health endpoint is responding." : "Start the backend, check its URL, and try again."}
          </p>
        </div>
      </div>

      <button
        type="button"
        disabled={checking}
        onClick={() => {
          setStatus("checking");
          setAttempt((value) => value + 1);
        }}
        className="w-full rounded-lg bg-emerald-950 px-5 py-3 text-sm font-semibold text-white transition-colors hover:bg-emerald-800 focus-visible:outline-2 focus-visible:outline-offset-4 focus-visible:outline-emerald-700 disabled:opacity-60"
      >
        {checking ? "Checking…" : "Check connection"}
      </button>
      <p className="mt-4 text-xs leading-5 text-slate-500">This checks application reachability. Database health is reported separately by backend Actuator.</p>
    </section>
  );
}
