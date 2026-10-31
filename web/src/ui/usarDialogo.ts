import { useEffect, useRef } from "react";

const ENFOCABLES =
  'button:not([disabled]), [href], input:not([disabled]), select:not([disabled]), textarea:not([disabled]), [tabindex]:not([tabindex="-1"])';

/**
 * Comportamiento mínimo de diálogo accesible: enfoca el primer control al abrir,
 * encierra el Tab, cierra con Escape y devuelve el foco al cerrar.
 */
export function usarDialogo<T extends HTMLElement>(abierto: boolean, alCerrar: () => void) {
  const ref = useRef<T | null>(null);
  // El callback suele ser una lambda nueva en cada render: si estuviera en las deps,
  // el efecto se re-ejecutaría y robaría el foco cada vez que la mesa se actualiza.
  const alCerrarRef = useRef(alCerrar);
  alCerrarRef.current = alCerrar;

  useEffect(() => {
    if (!abierto) return;
    const anterior = document.activeElement instanceof HTMLElement ? document.activeElement : null;
    const nodo = ref.current;

    const enfocables = (): HTMLElement[] =>
      Array.from(nodo?.querySelectorAll<HTMLElement>(ENFOCABLES) ?? []).filter(
        (elemento) => elemento.offsetParent !== null || elemento === document.activeElement,
      );

    const primero = enfocables()[0];
    primero?.focus();

    const alTeclear = (evento: KeyboardEvent) => {
      if (evento.key === "Escape") {
        evento.preventDefault();
        alCerrarRef.current();
        return;
      }
      if (evento.key !== "Tab") return;
      const lista = enfocables();
      if (lista.length === 0) return;
      const primerNodo = lista[0]!;
      const ultimoNodo = lista[lista.length - 1]!;
      if (evento.shiftKey && document.activeElement === primerNodo) {
        evento.preventDefault();
        ultimoNodo.focus();
      } else if (!evento.shiftKey && document.activeElement === ultimoNodo) {
        evento.preventDefault();
        primerNodo.focus();
      }
    };

    document.addEventListener("keydown", alTeclear);
    return () => {
      document.removeEventListener("keydown", alTeclear);
      anterior?.focus?.();
    };
  }, [abierto]);

  return ref;
}
