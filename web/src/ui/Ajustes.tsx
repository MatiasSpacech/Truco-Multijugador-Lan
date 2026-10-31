import { useState } from "react";
import type { ReactNode } from "react";
import { t, usarIdioma } from "../i18n";
import type { Cliente } from "../net/cliente";
import { cambiarPreferencias, usarPreferencias } from "../preferencias";
import type { Tema } from "../preferencias";
import { sonar, vibrar } from "../sonido";

interface Props {
  cliente: Cliente;
  alVerTutorial: () => void;
}

export function Ajustes({ cliente, alVerTutorial }: Props) {
  const preferencias = usarPreferencias();
  const idioma = usarIdioma();
  const [copiado, setCopiado] = useState(false);

  async function copiar() {
    const ok = await cliente.copiarDiagnostico();
    if (!ok) return;
    setCopiado(true);
    setTimeout(() => setCopiado(false), 2500);
  }

  return (
    <div className="ajustes">
      <section>
        <h4>{t("ajustes_idioma")}</h4>
        <div className="segmentado" role="group" aria-label={t("ajustes_idioma")}>
          <Opcion activa={idioma === "es"} onClick={() => cambiarPreferencias({ idioma: "es" })}>
            Español
          </Opcion>
          <Opcion activa={idioma === "en"} onClick={() => cambiarPreferencias({ idioma: "en" })}>
            English
          </Opcion>
        </div>
      </section>

      <section>
        <h4>{t("ajustes_tema")}</h4>
        <div className="segmentado" role="group" aria-label={t("ajustes_tema")}>
          {(["auto", "claro", "oscuro"] as Tema[]).map((tema) => (
            <Opcion
              key={tema}
              activa={preferencias.tema === tema}
              onClick={() => cambiarPreferencias({ tema })}
            >
              {t(tema === "auto" ? "ajustes_tema_auto" : tema === "claro" ? "ajustes_tema_claro" : "ajustes_tema_oscuro")}
            </Opcion>
          ))}
        </div>
      </section>

      <section>
        <h4>{t("ajustes_sonido")}</h4>
        <div className="fila">
          <Opcion
            activa={preferencias.sonido}
            onClick={() => {
              const sonido = !preferencias.sonido;
              cambiarPreferencias({ sonido });
              if (sonido) sonar("punto");
            }}
          >
            {preferencias.sonido ? t("si") : t("no")}
          </Opcion>
          <button type="button" className="secundario chico" onClick={() => sonar("canto", true)}>
            {t("ajustes_probar_sonido")}
          </button>
        </div>
      </section>

      <section>
        <h4>{t("ajustes_vibracion")}</h4>
        <div className="fila">
          <Opcion
            activa={preferencias.vibracion}
            onClick={() => {
              const vibracion = !preferencias.vibracion;
              cambiarPreferencias({ vibracion });
              if (vibracion) vibrar([20, 40, 20]);
            }}
          >
            {preferencias.vibracion ? t("si") : t("no")}
          </Opcion>
        </div>
      </section>

      <section>
        <h4>{t("ajustes_tutorial_titulo")}</h4>
        <button type="button" className="secundario" onClick={alVerTutorial}>
          {t("ajustes_tutorial")}
        </button>
      </section>

      <section>
        <h4>{t("ajustes_diagnostico")}</h4>
        <p className="detalle">{t("ajustes_diagnostico_texto")}</p>
        <div className="fila">
          <button type="button" className="secundario" onClick={() => cliente.descargarDiagnostico()}>
            {t("ajustes_descargar")}
          </button>
          <button type="button" className="secundario" onClick={() => void copiar()}>
            {copiado ? t("ajustes_copiado") : t("ajustes_copiar")}
          </button>
        </div>
      </section>
    </div>
  );
}

function Opcion({
  activa,
  onClick,
  children,
}: {
  activa: boolean;
  onClick: () => void;
  children: ReactNode;
}) {
  return (
    <button type="button" className={`opcion${activa ? " activa" : ""}`} aria-pressed={activa} onClick={onClick}>
      {children}
    </button>
  );
}
