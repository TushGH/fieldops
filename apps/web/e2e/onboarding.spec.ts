import { test, expect } from "@playwright/test";
import { randomUUID } from "node:crypto";

for (const mobile of [false, true]) {
  test(`owner can onboard, sign in, enter their business and sign out (${mobile ? "mobile" : "desktop"})`, async ({ page, context }) => {
    if (mobile) await page.setViewportSize({ width: 390, height: 844 });
    const id = randomUUID();
    const email = `browser-${id}@example.com`;
    const business = `Browser Test ${id.slice(0, 8)}`;
    const password = "browser test only passphrase";
    await page.goto("/onboarding");
    await page.getByLabel("Business name", { exact: true }).fill(business);
    await page.getByLabel("Business identifier").fill(`browser-${id}`);
    await page.getByLabel("Your name").fill("Browser Test Owner");
    await page.getByLabel("Email address").fill(email);
    await page.getByLabel("Password", { exact: true }).fill(password);
    await page.getByRole("button", { name: "Create your business" }).click();
    await expect(page).toHaveURL(/\/login\?created=1/);
    await expect(page.getByRole("status")).toContainText("Your business is ready");
    await page.getByLabel("Email address").fill(email);
    await page.getByLabel("Password", { exact: true }).fill("incorrect test password");
    await page.getByRole("button", { name: "Sign in", exact: true }).click();
    await expect(page.locator("form [role=alert]")).toContainText("email or password is incorrect");
    await page.getByLabel("Password", { exact: true }).fill(password);
    await page.getByRole("button", { name: "Sign in", exact: true }).click();
    await expect(page).toHaveURL(/\/workspace$/);
    await expect(page.getByRole("heading", { name: "Welcome, Browser Test Owner." })).toBeVisible();
    await page.getByRole("button", { name: new RegExp(business) }).click();
    await expect(page.getByRole("heading", { name: business, exact: true })).toBeVisible();
    await expect(page.getByText("Your business account is ready.")).toBeVisible();
    const cookie = (await context.cookies()).find(cookie => cookie.name === "FIELDOPS_SESSION");
    expect(cookie?.httpOnly).toBe(true);
    expect(cookie?.sameSite).toBe("Lax");
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true);
    await page.getByRole("button", { name: "Sign out" }).click();
    await expect(page).toHaveURL(/\/login$/);
    await page.goto("/workspace");
    await expect(page).toHaveURL(/\/login$/);
  });
}

test("business setup shows duplicate conflicts and client password validation", async ({ page }) => {
  const id = randomUUID();
  await page.goto("/onboarding");
  await page.getByLabel("Business name", { exact: true }).fill("Duplicate Test");
  await page.getByLabel("Business identifier").fill(`duplicate-${id}`);
  await page.getByLabel("Your name").fill("Test Owner");
  await page.getByLabel("Email address").fill(`duplicate-${id}@example.com`);
  await page.getByLabel("Password", { exact: true }).fill("short");
  await page.getByRole("button", { name: "Create your business" }).click();
  await expect(page.locator("form [role=alert]")).toContainText("at least 15 characters");
  await page.getByLabel("Password", { exact: true }).fill("browser test only passphrase");
  await page.getByRole("button", { name: "Create your business" }).click();
  await expect(page).toHaveURL(/\/login\?created=1/);
  await page.goto("/onboarding");
  await page.getByLabel("Business name", { exact: true }).fill("Another Business");
  await page.getByLabel("Business identifier").fill(`duplicate-${id}`);
  await page.getByLabel("Your name").fill("Test Owner");
  await page.getByLabel("Email address").fill(`other-${id}@example.com`);
  await page.getByLabel("Password", { exact: true }).fill("browser test only passphrase");
  await page.getByRole("button", { name: "Create your business" }).click();
  await expect(page.locator("form [role=alert]")).toContainText("couldn’t create a business");
});
