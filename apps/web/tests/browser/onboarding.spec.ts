import { test, expect, type Page } from "@playwright/test";
const one = "11111111-1111-4111-8111-111111111111";
const two = "22222222-2222-4222-8222-222222222222";
const invitationId = "33333333-3333-4333-8333-333333333333";
const owner = { id: "44444444-4444-4444-8444-444444444444", displayName: "Robin", email: "robin@example.com", emailVerifiedAt: "2026-09-27T12:00:00Z", platformAdmin: false };
const business = (id: string) => ({ id, name: id === one ? "Robin Plumbing" : "Another Business", slug: "robin-plumbing", role: "BUSINESS_OWNER" });
const invitation = { id: invitationId, tenantId: two, businessName: "Another Business", email: owner.email, role: "TECHNICIAN", status: "PENDING", expiresAt: "2030-01-01T00:00:00Z" };
async function backend(page: Page, options: { businesses?: string[]; signedIn?: boolean; verified?: boolean; wrongAccount?: boolean; incomplete?: boolean } = {}) {
  let signedIn = options.signedIn ?? false;
  let verified = options.verified ?? true;
  let complete = !options.incomplete;
  let failedLogin = false;
  const calls: string[] = [];
  await page.route("**/api/backend/**", async route => {
    const request = route.request(); const path = new URL(request.url()).pathname.replace("/api/backend/", "");
    calls.push(`${request.method()} ${path}`);
    const reply = (body: unknown, status = 200) => route.fulfill({ status, contentType: "application/json", body: JSON.stringify(body) });
    if (path === "auth/csrf") return reply({ headerName: "X-CSRF-TOKEN", token: "test-token" });
    if (path === "auth/login") {
      if (request.postData()?.includes("wrong")) { failedLogin = true; return reply({ message: "Invalid credentials." }, 401); }
      signedIn = true; return route.fulfill({ status: 204 });
    }
    if (path === "auth/logout") { signedIn = false; return route.fulfill({ status: 204 }); }
    if (path === "auth/signup") return route.fulfill({ status: 202 });
    if (path === "auth/signup/complete") { verified = true; return route.fulfill({ status: 204 }); }
    if (path === "invitations/resolve") return reply({ id: invitationId });
    if (!signedIn) return reply({ message: "Sign in required." }, 401);
    if (path === "auth/me") return reply({ ...owner, emailVerifiedAt: verified ? owner.emailVerifiedAt : null });
    if (path === "auth/email-verification/confirm") { verified = true; return route.fulfill({ status: 204 }); }
    if (path === "auth/email-verification/request") return route.fulfill({ status: 202 });
    if (path === "businesses") {
      if (request.method() === "POST") return reply(business(one), 201);
      return reply((options.businesses ?? [one]).map(business));
    }
    if (path === "invitations") return reply([]);
    if (path === `invitations/${invitationId}`) return options.wrongAccount ? reply({ message: "Invitation does not match your account." }, 404) : reply(invitation);
    if (path === `invitations/${invitationId}/accept`) return reply({ tenantId: two });
    if (path === "tenant/context") return reply({ role: "BUSINESS_OWNER" });
    if (path === "tenant") return reply(business(request.headers()["x-tenant-id"]));
    if (path === "tenant/onboarding/business/complete") { complete = true; return reply({ businessComplete: true, businessSetupRequired: false }); }
    if (path === "tenant/onboarding") return reply({ businessComplete: complete, businessSetupRequired: !complete });
    if (path === "tenant/invitations") return reply([]);
    return reply({ message: `Unexpected request ${path}` }, 500);
  });
  return { calls, failedLogin: () => failedLogin };
}
async function login(page: Page) {
  await page.getByLabel("Email address").fill(owner.email);
  await page.getByLabel("Password", { exact: true }).fill("a sufficiently long password");
  await page.getByRole("button", { name: "Sign in", exact: true }).click();
}

