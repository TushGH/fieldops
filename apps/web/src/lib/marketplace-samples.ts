// Fictional, public preview content. Never a fallback for authenticated API data.
export const categories = ["Plumbing", "Heating & cooling", "Electrical", "Cleaning", "Appliance repair"];
export const providers = [
  { slug: "oak-plumbing", name: "Oak Plumbing", category: "Plumbing", area: "North district", initials: "OP", tone: "blue", description: "Help with the everyday essentials, from a dripping tap to a kitchen plumbing project.", services: ["Leak inspection", "Tap and fixture repair", "Drain maintenance"] },
  { slug: "brightwire-electric", name: "Brightwire Electric", category: "Electrical", area: "Central district", initials: "BE", tone: "amber", description: "Thoughtful electrical work for the spaces you live and work in.", services: ["Lighting installation", "Outlet inspection", "Electrical troubleshooting"] },
  { slug: "northside-heating", name: "Northside Heating", category: "Heating & cooling", area: "North district", initials: "NH", tone: "green", description: "A more comfortable home starts with heating and cooling that works as it should.", services: ["Heating inspection", "Cooling maintenance", "Filter replacement"] },
  { slug: "clear-day-cleaning", name: "Clear Day Cleaning", category: "Cleaning", area: "Central district", initials: "CD", tone: "blue", description: "A fresh start for your space, with practical cleaning services for everyday life.", services: ["Home cleaning", "Move-out cleaning", "Office cleaning"] },
  { slug: "fixwell-appliances", name: "Fixwell Appliances", category: "Appliance repair", area: "South district", initials: "FA", tone: "green", description: "Get the appliances you rely on back into your daily routine.", services: ["Washer inspection", "Dishwasher repair", "Oven inspection"] },
];
export type Provider = typeof providers[number];
export type SampleJob = { id: string; title: string; customer: string; provider: string; assignee: string; date: string; time: string; status: string; address: string; notes: string };
export const sampleJobs: SampleJob[] = [
  { id: "DEMO-104", title: "Kitchen tap repair", customer: "Alex Morgan", provider: "Oak Plumbing", assignee: "Sam Taylor", date: "2026-09-28", time: "09:00–10:30", status: "In progress", address: "12 Example Lane", notes: "Inspect the persistent drip and check the fixture seals." },
  { id: "DEMO-105", title: "Bathroom leak inspection", customer: "Jamie Park", provider: "Oak Plumbing", assignee: "Sam Taylor", date: "2026-09-28", time: "13:00–14:30", status: "Scheduled", address: "24 Sample Street", notes: "Check the pipe connection beneath the bathroom sink." },
  { id: "DEMO-106", title: "Drain maintenance", customer: "Casey Reed", provider: "Oak Plumbing", assignee: "Unassigned", date: "2026-09-29", time: "Preferred morning", status: "Requested", address: "8 Example Court", notes: "Customer reports slow drainage. Confirm scope before scheduling." },
  { id: "DEMO-101", title: "Utility sink installation", customer: "Jordan Lee", provider: "Oak Plumbing", assignee: "Sam Taylor", date: "2026-09-25", time: "10:00–12:00", status: "Completed", address: "16 Sample Road", notes: "Fixture installed and tested; work area left clean." },
  { id: "DEMO-102", title: "Shower fixture repair", customer: "Taylor Quinn", provider: "Oak Plumbing", assignee: "Robin Ellis", date: "2026-09-23", time: "14:00–15:00", status: "Completed", address: "32 Example Avenue", notes: "Replaced worn seal and checked water flow." },
];
