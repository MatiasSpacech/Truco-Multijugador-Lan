import { spawn, spawnSync } from "node:child_process";
import { existsSync } from "node:fs";
import { resolve } from "node:path";

/**
 * Arranca el servidor para los tests E2E.
 * Si falta `:server:installDist` lo compila; si falta `web/dist` avisa y sale.
 */
const raiz = resolve(import.meta.dirname, "../..");
const esWindows = process.platform === "win32";
const binRel = `server/build/install/server/bin/${esWindows ? "server.bat" : "server"}`;
const binAbs = resolve(raiz, binRel);
const cliente = resolve(raiz, "web/dist/index.html");

if (!existsSync(cliente)) {
  console.error("Falta web/dist. Corré antes: cd web && npm install && npm run build");
  process.exit(1);
}

if (!existsSync(binAbs)) {
  console.log("No está :server:installDist; compilando…");
  const compilar = esWindows
    ? spawnSync("cmd.exe", ["/c", "gradlew.bat", ":server:installDist"], { cwd: raiz, stdio: "inherit" })
    : spawnSync(resolve(raiz, "gradlew"), [":server:installDist"], { cwd: raiz, stdio: "inherit" });

  if (compilar.status !== 0 || !existsSync(binAbs)) {
    console.error("No se pudo compilar el servidor. Corré: ./gradlew :server:installDist");
    process.exit(1);
  }
}

const [programa, argumentos] = esWindows
  ? ["cmd.exe", ["/c", binRel.replaceAll("/", "\\")]]
  : [binAbs, []];

const hijo = spawn(programa, argumentos, {
  cwd: raiz,
  env: {
    ...process.env,
    TRUCO_PUERTO: "8123",
    TRUCO_JUGADORES: "2",
  },
  stdio: ["ignore", "inherit", "inherit"],
});

const cerrar = () => {
  if (!hijo.killed) hijo.kill();
  process.exit(0);
};

process.on("SIGINT", cerrar);
process.on("SIGTERM", cerrar);
hijo.on("exit", (codigo) => process.exit(codigo ?? 0));
