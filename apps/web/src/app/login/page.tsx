import { AuthShell } from "@/components/auth-shell";
import { AccountForm } from "@/components/account-form";

export default async function Login({ searchParams }: { searchParams: Promise<{ created?: string }> }) {
  return <AuthShell><AccountForm created={(await searchParams).created === "1"} /></AuthShell>;
}
