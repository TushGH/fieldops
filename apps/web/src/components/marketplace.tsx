"use client";
import Link from "next/link";
import { useState } from "react";
import { categories, providers, type Provider } from "@/lib/marketplace-samples";
import { TradeIcon } from "./trade-icon";
export function SearchForm({ query = "", location = "" }: { query?: string; location?: string }) {
  return <form className="service-search" action="/services"><label>What do you need?<input name="q" defaultValue={query} placeholder="Try plumbing or cleaning" /></label><label>Where?<input name="location" defaultValue={location} placeholder="Search sample districts" /></label><button type="submit">Find services <span aria-hidden="true">↗</span></button></form>;
}
export function SampleNotice() { return <p className="preview-notice"><span className="status-dot" />Sample providers — bookings are not available yet.</p>; }
export function ProviderCard({ provider }: { provider: Provider }) {
  return <article className="provider-card"><div className={`provider-art ${provider.tone}`}><TradeIcon category={provider.category} size={64} /><span>{provider.category}</span><span className="sample-stamp">Sample business</span></div><div className="provider-body"><span className="hint">{provider.area}</span><h3><Link href={`/services/${provider.slug}`}>{provider.name}</Link></h3><p>{provider.description}</p><Link className="text-link" href={`/services/${provider.slug}`}>Explore services <span aria-hidden="true">↗</span></Link></div></article>;
}
export function ServiceResults({ initialQuery, initialLocation }: { initialQuery: string; initialLocation: string }) {
  const [category, setCategory] = useState(""); const [sort, setSort] = useState("name");
  const filtered = providers.filter(p => (!category || p.category === category) && `${p.name} ${p.category} ${p.services.join(" ")}`.toLowerCase().includes(initialQuery.trim().toLowerCase()) && p.area.toLowerCase().includes(initialLocation.trim().toLowerCase())).sort((a,b) => sort === "category" ? a.category.localeCompare(b.category) : a.name.localeCompare(b.name));
  return <><SearchForm query={initialQuery} location={initialLocation} /><div className="results-toolbar"><label>Service category<select value={category} onChange={e=>setCategory(e.target.value)}><option value="">All services</option>{categories.map(c=><option key={c}>{c}</option>)}</select></label><p role="status">{filtered.length} sample {filtered.length === 1 ? "business" : "businesses"}</p><label>Sort by<select value={sort} onChange={e=>setSort(e.target.value)}><option value="name">Business name</option><option value="category">Service category</option></select></label><Link href="/services" onClick={()=>{setCategory("");setSort("name");}}>Clear filters</Link></div>{filtered.length ? <div className="provider-grid">{filtered.map(p=><ProviderCard key={p.slug} provider={p} />)}</div> : <div className="empty-state"><TradeIcon category="Search" size={40} /><h2>No sample businesses match</h2><p>Try another service, or leave the location blank to explore all sample districts.</p><Link className="button secondary" href="/services">View all services</Link></div>}</>;
}
