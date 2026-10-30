import { expect, test } from "@playwright/test";
import type { Page } from "@playwright/test";

async function entrar(page: Page, nombre: string) {
  await page.goto("/");
  await page.locator("#nombre").fill(nombre);
  await page.getByRole("button", { name: "Entrar a la mesa" }).click();
  await expect(page.getByRole("button", { name: "Estoy listo" })).toBeVisible();
}

async function cartasEnMano(page: Page): Promise<string[]> {
  return page
    .locator(".mi-mano .carta")
    .evaluateAll((nodos) => nodos.map((nodo) => nodo.getAttribute("aria-label") ?? ""));
}

async function manoTerminada(page: Page): Promise<boolean> {
  const texto = await page.locator(".turno").innerText().catch(() => "");
  return texto.includes("Mano para el equipo");
}

/** Toca la primera carta jugable y confirma con el segundo toque. */
async function jugarCartaSiPuede(page: Page): Promise<boolean> {
  const carta = page.locator(".mi-mano .carta:not([disabled])").first();
  if ((await carta.count()) === 0) return false;

  await carta.click();
  await expect(page.locator(".confirmar")).toBeVisible();
  await carta.click();
  return true;
}

test("dos jugadores juegan una mano, se recarga sin perder la silla y se anota el punto", async ({
  browser,
}) => {
  const contextoAna = await browser.newContext();
  const contextoBeto = await browser.newContext();
  const ana = await contextoAna.newPage();
  const beto = await contextoBeto.newPage();

  await entrar(ana, "Ana");
  await entrar(beto, "Beto");

  await ana.getByRole("button", { name: "Estoy listo" }).click();
  await expect(ana.getByRole("button", { name: "Cancelar" })).toBeVisible();
  await beto.getByRole("button", { name: "Estoy listo" }).click();

  // Arranca la partida: cada uno ve 3 cartas.
  await expect(ana.locator(".mi-mano .carta")).toHaveCount(3);
  await expect(beto.locator(".mi-mano .carta")).toHaveCount(3);

  // Nadie ve las cartas del otro.
  const cartasAna = await cartasEnMano(ana);
  const cartasBeto = await cartasEnMano(beto);
  expect(cartasAna.filter((carta) => cartasBeto.includes(carta))).toEqual([]);

  // Recargar la página: vuelve a la mesa con la misma mano (token + rejoin).
  await ana.reload();
  await expect(ana.locator(".mi-mano .carta")).toHaveCount(3);
  const trasRecarga = await cartasEnMano(ana);
  expect([...trasRecarga].sort()).toEqual([...cartasAna].sort());

  // Jugar la mano completa alternando turnos.
  for (let intento = 0; intento < 16; intento++) {
    if (await manoTerminada(ana)) break;
    const jugoAna = await jugarCartaSiPuede(ana);
    const jugoBeto = jugoAna ? false : await jugarCartaSiPuede(beto);
    if (!jugoAna && !jugoBeto) await ana.waitForTimeout(150);
    else await ana.waitForTimeout(120);
  }

  // La mano se cerró y se anotó 1 punto (nadie cantó).
  await expect(ana.locator(".turno")).toContainText(/Mano para el equipo/);
  const puntos = await ana.locator(".puntos-num").allInnerTexts();
  expect(puntos.map(Number).reduce((total, valor) => total + valor, 0)).toBe(1);

  // Y el servidor reparte la mano siguiente solo.
  await expect(ana.locator(".mi-mano .carta")).toHaveCount(3, { timeout: 10_000 });
  await expect(ana.locator(".mano-datos")).toContainText("Mano 2");
});
