import { beforeEach, describe, expect, it } from "vitest";
import {
  CLAVES,
  etiquetaAccion,
  nombreCanto,
  nombreCarta,
  t,
  textoCrudo,
  textoEvento,
} from "./i18n";
import { cambiarPreferencias } from "./preferencias";
import type { Evento } from "./tipos";

function marcadores(texto: string): string[] {
  return [...texto.matchAll(/\{(\w+)\}/g)].map((encontrado) => encontrado[1]!).sort();
}

describe("diccionarios", () => {
  it("es y en tienen las mismas claves, textos y parámetros", () => {
    for (const clave of CLAVES) {
      const es = textoCrudo("es", clave);
      const en = textoCrudo("en", clave);
      expect(es.length, `${clave} (es)`).toBeGreaterThan(0);
      expect(en.length, `${clave} (en)`).toBeGreaterThan(0);
      // Si una traducción olvida un {parámetro}, el texto sale con huecos.
      expect(marcadores(en), `${clave} (en)`).toEqual(marcadores(es));
    }
  });

  it("traduce los parámetros", () => {
    cambiarPreferencias({ idioma: "es" });
    expect(t("mesa_vale", { n: 3 })).toBe("Vale 3");
    cambiarPreferencias({ idioma: "en" });
    expect(t("mesa_vale", { n: 3 })).toBe("Worth 3");
  });
});

describe("nombres del juego", () => {
  beforeEach(() => cambiarPreferencias({ idioma: "es" }));

  it("las cartas se nombran en el idioma activo", () => {
    const carta = { palo: "ORO" as const, numero: 12 };
    expect(nombreCarta(carta, "es")).toBe("12 de oro");
    expect(nombreCarta(carta, "en")).toBe("12 of golds");
    expect(nombreCarta({ palo: "ESPADA", numero: 3 }, "en")).toBe("3 of swords");
  });

  it("los cantos conservan su nombre y las acciones su etiqueta", () => {
    expect(nombreCanto("CONTRAFLOR_AL_RESTO")).toBe("contraflor al resto");
    expect(nombreCanto("ENVIDO")).toBe("envido");
    expect(etiquetaAccion("CANTAR_TRUCO")).toBe("¡Truco!");
    cambiarPreferencias({ idioma: "en" });
    expect(etiquetaAccion("CANTAR_TRUCO")).toBe("Truco!");
    expect(etiquetaAccion("RESPONDER_NO_QUIERO")).toBe("No");
  });

  it("un canto o una acción desconocidos no rompen la mesa", () => {
    expect(nombreCanto("CANTO_NUEVO")).toBe("canto nuevo");
    expect(etiquetaAccion("ACCION_NUEVA")).toBe("ACCION_NUEVA");
  });
});

describe("historia traducida", () => {
  const nombres = ["Ana", "Beto"];

  beforeEach(() => cambiarPreferencias({ idioma: "es" }));

  it("traduce una carta jugada sin usar el texto del servidor", () => {
    const evento: Evento = {
      tipo: "CARTA_JUGADA",
      texto: "Ana jugó 3 de espada",
      jugador: 0,
      carta: { palo: "ESPADA", numero: 3 },
    };
    expect(textoEvento(evento, nombres)).toBe("Ana jugó 3 de espada");
    cambiarPreferencias({ idioma: "en" });
    expect(textoEvento(evento, nombres)).toBe("Ana played 3 of swords");
  });

  it("traduce el envido con sus valores y el rechazo", () => {
    const resuelto: Evento = {
      tipo: "ENVIDO_RESUELTO",
      texto: "Envido: 30 punto(s) para el equipo 2 (30 / 27)",
      puntos: 30,
      equipo: 1,
      valores: [30, 27],
      porRechazo: false,
    };
    expect(textoEvento(resuelto, nombres)).toBe("Envido: 30 punto(s) para el equipo B (30 / 27)");
    cambiarPreferencias({ idioma: "en" });
    expect(textoEvento(resuelto, nombres)).toBe("Envido: 30 point(s) for team B (30 / 27)");

    const rechazado: Evento = {
      tipo: "ENVIDO_RESUELTO",
      texto: "Envido rechazado",
      puntos: 2,
      equipo: 0,
      porRechazo: true,
    };
    expect(textoEvento(rechazado, nombres)).toBe("Envido declined: 2 point(s) for team A");
  });

  it("cae al texto del servidor si falta el dato estructurado", () => {
    const sinValores: Evento = {
      tipo: "FLOR_RESUELTA",
      texto: "Flor: 3 punto(s) para el equipo 1 (38)",
      puntos: 3,
      equipo: 0,
    };
    // Sin `valores` el cliente no inventa: usa el texto original del servidor.
    expect(textoEvento(sinValores, nombres)).toBe("Flor: 3 punto(s) para el equipo 1 (38)");
    const desconocido: Evento = { tipo: "EVENTO_NUEVO", texto: "Algo pasó" };
    expect(textoEvento(desconocido, nombres)).toBe("Algo pasó");
  });
});
