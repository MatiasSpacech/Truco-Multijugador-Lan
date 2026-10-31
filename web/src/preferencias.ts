import { useSyncExternalStore } from "react";
import type { Idioma } from "./i18n";

export type Tema = "auto" | "claro" | "oscuro";
export type TemaResuelto = "claro" | "oscuro";

export interface Preferencias {
  idioma: Idioma;
  tema: Tema;
  sonido: boolean;
  vibracion: boolean;
  /** El tutorial de 3 pasos se muestra una sola vez por dispositivo. */
  tutorialVisto: boolean;
}

const CLAVE = "truco.prefs";

/**
 * Sin preferencia guardada elegimos por el idioma del sistema: `en` para
 * navegadores en inglés, español para todo lo demás (es un juego argentino).
 */
function idiomaDetectado(): Idioma {
  if (typeof navigator === "undefined") return "es";
  const navegador = (navigator.language || "").toLowerCase();
  return navegador.startsWith("en") ? "en" : "es";
}

const POR_DEFECTO: Preferencias = {
  idioma: idiomaDetectado(),
  tema: "auto",
  sonido: true,
  vibracion: true,
  tutorialVisto: false,
};

function cargar(): Preferencias {
  if (typeof localStorage === "undefined") return { ...POR_DEFECTO };
  try {
    const crudo = localStorage.getItem(CLAVE);
    if (!crudo) return { ...POR_DEFECTO };
    const datos = JSON.parse(crudo) as Partial<Preferencias>;
    return {
      idioma: datos.idioma === "en" || datos.idioma === "es" ? datos.idioma : POR_DEFECTO.idioma,
      tema: datos.tema === "claro" || datos.tema === "oscuro" || datos.tema === "auto" ? datos.tema : "auto",
      sonido: typeof datos.sonido === "boolean" ? datos.sonido : POR_DEFECTO.sonido,
      vibracion: typeof datos.vibracion === "boolean" ? datos.vibracion : POR_DEFECTO.vibracion,
      tutorialVisto: datos.tutorialVisto === true,
    };
  } catch {
    return { ...POR_DEFECTO };
  }
}

let estado: Preferencias = cargar();
const oyentes = new Set<() => void>();

export const preferencias = (): Preferencias => estado;

export function suscribirPreferencias(oyente: () => void): () => void {
  oyentes.add(oyente);
  return () => {
    oyentes.delete(oyente);
  };
}

export function usarPreferencias(): Preferencias {
  return useSyncExternalStore(suscribirPreferencias, preferencias, preferencias);
}

export function cambiarPreferencias(cambios: Partial<Preferencias>): void {
  estado = { ...estado, ...cambios };
  try {
    localStorage.setItem(CLAVE, JSON.stringify(estado));
  } catch {
    /* modo privado: seguimos en memoria */
  }
  aplicarTema();
  aplicarIdioma();
  oyentes.forEach((oyente) => oyente());
}

function temaResuelto(tema: Tema): TemaResuelto {
  if (tema !== "auto") return tema;
  if (typeof window === "undefined" || !window.matchMedia) return "oscuro";
  return window.matchMedia("(prefers-color-scheme: light)").matches ? "claro" : "oscuro";
}

/** Escribe el tema resuelto en `<html data-tema>` y actualiza el color de la barra del navegador. */
export function aplicarTema(): void {
  if (typeof document === "undefined") return;
  const resuelto = temaResuelto(estado.tema);
  document.documentElement.dataset.tema = resuelto;
  const meta = document.querySelector('meta[name="theme-color"]');
  if (meta) meta.setAttribute("content", resuelto === "claro" ? "#efe6cf" : "#0f2a20");
}

export function aplicarIdioma(): void {
  if (typeof document === "undefined") return;
  document.documentElement.lang = estado.idioma;
}

/**
 * Se llama una vez al arrancar: aplica tema/idioma antes del primer render (sin
 * parpadeo) y escucha los cambios del tema del sistema cuando está en "auto".
 */
export function iniciarPreferencias(): void {
  aplicarTema();
  aplicarIdioma();
  if (typeof window === "undefined" || !window.matchMedia) return;
  const consulta = window.matchMedia("(prefers-color-scheme: light)");
  const alCambiar = () => {
    if (estado.tema === "auto") aplicarTema();
  };
  if (consulta.addEventListener) consulta.addEventListener("change", alCambiar);
  else consulta.addListener(alCambiar);
}
