import { AuthShell } from "@/components/auth-shell";
import { AccountForm } from "@/components/account-form";

export default function Onboarding() {
  return <AuthShell><AccountForm onboarding /></AuthShell>;
}
