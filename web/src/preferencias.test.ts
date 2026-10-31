import { beforeEach, describe, expect, it, vi } from "vitest";

/**
 * `preferencias` lee y escribe `localStorage` al importarse, así que el stub tiene que
 * existir antes del import dinámico (y `resetModules` para simular otro arranque).
 */
function almacenFalso(inicial: Record<string, string> = {}) {
  const datos = new Map(Object.entries(inicial));
  return {
    datos,
    almacen: {
      getItem: (clave: string) => datos.get(clave) ?? null,
      setItem: (clave: string, valor: string) => {
        datos.set(clave, valor);
      },
      removeItem: (clave: string) => {
        datos.delete(clave);
      },
      clear: () => datos.clear(),
      key: (indice: number) => [...datos.keys()][indice] ?? null,
      get length() {
        return datos.size;
      },
    },
  };
}

describe("preferencias", () => {
  beforeEach(() => {
    vi.resetModules();
    vi.unstubAllGlobals();
  });

  it("arranca en español, tema auto y tutorial sin ver", async () => {
    vi.stubGlobal("localStorage", almacenFalso().almacen);
    const { preferencias } = await import("./preferencias");
    expect(preferencias()).toMatchObject({
      idioma: "es",
      tema: "auto",
      sonido: true,
      vibracion: true,
      tutorialVisto: false,
    });
  });

  it("guarda los cambios y los recupera al volver a abrir", async () => {
    const { almacen, datos } = almacenFalso();
    vi.stubGlobal("localStorage", almacen);

    const primera = await import("./preferencias");
    primera.cambiarPreferencias({ idioma: "en", tema: "claro", sonido: false, tutorialVisto: true });

    const guardado = JSON.parse(datos.get("truco.prefs")!);
    expect(guardado).toMatchObject({ idioma: "en", tema: "claro", sonido: false, tutorialVisto: true });

    // Otro arranque (pestaña nueva) lee lo guardado.
    vi.resetModules();
    const segunda = await import("./preferencias");
    expect(segunda.preferencias()).toMatchObject({
      idioma: "en",
      tema: "claro",
      sonido: false,
      vibracion: true,
      tutorialVisto: true,
    });
  });

  it("ignora un localStorage corrupto en vez de romper la app", async () => {
    vi.stubGlobal("localStorage", almacenFalso({ "truco.prefs": "{no es json" }).almacen);
    const { preferencias } = await import("./preferencias");
    expect(preferencias().idioma).toBe("es");
    expect(preferencias().tema).toBe("auto");
  });

  it("valida cada campo guardado", async () => {
    const corrupto = JSON.stringify({ idioma: "fr", tema: "neon", sonido: "sí", tutorialVisto: 1 });
    vi.stubGlobal("localStorage", almacenFalso({ "truco.prefs": corrupto }).almacen);
    const { preferencias } = await import("./preferencias");
    expect(preferencias()).toMatchObject({
      idioma: "es",
      tema: "auto",
      sonido: true,
      vibracion: true,
      tutorialVisto: false,
    });
  });
});
