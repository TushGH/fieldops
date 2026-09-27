import { defineConfig, devices } from "@playwright/test";

// Runs against explicitly started local servers. Uses unique test-only businesses.
export default defineConfig({
  testDir: "./e2e",
  workers: 1,
  use: {
    baseURL: process.env.E2E_BASE_URL ?? "http://127.0.0.1:3000",
    trace: "off", // Do not write credentials/session state into test artifacts.
    ...devices["Desktop Chrome"],
    channel: "chrome",
  },
});
