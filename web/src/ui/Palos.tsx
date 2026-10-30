import type { Palo } from "../tipos";

/**
 * Palos de la baraja española dibujados como SVG inline.
 * Usan `currentColor` para heredar el color del palo (negro o rojo) y se leen
 * igual a 40 px que a 90 px. Sin fuentes ni imágenes remotas.
 */
export function IconoPalo({ palo }: { palo: Palo }) {
  return (
    <svg viewBox="0 0 24 24" className="carta-icono-svg" aria-hidden="true" focusable="false">
      {palo === "ESPADA" && (
        <>
          {/* hoja ancha con hombros y punta */}
          <path d="M12 1.2 13.6 3.3 13.4 12.3 12 13.3 10.6 12.3 10.4 3.3Z" fill="currentColor" />
          {/* guarda */}
          <path
            d="M6.6 13.3h10.8a1 1 0 0 1 1 1v.4a1 1 0 0 1-1 1H6.6a1 1 0 0 1-1-1v-.4a1 1 0 0 1 1-1Z"
            fill="currentColor"
            opacity="0.8"
          />
          {/* empuñadura y pomo */}
          <path d="M11.3 15.9h1.4v3.4h-1.4z" fill="currentColor" opacity="0.9" />
          <circle cx="12" cy="20.9" r="1.4" fill="currentColor" opacity="0.85" />
        </>
      )}

      {palo === "BASTO" && (
        <>
          {/* tronco nudoso */}
          <path
            d="M6.1 21.9 15.9 4.1"
            stroke="currentColor"
            strokeWidth="4.6"
            strokeLinecap="round"
            fill="none"
          />
          <path
            d="M6.1 21.9 15.9 4.1"
            stroke="#fffdf7"
            strokeOpacity="0.22"
            strokeWidth="1.5"
            strokeLinecap="round"
            fill="none"
          />
          {/* nudos */}
          <g fill="#fffdf7" fillOpacity="0.5">
            <circle cx="8.9" cy="17.5" r="1.1" />
            <circle cx="11.2" cy="13.5" r="0.95" />
            <circle cx="13.4" cy="9.7" r="0.85" />
          </g>
        </>
      )}

      {palo === "ORO" && (
        <>
          {/* moneda */}
          <circle cx="12" cy="12" r="8.7" fill="currentColor" opacity="0.92" />
          <circle
            cx="12"
            cy="12"
            r="6.6"
            fill="none"
            stroke="#fffdf7"
            strokeOpacity="0.5"
            strokeWidth="0.8"
          />
          {/* sol: disco y rayos */}
          <circle cx="12" cy="12" r="3.1" fill="#fffdf7" fillOpacity="0.85" />
          <g stroke="#fffdf7" strokeOpacity="0.7" strokeWidth="1.1" strokeLinecap="round">
            <path d="M12 6.5v1.9M12 15.6v1.9M6.5 12h1.9M15.6 12h1.9" />
            <path d="M8.1 8.1l1.4 1.4M14.5 14.5l1.4 1.4M15.9 8.1l-1.4 1.4M9.5 14.5l-1.4 1.4" />
          </g>
        </>
      )}

      {palo === "COPA" && (
        <>
          {/* copa */}
          <path d="M6.6 3.9h10.8c0 5-2.1 7.6-5.4 7.6S6.6 8.9 6.6 3.9Z" fill="currentColor" />
          {/* pie */}
          <path d="M11.1 11.5h1.8v4.3h-1.8z" fill="currentColor" opacity="0.9" />
          <path d="M7.6 20.7c0-2.3 1.8-3.6 4.4-3.6s4.4 1.3 4.4 3.6Z" fill="currentColor" />
        </>
      )}
    </svg>
  );
}

/** Nombres de las figuras en la baraja española (10, 11 y 12). */
export const FIGURA: Record<number, string> = {
  10: "sota",
  11: "caballo",
  12: "rey",
};
