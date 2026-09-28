import { BusinessExperience } from "@/components/business-experience";
export default async function Page({ params }: { params: Promise<{ tenantId: string }> }) {
 const { tenantId } = await params;
 return <BusinessExperience view="workspace" tenant={tenantId} key={tenantId} />;
}
