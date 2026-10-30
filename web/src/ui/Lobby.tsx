import type { Lobby as LobbyDto } from "../tipos";

interface Props {
  lobby: LobbyDto;
  miId: number;
  onListo: (listo: boolean) => void;
}

export function Lobby({ lobby, miId, onListo }: Props) {
  const yo = lobby.asientos.find((asiento) => asiento.id === miId);
  const libres = Math.max(0, lobby.config.jugadores - lobby.asientos.length);
  const listos = lobby.asientos.filter((asiento) => asiento.listo).length;

  return (
    <div className="pantalla lobby">
      <header className="encabezado">
        <p className="codigo">
          Sala <strong>{lobby.codigo}</strong>
        </p>
        <h2>Esperando jugadores</h2>
        <p className="resumen">
          {lobby.config.jugadores} jugadores · a {lobby.config.targetScore}
          {lobby.config.malasYBuenas ? " (malas y buenas)" : ""} ·{" "}
          {lobby.config.flor === "FLOR" ? "con flor" : "sin flor"}
        </p>
        <p className="resumen">
          {lobby.asientos.length}/{lobby.config.jugadores} en la mesa · {listos} listo(s)
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
              {asiento.esHost ? <em> · anfitrión</em> : null}
            </span>
            <span className={`insignia ${asiento.listo ? "ok" : ""}`}>
              {!asiento.conectado ? "sin conexión" : asiento.listo ? "listo" : "esperando"}
            </span>
          </li>
        ))}

        {Array.from({ length: libres }, (_, indice) => (
          <li key={`libre-${indice}`} className="libre">
            <span className={`equipo equipo-${(lobby.asientos.length + indice) % 2}`}>
              {(lobby.asientos.length + indice) % 2 === 0 ? "A" : "B"}
            </span>
            <span className="nombre">Lugar libre</span>
            <span className="insignia">—</span>
          </li>
        ))}
      </ol>

      <button className={yo?.listo ? "secundario" : "primario"} onClick={() => onListo(!yo?.listo)}>
        {yo?.listo ? "Cancelar" : "Estoy listo"}
      </button>

      <p className="pie">
        Cuando estén todos listos, la partida empieza sola. Los compañeros de equipo juegan
        enfrentados: asientos 1 y 3 son del equipo A, 2 y 4 del equipo B.
      </p>
    </div>
  );
}
