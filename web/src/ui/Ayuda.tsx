import { t, usarIdioma } from "../i18n";

interface Props {
  alCerrar?: () => void;
}

/**
 * Ayuda de reglas corta, pensada para leerse en el teléfono en medio de una mano.
 * No reemplaza al reglamento: explica lo que el jugador necesita para decidir.
 */
export function Ayuda({ alCerrar }: Props) {
  // Suscribe al idioma para que el panel se re-renderice al cambiarlo.
  usarIdioma();

  return (
    <div className="ayuda">
      <p className="detalle">{t("ayuda_intro")}</p>

      <Seccion titulo={t("ayuda_cartas_titulo")} texto={t("ayuda_cartas_texto")} />
      <Seccion titulo={t("ayuda_envido_titulo")} texto={t("ayuda_envido_texto")} />
      <Seccion titulo={t("ayuda_truco_titulo")} texto={t("ayuda_truco_texto")} />
      <Seccion titulo={t("ayuda_flor_titulo")} texto={t("ayuda_flor_texto")} />
      <Seccion titulo={t("ayuda_parda_titulo")} texto={t("ayuda_parda_texto")} />
      <Seccion titulo={t("ayuda_malas_titulo")} texto={t("ayuda_malas_texto")} />

      {alCerrar && (
        <button type="button" className="primario" onClick={alCerrar}>
          {t("ayuda_cerrar")}
        </button>
      )}
    </div>
  );
}

function Seccion({ titulo, texto }: { titulo: string; texto: string }) {
  return (
    <section>
      <h4>{titulo}</h4>
      <p>{texto}</p>
    </section>
  );
}
