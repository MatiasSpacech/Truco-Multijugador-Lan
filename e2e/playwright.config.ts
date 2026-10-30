import { defineConfig, devices } from "@playwright/test";

const PUERTO = 8123;

/**
 * Levanta el servidor Kotlin real (dist de :server) sirviendo el cliente compilado
 * de `web/dist`. Requiere haber corrido antes:
 *   ./gradlew :server:installDist
 *   cd web && npm run build
 */
export default defineConfig({
  testDir: "./tests",
  timeout: 60_000,
  fullyParallel: false,
  workers: 1,
  reporter: [["list"]],
  use: {
    baseURL: `http://127.0.0.1:${PUERTO}`,
    trace: "retain-on-failure",
  },
  projects: [{ name: "chromium", use: { ...devices["Pixel 7"] } }],
  webServer: {
    command: "node scripts/servidor.mjs",
    url: `http://127.0.0.1:${PUERTO}/api/info`,
    reuseExistingServer: false,
    timeout: 90_000,
  },
});
