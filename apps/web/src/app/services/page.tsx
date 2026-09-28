import { PublicShell } from "@/components/public-shell";
import { SampleNotice, ServiceResults } from "@/components/marketplace";
export const metadata = { title: "Find services | FieldOps" };
export default async function Page({searchParams}: {searchParams: Promise<{q?: string; location?: string}>}) {
 const params = await searchParams;
 return <PublicShell><section className="container section"><div className="page-heading"><p className="kicker">Find your helping hand</p><h1>Good people. Useful services.</h1><p>Explore our sample directory and find the right kind of help.</p></div><SampleNotice /><ServiceResults initialQuery={typeof params.q === "string" ? params.q : ""} initialLocation={typeof params.location === "string" ? params.location : ""} /></section></PublicShell>;
}
