import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";

/**
 * En desarrollo: `npm run dev` con proxy a la PC (`./gradlew :server:run`).
 * En producción: `npm run build` deja `web/dist`, que el servidor Ktor sirve como assets.
 */
export default defineConfig({
  plugins: [react()],
  server: {
    host: true,
    port: 5173,
    proxy: {
      "/api": "http://localhost:8080",
      "/ws": { target: "ws://localhost:8080", ws: true },
    },
  },
  build: {
    outDir: "dist",
    assetsDir: "assets",
    sourcemap: false,
  },
});
