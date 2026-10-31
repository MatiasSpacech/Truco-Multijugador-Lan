import { defineConfig } from "vitest/config";

/**
 * Tests unitarios del cliente (los flujos completos van por Playwright en `e2e/`).
 * El entorno es `node`: los módulos puros (i18n, diagnóstico, preferencias) no necesitan DOM.
 */
export default defineConfig({
  test: {
    environment: "node",
    include: ["src/**/*.test.ts"],
  },
});
