import Link from "next/link";
import { notFound } from "next/navigation";
import { PublicShell } from "@/components/public-shell";
import { SampleNotice } from "@/components/marketplace";
import { TradeIcon } from "@/components/trade-icon";
import { providers } from "@/lib/marketplace-samples";
export default async function Page({params}: {params: Promise<{slug: string}>}) {
 const {slug}=await params; const provider=providers.find(p=>p.slug===slug); if(!provider) notFound();
 return <PublicShell><section className="container section"><Link href="/services" className="text-link">Back to services</Link><SampleNotice /><div className="provider-detail"><div><div className={`provider-avatar ${provider.tone}`}><TradeIcon category={provider.category} size={44}/></div><p className="kicker">{provider.category} · {provider.area}</p><h1>{provider.name}</h1><p className="lead">{provider.description}</p><h2>Services at a glance</h2><ul className="service-list">{provider.services.map(s=><li key={s}><span aria-hidden="true">✓</span>{s}</li>)}</ul><h2>Service area</h2><p>{provider.area} is an illustrative area in our sample directory, not a confirmed launch location.</p></div><aside className="panel stack"><h2>See how a request works</h2><p>Walk through a sample service request. No appointment is booked and no business is contacted.</p><Link className="button" href={`/my/requests/new?provider=${provider.slug}`}>Preview a service request</Link><p className="hint">Sign in to continue to your personal space. Use example details in the preview.</p></aside></div></section></PublicShell>;
}
