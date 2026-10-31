import { t, usarIdioma } from "../i18n";
import type { Lobby as LobbyDto } from "../tipos";

interface Props {
  lobby: LobbyDto;
  miId: number;
  onListo: (listo: boolean) => void;
  onAyuda: () => void;
  onAjustes: () => void;
}

export function Lobby({ lobby, miId, onListo, onAyuda, onAjustes }: Props) {
  usarIdioma();
  const yo = lobby.asientos.find((asiento) => asiento.id === miId);
  const libres = Math.max(0, lobby.config.jugadores - lobby.asientos.length);
  const listos = lobby.asientos.filter((asiento) => asiento.listo).length;

  return (
    <div className="pantalla lobby">
      <header className="encabezado">
        <p className="codigo">
          {t("lobby_sala")} <strong>{lobby.codigo}</strong>
        </p>
        <h2>{t("lobby_esperando")}</h2>
        <p className="resumen">
          {t("lobby_resumen", { jugadores: lobby.config.jugadores, objetivo: lobby.config.targetScore })}
          {lobby.config.malasYBuenas ? ` ${t("lobby_malas_buenas")}` : ""} ·{" "}
          {lobby.config.flor === "FLOR" ? t("lobby_con_flor") : t("lobby_sin_flor")}
        </p>
        <p className="resumen">
          {t("lobby_en_mesa", {
            dentro: lobby.asientos.length,
            total: lobby.config.jugadores,
            listos,
          })}
        </p>
      </header>

      <ol className="asientos">
        {lobby.asientos.map((asiento) => (
          <li
            key={asiento.id}
            className={`${asiento.id === miId ? "yo " : ""}${asiento.listo ? "listo" : ""}`}
          >
            <span className={`equipo equipo-${asiento.equipo}`}>{asiento.equipo === 0 ? "A" : "B"}</span>
            <span className="nombre">
              {asiento.nombre}
              {asiento.esHost ? <em>{t("lobby_anfitrion")}</em> : null}
            </span>
            <span className={`insignia ${asiento.listo ? "ok" : ""}`}>
              {!asiento.conectado
                ? t("lobby_sin_conexion")
                : asiento.listo
                  ? t("lobby_listo")
                  : t("lobby_esperando_estado")}
            </span>
          </li>
        ))}

        {Array.from({ length: libres }, (_, indice) => (
          <li key={`libre-${indice}`} className="libre">
            <span className={`equipo equipo-${(lobby.asientos.length + indice) % 2}`}>
              {(lobby.asientos.length + indice) % 2 === 0 ? "A" : "B"}
            </span>
            <span className="nombre">{t("lobby_lugar_libre")}</span>
            <span className="insignia">—</span>
          </li>
        ))}
      </ol>

      <button className={yo?.listo ? "secundario" : "primario"} onClick={() => onListo(!yo?.listo)}>
        {yo?.listo ? t("lobby_cancelar") : t("lobby_estoy_listo")}
      </button>

      <div className="fila">
        <button type="button" className="secundario chico" onClick={onAyuda}>
          {t("lobby_como_jugar")}
        </button>
        <button type="button" className="secundario chico" onClick={onAjustes}>
          {t("ajustes")}
        </button>
      </div>

      <p className="pie">{t("lobby_pie")}</p>
    </div>
  );
}
