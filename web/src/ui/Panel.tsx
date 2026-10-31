import { useState } from "react";
import { t, textoEvento, usarIdioma } from "../i18n";
import type { Cliente } from "../net/cliente";
import type { Evento } from "../tipos";
import { Ajustes } from "./Ajustes";
import { Ayuda } from "./Ayuda";
import { usarDialogo } from "./usarDialogo";

export type Pestana = "historia" | "ayuda" | "ajustes";

interface Props {
  cliente: Cliente;
  eventos: Evento[];
  nombres: string[];
  pestanaInicial?: Pestana;
  alCerrar: () => void;
  alVerTutorial: () => void;
}

const PESTANAS: Pestana[] = ["historia", "ayuda", "ajustes"];

/**
 * Una sola hoja inferior para historia, ayuda y ajustes: en un teléfono de 360 px
 * no entran tres botones en la barra de la mesa.
 */
export function Panel({ cliente, eventos, nombres, pestanaInicial = "historia", alCerrar, alVerTutorial }: Props) {
  const [pestana, setPestana] = useState<Pestana>(pestanaInicial);
  const ref = usarDialogo<HTMLDivElement>(true, alCerrar);
  usarIdioma();

  const etiqueta = (id: Pestana) =>
    id === "historia" ? t("mesa_historia") : id === "ayuda" ? t("ayuda") : t("ajustes");

  function mover(dir: 1 | -1) {
    const indice = PESTANAS.indexOf(pestana);
    const siguiente = PESTANAS[(indice + dir + PESTANAS.length) % PESTANAS.length]!;
    setPestana(siguiente);
    document.getElementById(`pestana-${siguiente}`)?.focus();
  }

  return (
    <div className="velo">
      <div className="hoja" ref={ref} role="dialog" aria-modal="true" aria-label={t("mesa_aria_panel")}>
        <header className="hoja-header">
          <div className="pestanas" role="tablist" aria-label={t("mesa_aria_panel")}>
            {PESTANAS.map((id) => (
              <button
                key={id}
                id={`pestana-${id}`}
                type="button"
                role="tab"
                aria-selected={pestana === id}
                aria-controls="panel-mesa"
                tabIndex={pestana === id ? 0 : -1}
                className={pestana === id ? "pestana activa" : "pestana"}
                onClick={() => setPestana(id)}
                onKeyDown={(evento) => {
                  if (evento.key === "ArrowRight") {
                    evento.preventDefault();
                    mover(1);
                  } else if (evento.key === "ArrowLeft") {
                    evento.preventDefault();
                    mover(-1);
                  }
                }}
              >
                {etiqueta(id)}
              </button>
            ))}
          </div>
          <button type="button" className="icono" onClick={alCerrar} aria-label={t("cerrar")}>
            ×
          </button>
        </header>

        <div
          id="panel-mesa"
          role="tabpanel"
          aria-labelledby={`pestana-${pestana}`}
          className="hoja-cuerpo"
        >
          {pestana === "historia" && (
            <ol className="historia-lista">
              {eventos.map((evento, indice) => (
                <li key={`${evento.tipo}-${indice}`}>{textoEvento(evento, nombres)}</li>
              ))}
              {eventos.length === 0 && <li className="detalle">{t("mesa_nada")}</li>}
            </ol>
          )}
          {pestana === "ayuda" && <Ayuda />}
          {pestana === "ajustes" && <Ajustes cliente={cliente} alVerTutorial={alVerTutorial} />}
        </div>
      </div>
    </div>
  );
}
