export function TradeIcon({ category, size = 28 }: { category: string; size?: number }) {
  const paths: Record<string, React.ReactNode> = {
    Plumbing: <><path d="M8 3v8h8V3M8 7h8M12 15v6M8 11a4 4 0 0 0 8 0" /><path d="M3 18h5m8 0h5" /></>,
    "Heating & cooling": <><path d="M12 2v20M3.4 7l17.2 10M3.4 17 20.6 7M8 4l4 3 4-3M8 20l4-3 4 3" /></>,
    Electrical: <path d="m14 2-9 12h6l-1 8 9-12h-6z" />,
    Cleaning: <><path d="m15 3-5 11M7 12l7 3-3 7-8-3zM19 6v6m-3-3h6" /></>,
    "Appliance repair": <><rect x="4" y="2" width="16" height="20" rx="2" /><circle cx="12" cy="14" r="5" /><path d="M4 7h16M7 4.5h1m3 0h1" /></>,
  };
  return <svg width={size} height={size} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.6" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">{paths[category] ?? <path d="M3 10 12 3l9 7v11H3zM9 21v-8h6v8" />}</svg>;
}
