import { chromium, devices } from "@playwright/test";
import { resolve } from "node:path";

/**
 * Capturas para revisar el diseño a ojo (no forma parte de los tests):
 *   TRUCO_JUGADORES=2 ./gradlew :server:run &   # o el servidor de la app
 *   TRUCO_URL=http://127.0.0.1:8080 node scripts/captura.mjs
 *
 * `TRUCO_TEMA=claro` arranca en tema claro (por defecto oscuro).
 */
const base = process.env.TRUCO_URL ?? "http://127.0.0.1:8123";
const salida = resolve(import.meta.dirname, "../../docs/capturas");
const colorScheme = process.env.TRUCO_TEMA === "claro" ? "light" : "dark";

const navegador = await chromium.launch();
const opciones = { ...devices["Pixel 7"], locale: "es-AR", colorScheme };
const ctxA = await navegador.newContext(opciones);
const ctxB = await navegador.newContext(opciones);
const ana = await ctxA.newPage();
const beto = await ctxB.newPage();

async function entrar(page, nombre) {
  await page.goto(base);
  await page.locator("#nombre").fill(nombre);
  await page.getByRole("button", { name: "Entrar a la mesa" }).click();
  await page.getByRole("button", { name: "Estoy listo" }).waitFor();
}

await entrar(ana, "Ana");
await entrar(beto, "Beto");
await ana.screenshot({ path: `${salida}/1-lobby.png` });

// Ajustes (idioma, tema, sonido, diagnóstico) desde el lobby.
await ana.getByRole("button", { name: "Ajustes" }).click();
await ana.getByRole("dialog").waitFor();
await ana.waitForTimeout(350); // que termine la animación de la hoja
await ana.screenshot({ path: `${salida}/6-ajustes.png` });
await ana.keyboard.press("Escape");

// Tutorial de 3 pasos: aparece la primera vez en la mesa (en cada dispositivo).
await ana.getByRole("button", { name: "Estoy listo" }).click();
await beto.getByRole("button", { name: "Estoy listo" }).click();
await ana.locator(".tutorial").waitFor();
await beto.locator(".tutorial").waitFor();
await ana.waitForTimeout(300);
await ana.screenshot({ path: `${salida}/8-tutorial.png` });
for (const page of [ana, beto]) {
  await page.getByRole("button", { name: "Siguiente" }).click();
  await page.getByRole("button", { name: "Siguiente" }).click();
  await page.getByRole("button", { name: "¡A jugar!" }).click();
}
await ana.locator(".mi-mano .carta").first().waitFor();
await ana.waitForTimeout(350);
await ana.screenshot({ path: `${salida}/2-mesa.png` });

// Jugar dos cartas para ver la mesa con bazas y el panel de cantos.
for (let i = 0; i < 2; i++) {
  for (const page of [ana, beto]) {
    const carta = page.locator(".mi-mano .carta:not([disabled])").first();
    if ((await carta.count()) === 0) continue;
    await carta.click();
    await carta.click();
    await page.waitForTimeout(250);
    break;
  }
}
await ana.screenshot({ path: `${salida}/3-jugando.png` });

await ana.getByRole("button", { name: "¡Truco!" }).click();
await beto.waitForTimeout(300);
await beto.screenshot({ path: `${salida}/4-canto.png` });

// Ayuda de reglas desde la mesa.
await ana.getByRole("button", { name: "Historia, ayuda y ajustes" }).click();
await ana.getByRole("tab", { name: "Ayuda" }).click();
await ana.waitForTimeout(300);
await ana.screenshot({ path: `${salida}/7-ayuda.png` });

// Tema claro de la mesa (se cambia en Ajustes y queda guardado).
await ana.getByRole("tab", { name: "Ajustes" }).click();
await ana.getByRole("button", { name: "Claro" }).click();
await ana.keyboard.press("Escape");
await ana.waitForTimeout(250);
await ana.screenshot({ path: `${salida}/9-tema-claro.png` });

await navegador.close();
console.log(`Capturas en ${salida}`);
