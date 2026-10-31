import { defineConfig, devices } from "@playwright/test";

/**
 * Cada test levanta su propio servidor Kotlin en un puerto libre (ver `tests/fixtures.ts`),
 * así cada caso juega en una sala limpia. Requiere haber corrido antes:
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
    trace: "retain-on-failure",
    // El juego es en español por defecto; sin esto Chromium arranca en en-US y
    // el cliente detecta inglés (los tests buscan textos en español).
    locale: "es-AR",
    // El tema "auto" sigue al sistema: fijamos oscuro para que el arranque sea el
    // de la mesa de noche (el claro se prueba explícitamente en pulido.spec).
    colorScheme: "dark",
  },
  projects: [{ name: "chromium", use: { ...devices["Pixel 7"] } }],
});
