import Link from "next/link";

export function AuthShell({ children }: { children: React.ReactNode }) {
  return <main className="auth-layout">
    <section className="brand-panel">
      <Link className="brand" href="/"><span className="brand-icon">F<span>↗</span></span>FieldOps</Link>
      <div className="brand-story">
        <p className="eyebrow">BUILT FOR THE PEOPLE WHO SHOW UP</p>
        <h1>Good work.<br />Great service.<br /><span>One home base.</span></h1>
        <p>A place for your business to get started, and your team to belong.</p>
        <div className="brand-line" aria-hidden="true"><span>01</span><i /><span>YOUR NEXT CHAPTER</span></div>
      </div>
      <p className="brand-foot">For the businesses that keep our communities running.</p>
    </section>
    <section className="form-panel">{children}</section>
  </main>;
}
