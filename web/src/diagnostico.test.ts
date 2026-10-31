import { beforeEach, describe, expect, it } from "vitest";
import { cantidadEntradas, limpiarDiagnostico, registrar, textoDiagnostico } from "./diagnostico";

describe("log de diagnóstico", () => {
  beforeEach(() => limpiarDiagnostico());

  it("guarda solo los últimos 250 mensajes", () => {
    for (let i = 0; i < 300; i += 1) registrar("←", "state", `seq ${i}`);

    expect(cantidadEntradas()).toBe(250);
    const texto = textoDiagnostico({ sala: "ABCD", idioma: "es", protocolo: 1 });
    expect(texto).toContain("Truco Argentino — diagnóstico");
    expect(texto).toContain("sala: ABCD");
    expect(texto).toContain("idioma: es");
    expect(texto).toContain("seq 299");
    expect(texto).not.toContain("seq 49");
  });

  it("recorta los mensajes largos y conserva el sentido", () => {
    registrar("→", "action", "x".repeat(500));
    const texto = textoDiagnostico();
    expect(texto).toContain("action");
    expect(texto).not.toContain("x".repeat(400));
    expect(texto).toContain("…");
  });

  it("sin actividad lo dice en vez de quedar vacío", () => {
    expect(textoDiagnostico()).toContain("(sin actividad registrada)");
  });
});
