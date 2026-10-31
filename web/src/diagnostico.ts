/**
 * Log de diagnóstico en memoria + exportación a texto.
 *
 * Sirve para depurar redes raras (hotspot que cambia de IP, WebView que duerme,
 * un jugador que no reconecta): el jugador descarga el archivo y se lo pasa al
 * anfitrión. Se guardan los últimos 250 eventos, sin historial persistente.
 */

export type Direccion = "→" | "←" | "!";

export interface EntradaDiagnostico {
  t: number;
  dir: Direccion;
  tipo: string;
  detalle: string;
}

const MAXIMO = 250;
const LARGO_DETALLE = 220;

const entradas: EntradaDiagnostico[] = [];

export function registrar(dir: Direccion, tipo: string, detalle = ""): void {
  const limpio = detalle.length > LARGO_DETALLE ? `${detalle.slice(0, LARGO_DETALLE)}…` : detalle;
  entradas.push({ t: Date.now(), dir, tipo, detalle: limpio });
  if (entradas.length > MAXIMO) entradas.splice(0, entradas.length - MAXIMO);
}

export function limpiarDiagnostico(): void {
  entradas.length = 0;
}

export function cantidadEntradas(): number {
  return entradas.length;
}

function dosDigitos(valor: number): string {
  return String(valor).padStart(2, "0");
}

function hora(ms: number): string {
  const fecha = new Date(ms);
  return `${dosDigitos(fecha.getHours())}:${dosDigitos(fecha.getMinutes())}:${dosDigitos(
    fecha.getSeconds(),
  )}.${String(fecha.getMilliseconds()).padStart(3, "0")}`;
}

function conexion(): string {
  if (typeof navigator === "undefined") return "?";
  const red = (navigator as unknown as { connection?: { effectiveType?: string; downlink?: number } })
    .connection;
  if (!red) return "?";
  const partes = [red.effectiveType, red.downlink ? `${red.downlink} Mb/s` : null].filter(Boolean);
  return partes.length > 0 ? partes.join(" ") : "?";
}

export interface ContextoDiagnostico {
  [clave: string]: string | number | boolean | null | undefined;
}

/** Arma el texto que se descarga. `contexto` son los datos de la sesión actual. */
export function textoDiagnostico(contexto: ContextoDiagnostico = {}): string {
  const lineas: string[] = [];
  lineas.push("Truco Argentino — diagnóstico");
  lineas.push(`Generado: ${new Date().toISOString()}`);
  if (typeof window !== "undefined") {
    lineas.push(`URL: ${window.location.href}`);
    lineas.push(`Pantalla: ${window.innerWidth}x${window.innerHeight} @${window.devicePixelRatio}`);
    lineas.push(`Tema del sistema: ${window.matchMedia?.("(prefers-color-scheme: dark)").matches ? "oscuro" : "claro"}`);
  }
  if (typeof navigator !== "undefined") {
    lineas.push(`Navegador: ${navigator.userAgent}`);
    lineas.push(`Idioma: ${navigator.language} (${navigator.languages?.join(", ") ?? "-"})`);
    lineas.push(`Conexión: ${conexion()}`);
  }

  const claves = Object.keys(contexto).sort();
  if (claves.length > 0) {
    lineas.push("");
    lineas.push("--- sesión ---");
    for (const clave of claves) {
      const valor = contexto[clave];
      if (valor === undefined) continue;
      lineas.push(`${clave}: ${valor === null ? "—" : String(valor)}`);
    }
  }

  lineas.push("");
  lineas.push(`--- últimos ${entradas.length} mensajes (más viejo primero) ---`);
  for (const entrada of entradas) {
    lineas.push(`${hora(entrada.t)} ${entrada.dir} ${entrada.tipo}${entrada.detalle ? ` ${entrada.detalle}` : ""}`);
  }
  if (entradas.length === 0) lineas.push("(sin actividad registrada)");

  return `${lineas.join("\n")}\n`;
}

function nombreArchivo(): string {
  const fecha = new Date();
  const sello = `${fecha.getFullYear()}${dosDigitos(fecha.getMonth() + 1)}${dosDigitos(
    fecha.getDate(),
  )}-${dosDigitos(fecha.getHours())}${dosDigitos(fecha.getMinutes())}`;
  return `truco-diagnostico-${sello}.txt`;
}

export function descargarDiagnostico(contexto: ContextoDiagnostico = {}): void {
  const texto = textoDiagnostico(contexto);
  const blob = new Blob([texto], { type: "text/plain;charset=utf-8" });
  const url = URL.createObjectURL(blob);
  const enlace = document.createElement("a");
  enlace.href = url;
  enlace.download = nombreArchivo();
  enlace.rel = "noopener";
  document.body.appendChild(enlace);
  enlace.click();
  enlace.remove();
  // Damos margen a que el navegador tome el blob antes de liberarlo.
  setTimeout(() => URL.revokeObjectURL(url), 10_000);
}

/** Copia el log al portapapeles. Devuelve false si el navegador no lo permite. */
export async function copiarDiagnostico(contexto: ContextoDiagnostico = {}): Promise<boolean> {
  const texto = textoDiagnostico(contexto);
  try {
    if (navigator.clipboard?.writeText) {
      await navigator.clipboard.writeText(texto);
      return true;
    }
  } catch {
    /* seguimos con el fallback */
  }
  try {
    const area = document.createElement("textarea");
    area.value = texto;
    area.setAttribute("readonly", "true");
    area.style.position = "fixed";
    area.style.opacity = "0";
    document.body.appendChild(area);
    area.select();
    const ok = document.execCommand("copy");
    area.remove();
    return ok;
  } catch {
    return false;
  }
}