test("one business is selected automatically and refresh revalidates the context", async ({ page }) => {
  const mock = await backend(page);
  await page.goto("/login"); await login(page);
  await expect(page).toHaveURL(`/app/${one}`);
  await expect(page.getByRole("heading", { name: "Robin Plumbing" })).toBeVisible();
  await page.reload(); await expect(page.getByRole("heading", { name: "Robin Plumbing" })).toBeVisible();
  expect(mock.calls.filter(call => call === "GET tenant/context").length).toBeGreaterThanOrEqual(2);
});
test("multiple businesses show selection and switching keeps authentication", async ({ page }) => {
  await backend(page, { businesses: [one, two] });
  await page.goto("/login"); await login(page);
  await expect(page).toHaveURL("/select-business");
  await page.getByRole("link", { name: /Robin Plumbing/ }).click();
  await expect(page).toHaveURL(`/app/${one}`);
  await page.getByRole("link", { name: "Switch business" }).click();
  await page.getByRole("link", { name: /Another Business/ }).click();
  await expect(page).toHaveURL(`/app/${two}`);
  await page.getByRole("button", { name: "Sign out" }).click();
  await expect(page).toHaveURL("/login");
  expect(await page.evaluate(() => Object.keys(sessionStorage).filter(key => key.startsWith("fieldops:")))).toEqual([]);
});
test("invitation intent survives a failed login and takes precedence over an existing business", async ({ page }) => {
  const mock = await backend(page);
  await page.goto("/invitations/accept#token=opaque-token");
  await expect(page).toHaveURL("/login");
  await page.getByLabel("Email address").fill(owner.email);
  await page.getByLabel("Password", { exact: true }).fill("wrong password");
  await page.getByRole("button", { name: "Sign in", exact: true }).click();
  await expect(page.locator("main").getByRole("alert")).toContainText("Invalid credentials");
  await login(page);
  await expect(page).toHaveURL(`/invitations/accept?id=${invitationId}`);
  await page.getByRole("button", { name: "Accept invitation" }).click();
  await expect(page).toHaveURL(`/app/${two}`);
  expect(mock.failedLogin()).toBeTruthy();
  expect(await page.evaluate(() => sessionStorage.getItem("fieldops:intent"))).toBeNull();
});
test("wrong account cannot see an acceptance action", async ({ page }) => {
  await backend(page, { signedIn: true, wrongAccount: true });
  await page.goto(`/invitations/accept?id=${invitationId}`);
  await expect(page.locator("main").getByRole("alert")).toContainText("does not match");
  await expect(page.getByRole("button", { name: "Accept invitation" })).toHaveCount(0);
});
test("zero businesses offers creation and owner can complete setup without inviting a teammate", async ({ page }) => {
  await backend(page, { businesses: [], incomplete: true });
  await page.goto("/login"); await login(page);
  await page.getByRole("link", { name: "Create another business" }).click();
  await page.getByLabel("Business name").fill("Robin Plumbing"); await page.getByLabel("Business slug").fill("robin-plumbing");
  await page.getByRole("button", { name: "Create business", exact: true }).click();
  await expect(page).toHaveURL(`/app/${one}/onboarding/business`);
  await page.getByRole("button", { name: "Confirm business and continue" }).click();
  await expect(page).toHaveURL(`/app/${one}`);
  await expect(page.getByText("Business setup completed.")).toBeVisible();
});
test("signup continuation and password link work on a mobile viewport without retaining the token", async ({ page }) => {
  await page.setViewportSize({ width: 390, height: 844 });
  await backend(page, { businesses: [] });
  await page.goto("/signup?intent=create-business");
  await page.getByLabel("Your name").fill("Robin"); await page.getByLabel("Email address").fill(owner.email);
  await page.getByRole("button", { name: "Send verification email" }).click();
  await expect(page.getByRole("status")).toContainText("Check your email");
  await page.goto("/signup/complete#token=opaque-token");
  await expect(page).toHaveURL("/signup/complete");
  await page.getByLabel("Password", { exact: true }).fill("a sufficiently long password");
  await page.getByRole("button", { name: "Create account", exact: true }).click();
  await page.getByRole("link", { name: "Continue to sign in" }).click(); await login(page);
  await expect(page).toHaveURL("/create-business");
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBeTruthy();
});
test("unverified legacy account routes to verification and explicitly confirms its link", async ({ page }) => {
  const mock = await backend(page, { verified: false });
  await page.goto("/login"); await login(page); await expect(page).toHaveURL("/verify-email");
  await page.goto("/verify-email#token=opaque-token");
  await expect(page.getByRole("button", { name: "Confirm email" })).toBeVisible();
  expect(mock.calls).not.toContain("POST auth/email-verification/confirm");
  await page.getByRole("button", { name: "Confirm email" }).click();
  await expect(page).toHaveURL(`/app/${one}`);
});

test("restores only an eligible user preference and keeps selections independent between tabs", async ({ page, context }) => {
  await backend(page, { businesses: [one, two], signedIn: true });
  await page.goto("/select-business");
  await page.evaluate(({ user, tenant }) => sessionStorage.setItem(`fieldops:tenant:${user}`, tenant), { user: owner.id, tenant: two });
  await page.goto("/workspace"); await expect(page).toHaveURL(`/app/${two}`);
  const otherTab = await context.newPage();
  await backend(otherTab, { businesses: [one, two], signedIn: true });
  await otherTab.goto(`/app/${one}`);
  await expect(otherTab.getByRole("heading", { name: "Robin Plumbing" })).toBeVisible();
  await expect(page).toHaveURL(`/app/${two}`);
  expect(await page.evaluate(user => sessionStorage.getItem(`fieldops:tenant:${user}`), owner.id)).toBe(two);
  expect(await otherTab.evaluate(user => sessionStorage.getItem(`fieldops:tenant:${user}`), owner.id)).toBe(one);
  await otherTab.evaluate(user => sessionStorage.setItem(`fieldops:tenant:${user}`, "unrelated-tenant"), owner.id);
  await otherTab.goto("/workspace"); await expect(otherTab).toHaveURL("/select-business");
});

test("expired invitation link shows recovery without accepting or disclosing details", async ({ page }) => {
  await backend(page);
  await page.route("**/api/backend/invitations/resolve", route => route.fulfill({ status: 400, contentType: "application/json", body: JSON.stringify({ message: "This invitation is unavailable. Sign in to check your invitations." }) }));
  await page.goto("/invitations/accept#token=expired-token");
  await expect(page.locator("main").getByRole("alert")).toContainText("unavailable");
  await expect(page.getByRole("button", { name: "Accept invitation" })).toHaveCount(0);
  await expect(page.getByRole("link", { name: "Check invitations" })).toBeVisible();
});
