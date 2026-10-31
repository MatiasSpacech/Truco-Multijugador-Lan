import { expect, test } from "./fixtures";
import type { Page } from "@playwright/test";
import { readFile } from "node:fs/promises";

async function entrar(page: Page, nombre: string) {
  await page.goto("/");
  await page.locator("#nombre").fill(nombre);
  await page.getByRole("button", { name: "Entrar a la mesa" }).click();
  await expect(page.getByRole("button", { name: "Estoy listo" })).toBeVisible();
}

/** Alto y ancho de todos los botones visibles: el mínimo accesible es 44 px. */
async function tamanosDeBotones(page: Page): Promise<{ texto: string; ancho: number; alto: number }[]> {
  return page.locator("button:visible").evaluateAll((nodos) =>
    nodos.map((nodo) => {
      const caja = nodo.getBoundingClientRect();
      return {
        texto: (nodo.getAttribute("aria-label") ?? nodo.textContent ?? "").trim().slice(0, 24),
        ancho: Math.round(caja.width),
        alto: Math.round(caja.height),
      };
    }),
  );
}

test("el tutorial de 3 pasos aparece una sola vez y la ayuda se abre desde la mesa", async ({
  browser,
}) => {
  const contextoAna = await browser.newContext();
  const contextoBeto = await browser.newContext();
  const ana = await contextoAna.newPage();
  const beto = await contextoBeto.newPage();

  await entrar(ana, "Ana");
  await entrar(beto, "Beto");
  await ana.getByRole("button", { name: "Estoy listo" }).click();
  await beto.getByRole("button", { name: "Estoy listo" }).click();

  // Tutorial: tres pasos, con "Atrás" disponible a partir del segundo.
  const tutorial = ana.locator(".tutorial");
  await expect(tutorial).toBeVisible();
  await expect(tutorial.getByText("Paso 1 de 3")).toBeVisible();
  await expect(tutorial.getByRole("button", { name: "Atrás" })).toHaveCount(0);

  await tutorial.getByRole("button", { name: "Siguiente" }).click();
  await expect(tutorial.getByText("Paso 2 de 3")).toBeVisible();
  await tutorial.getByRole("button", { name: "Atrás" }).click();
  await expect(tutorial.getByText("Paso 1 de 3")).toBeVisible();
  await tutorial.getByRole("button", { name: "Siguiente" }).click();
  await tutorial.getByRole("button", { name: "Siguiente" }).click();
  await expect(tutorial.getByText("Paso 3 de 3")).toBeVisible();
  await tutorial.getByRole("button", { name: "¡A jugar!" }).click();
  await expect(ana.getByRole("dialog")).toHaveCount(0);

  // Al recargar no vuelve a aparecer (quedó guardado en el dispositivo).
  await ana.reload();
  await expect(ana.locator(".mi-mano .carta")).toHaveCount(3);
  await expect(ana.getByRole("dialog")).toHaveCount(0);

  // La ayuda de reglas se abre desde la barra de la mesa, junto a la historia.
  await ana.getByRole("button", { name: "Historia, ayuda y ajustes" }).click();
  const panel = ana.getByRole("dialog", { name: /historia, ayuda y ajustes/i });
  await expect(panel).toBeVisible();
  await panel.getByRole("tab", { name: "Ayuda" }).click();
  await expect(panel.getByText("Las cartas")).toBeVisible();
  await expect(panel.getByText(/1 de espada, 1 de basto/)).toBeVisible();
  await panel.getByRole("tab", { name: "Lo que pasó" }).click();
  await expect(panel.getByText(/Se repartió la mano|jugó|Parda|ganó/).first()).toBeVisible();

  // Escape cierra la hoja y devuelve el foco a la barra.
  await ana.keyboard.press("Escape");
  await expect(panel).toHaveCount(0);

  // Con movimiento reducido no quedan animaciones activas.
  await ana.emulateMedia({ reducedMotion: "reduce" });
  const animacion = await ana
    .locator(".mi-mano .carta")
    .first()
    .evaluate((nodo) => getComputedStyle(nodo).animationName);
  expect(animacion).toBe("none");

  // Todo control tocable mide al menos 44x44 px.
  const chicos = (await tamanosDeBotones(ana)).filter((boton) => boton.alto < 44 || boton.ancho < 44);
  expect(chicos).toEqual([]);
});

test("idioma, tema y log de diagnóstico se cambian, se recuerdan y no rompen el lobby", async ({
  page,
}) => {
  await entrar(page, "Ana");

  // Ajustes desde el lobby.
  await page.getByRole("button", { name: "Ajustes" }).click();
  const panel = page.getByRole("dialog");
  await expect(panel.getByRole("tab", { name: "Ajustes" })).toHaveAttribute("aria-selected", "true");

  // El tutorial también se puede ver desde el lobby (no solo en la mesa).
  await panel.getByRole("button", { name: "Ver el tutorial" }).click();
  await expect(page.locator(".tutorial")).toBeVisible();
  await page.getByRole("button", { name: "Saltear" }).click();
  await expect(page.locator(".tutorial")).toHaveCount(0);
  await page.getByRole("button", { name: "Ajustes" }).click();
  await expect(panel.getByRole("tab", { name: "Ajustes" })).toHaveAttribute("aria-selected", "true");

  // Tema claro.
  await expect(page.locator("html")).toHaveAttribute("data-tema", "oscuro");
  await panel.getByRole("button", { name: "Claro" }).click();
  await expect(page.locator("html")).toHaveAttribute("data-tema", "claro");

  // Idioma: toda la interfaz pasa a inglés.
  await panel.getByRole("button", { name: "English" }).click();
  await expect(page.locator("html")).toHaveAttribute("lang", "en");
  await expect(panel.getByRole("tab", { name: "Settings" })).toBeVisible();
  await expect(page.getByRole("button", { name: "I'm ready" })).toBeVisible();

  // Log de diagnóstico: se descarga un .txt con la sesión y el tráfico WebSocket.
  const [descarga] = await Promise.all([
    page.waitForEvent("download"),
    panel.getByRole("button", { name: "Download log" }).click(),
  ]);
  expect(descarga.suggestedFilename()).toMatch(/^truco-diagnostico-\d{8}-\d{4}\.txt$/);
  const ruta = await descarga.path();
  expect(ruta).not.toBeNull();
  const texto = await readFile(ruta!, "utf8");
  expect(texto).toContain("Truco Argentino — diagnóstico");
  expect(texto).toContain("← welcome");
  expect(texto).toContain("→ join");
  expect(texto).toContain("idioma: en");

  // Al recargar se mantienen tema e idioma.
  await page.reload();
  await expect(page.locator("html")).toHaveAttribute("data-tema", "claro");
  await expect(page.locator("html")).toHaveAttribute("lang", "en");
  await expect(page.getByRole("button", { name: "I'm ready" })).toBeVisible();

  // Y se puede volver a español desde el lobby.
  await page.getByRole("button", { name: "Settings" }).click();
  await page.getByRole("button", { name: "Español" }).click();
  await expect(page.getByRole("button", { name: "Estoy listo" })).toBeVisible();
  await page.getByRole("button", { name: "Oscuro" }).click();
  await expect(page.locator("html")).toHaveAttribute("data-tema", "oscuro");
});
