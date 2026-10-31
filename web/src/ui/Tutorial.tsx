import { useState } from "react";
import { t, usarIdioma } from "../i18n";
import type { Clave } from "../i18n";
import { usarDialogo } from "./usarDialogo";

interface Props {
  alCerrar: () => void;
}

const PASOS: { titulo: Clave; texto: Clave; icono: "cartas" | "canto" | "auto" }[] = [
  { titulo: "tutorial_1_titulo", texto: "tutorial_1_texto", icono: "cartas" },
  { titulo: "tutorial_2_titulo", texto: "tutorial_2_texto", icono: "canto" },
  { titulo: "tutorial_3_titulo", texto: "tutorial_3_texto", icono: "auto" },
];

/** Tutorial de 3 pasos que aparece la primera vez que alguien se sienta a la mesa. */
export function Tutorial({ alCerrar }: Props) {
  usarIdioma();
  const [paso, setPaso] = useState(0);
  const ref = usarDialogo<HTMLDivElement>(true, alCerrar);
  const actual = PASOS[paso]!;
  const ultimo = paso === PASOS.length - 1;

  return (
    <div className="velo">
      <div
        className="tarjeta tutorial"
        ref={ref}
        role="dialog"
        aria-modal="true"
        aria-labelledby="tutorial-titulo"
      >
        <p className="tutorial-paso">{t("tutorial_paso", { n: paso + 1 })}</p>
        <Icono tipo={actual.icono} />
        <h2 id="tutorial-titulo">{t(actual.titulo)}</h2>
        <p className="tutorial-texto">{t(actual.texto)}</p>

        <div className="puntos-paso" aria-hidden="true">
          {PASOS.map((_, indice) => (
            <span key={indice} className={indice === paso ? "punto activo" : "punto"} />
          ))}
        </div>

        <div className="fila">
          {paso > 0 ? (
            <button type="button" className="secundario" onClick={() => setPaso((valor) => valor - 1)}>
              {t("tutorial_anterior")}
            </button>
          ) : (
            <button type="button" className="secundario" onClick={alCerrar}>
              {t("tutorial_saltar")}
            </button>
          )}
          <button
            type="button"
            className="primario"
            onClick={() => (ultimo ? alCerrar() : setPaso((valor) => valor + 1))}
          >
            {ultimo ? t("tutorial_listo") : t("tutorial_siguiente")}
          </button>
        </div>
      </div>
    </div>
  );
}

function Icono({ tipo }: { tipo: "cartas" | "canto" | "auto" }) {
  return (
    <svg className="tutorial-icono" viewBox="0 0 64 48" aria-hidden="true" focusable="false">
      {tipo === "cartas" && (
        <g>
          <rect x="8" y="8" width="20" height="30" rx="3" transform="rotate(-12 18 23)" fill="currentColor" opacity="0.35" />
          <rect x="22" y="8" width="20" height="30" rx="3" fill="currentColor" opacity="0.6" />
          <rect x="36" y="8" width="20" height="30" rx="3" transform="rotate(12 46 23)" fill="currentColor" />
        </g>
      )}
      {tipo === "canto" && (
        <g>
          <path d="M6 12a5 5 0 0 1 5-5h26a5 5 0 0 1 5 5v12a5 5 0 0 1-5 5H22l-8 8v-8h-3a5 5 0 0 1-5-5Z" fill="currentColor" opacity="0.55" />
          <path d="M28 24a5 5 0 0 1 5-5h20a5 5 0 0 1 5 5v9a5 5 0 0 1-5 5h-3v7l-7-7h-10a5 5 0 0 1-5-5Z" fill="currentColor" />
        </g>
      )}
      {tipo === "auto" && (
        <g>
          <circle cx="32" cy="24" r="18" fill="currentColor" opacity="0.25" />
          <path d="M23 24.5l6 6 12-13" fill="none" stroke="currentColor" strokeWidth="4.5" strokeLinecap="round" strokeLinejoin="round" />
        </g>
      )}
    </svg>
  );
}
