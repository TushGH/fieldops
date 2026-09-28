import { Heading, IdentityShell } from "@/components/identity-shell";
export default function Home() {
  return <IdentityShell><div className="hero"><Heading eyebrow="Built for the work ahead" title="Great service starts with a connected team.">One account for the businesses you own and the teams you work with. Create your business or join an invitation.</Heading><div className="actions"><a className="button" href="/signup?intent=create-business">Create a business</a><a className="button secondary" href="/login">Sign in</a></div></div></IdentityShell>;
}
