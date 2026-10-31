import { test as base, expect } from "@playwright/test";
import { spawn, spawnSync } from "node:child_process";
import type { ChildProcessWithoutNullStreams } from "node:child_process";
import { existsSync } from "node:fs";
import { createServer } from "node:net";
import { resolve } from "node:path";

/**
 * Cada test levanta su propio servidor (y por lo tanto su propia sala) en un puerto libre.
 *
 * La sala es única por proceso: si dos tests comparten servidor, el segundo entra a una
 * partida ya empezada y recibe `PARTIDA_EN_CURSO`. Aislar por test cuesta ~1 s de JVM por
 * caso y evita tests que dependen del orden.
 */
export interface Servidor {
  url: string;
  puerto: number;
}

const raiz = resolve(import.meta.dirname, "../..");
const lib = resolve(raiz, "server/build/install/server/lib");

function asegurarServidorCompilado(): void {
  if (existsSync(lib)) return;
  console.log("Falta :server:installDist; compilando…");
  const esWindows = process.platform === "win32";
  const resultado = esWindows
    ? spawnSync("cmd.exe", ["/c", "gradlew.bat", ":server:installDist"], { cwd: raiz, stdio: "inherit" })
    : spawnSync(resolve(raiz, "gradlew"), [":server:installDist"], { cwd: raiz, stdio: "inherit" });
  if (resultado.status !== 0 || !existsSync(lib)) {
    throw new Error("No se pudo compilar el servidor. Corré: ./gradlew :server:installDist");
  }
}

async function puertoLibre(): Promise<number> {
  return new Promise((listo, fallo) => {
    const servidor = createServer();
    servidor.unref();
    servidor.on("error", fallo);
    servidor.listen(0, "127.0.0.1", () => {
      const direccion = servidor.address();
      const puerto = typeof direccion === "object" && direccion ? direccion.port : 0;
      servidor.close(() => listo(puerto));
    });
  });
}

interface Arranque {
  hijo: ChildProcessWithoutNullStreams;
  puerto: number;
}

/**
 * Espera a que *nuestro* proceso haya tomado el puerto. No alcanza con que `/api/info`
 * responda: puede ser un servidor anterior que todavía está cerrando y libera el puerto
 * un segundo después (nos pasó y el test quedaba "reconectando" para siempre).
 */
async function arrancar(): Promise<Arranque> {
  const puerto = await puertoLibre();
  // `java -cp "lib/*"` expande el wildcard solo: no hace falta pasar por el shell.
  const hijo = spawn("java", ["-cp", resolve(lib, "*"), "ar.truco.server.MainKt"], {
    cwd: raiz,
    env: { ...process.env, TRUCO_PUERTO: String(puerto), TRUCO_JUGADORES: "2" },
    stdio: ["ignore", "pipe", "pipe"],
  });

  let buffer = "";
  hijo.stdout.on("data", (dato: Buffer) => {
    buffer += dato.toString();
    if (process.env.TRUCO_E2E_DEBUG) console.log("[servidor]", dato.toString().trimEnd());
  });
  hijo.stderr.on("data", (dato: Buffer) => {
    buffer += dato.toString();
    if (process.env.TRUCO_E2E_DEBUG) console.log("[servidor!]", dato.toString().trimEnd());
  });

  const limite = Date.now() + 60_000;
  try {
    while (Date.now() < limite) {
      if (buffer.includes(`servidor en http://0.0.0.0:${puerto}`)) break;
      if (hijo.exitCode !== null) {
        throw new Error(`El servidor no pudo arrancar en :${puerto}.\n${buffer}`);
      }
      await new Promise((listo) => setTimeout(listo, 50));
    }

    // Y que además responda HTTP (el log sale antes de que el socket acepte).
    while (Date.now() < limite) {
      try {
        const respuesta = await fetch(`http://127.0.0.1:${puerto}/api/info`);
        if (respuesta.ok) return { hijo, puerto };
      } catch {
        /* todavía no escucha */
      }
      await new Promise((listo) => setTimeout(listo, 100));
    }
    throw new Error(`El servidor no respondió en 60 s.\n${buffer}`);
  } catch (error) {
    hijo.kill();
    throw error;
  }
}

async function detener(arranque: Arranque): Promise<void> {
  const { hijo } = arranque;
  if (hijo.exitCode !== null) return;
  hijo.kill();
  const limite = Date.now() + 5_000;
  while (hijo.exitCode === null && hijo.signalCode === null && Date.now() < limite) {
    await new Promise((listo) => setTimeout(listo, 50));
  }
  if (hijo.exitCode === null && hijo.signalCode === null) hijo.kill("SIGKILL");
}

export const test = base.extend<{ servidor: Servidor }>({
  // El cliente web ya tiene que estar compilado (`cd web && npm run build`).
  servidor: [
    async ({}, usar) => {
      asegurarServidorCompilado();
      if (!existsSync(resolve(raiz, "web/dist/index.html"))) {
        throw new Error("Falta web/dist. Corré antes: cd web && npm run build");
      }
      // Reintento por si el puerto elegido quedó tomado por otro proceso (raro, barato).
      let arranque: Arranque | null = null;
      for (let intento = 0; intento < 3 && !arranque; intento += 1) {
        try {
          arranque = await arrancar();
        } catch (error) {
          if (intento === 2) throw error;
          console.log(`Reintentando servidor: ${String(error).slice(0, 200)}`);
        }
      }
      try {
        await usar({ url: `http://127.0.0.1:${arranque!.puerto}`, puerto: arranque!.puerto });
      } finally {
        await detener(arranque!);
      }
    },
    { scope: "test" },
  ],

  baseURL: async ({ servidor }, usar) => {
    await usar(servidor.url);
  },
});

export { expect };
