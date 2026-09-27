import { ConnectionStatus } from "@/components/connection-status";
import Link from "next/link";

export default function Home() {
  return (
    <div className="mx-auto flex min-h-screen max-w-6xl flex-col px-6 sm:px-10">
      <header className="flex items-center justify-between border-b border-emerald-950/15 py-7">
        <Link href="/" className="flex items-center gap-3 text-xl font-bold tracking-tight" aria-label="FieldOps home">
          <span aria-hidden="true" className="grid size-9 place-items-center rounded-lg bg-emerald-950 text-sm text-lime-200">FO</span>
          FieldOps<span className="text-emerald-700">.</span>
        </Link>
        <span className="rounded-full border border-emerald-950/15 px-3 py-1 text-xs font-medium text-emerald-900">In development</span>
      </header>

      <main className="flex flex-1 flex-col justify-center py-16 sm:py-24">
        <div className="grid items-start gap-12 lg:grid-cols-[1.25fr_1fr] lg:gap-20">
          <section aria-labelledby="welcome-heading">
            <p className="mb-6 text-xs font-bold tracking-[0.2em] text-emerald-700 uppercase">Built for the work ahead</p>
            <h1 id="welcome-heading" className="max-w-xl text-5xl leading-[1.08] font-semibold tracking-tight sm:text-6xl">
              Great service starts with a clear plan.
            </h1>
            <p className="mt-7 max-w-lg text-lg leading-8 text-slate-600">
              FieldOps is taking shape: one place for service businesses to coordinate their people, their work, and their next visit.
            </p>
            <div className="mt-10 border-l-2 border-emerald-700 pl-5">
              <p className="text-sm font-semibold">Phase 1 / Application foundation</p>
              <p className="mt-2 max-w-md text-sm leading-6 text-slate-600">
                The foundation is here. Business workflows will arrive in future phases.
              </p>
            </div>
          </section>

          <ConnectionStatus />
        </div>
      </main>

      <footer className="flex flex-wrap justify-between gap-3 border-t border-emerald-950/15 py-6 text-xs text-slate-500">
        <span>FieldOps · Field service, thoughtfully organized.</span>
        <span>Application foundation · v0.1</span>
      </footer>
    </div>
  );
}
