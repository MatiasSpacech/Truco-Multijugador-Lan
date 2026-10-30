import { chromium, devices } from "@playwright/test";
import { resolve } from "node:path";

/**
 * Capturas para revisar el diseño a ojo (no forma parte de los tests):
 *   node scripts/captura.mjs   (con el servidor levantado en :8123)
 */
const base = process.env.TRUCO_URL ?? "http://127.0.0.1:8123";
const salida = resolve(import.meta.dirname, "../../docs/capturas");

const navegador = await chromium.launch();
const ctxA = await navegador.newContext({ ...devices["Pixel 7"] });
const ctxB = await navegador.newContext({ ...devices["Pixel 7"] });
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

await ana.getByRole("button", { name: "Estoy listo" }).click();
await beto.getByRole("button", { name: "Estoy listo" }).click();
await ana.locator(".mi-mano .carta").first().waitFor();
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

await navegador.close();
console.log(`Capturas en ${salida}`);
