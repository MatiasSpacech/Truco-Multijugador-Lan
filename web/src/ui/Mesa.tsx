import { useEffect, useState } from "react";
import type { Cliente } from "../net/cliente";
import { ETIQUETA_ACCION, ETIQUETA_CANTO, accionDesdeNombre, mismaCarta } from "../net/cliente";
import type { Asiento, Baza, Carta as CartaDto, Evento, Vista } from "../tipos";
import { Carta } from "./Carta";

interface Props {
  vista: Vista;
  cliente: Cliente;
  eventos: Evento[];
  reconectando: boolean;
}

export function Mesa({ vista, cliente, eventos, reconectando }: Props) {
  const [elegida, setElegida] = useState<CartaDto | null>(null);
  const [verHistoria, setVerHistoria] = useState(false);

  const yo = vista.asientos[vista.miJugadorId];
  const claveJugable = new Set(vista.cartasJugables.map((carta) => `${carta.palo}-${carta.numero}`));
  const pendiente = vista.cantoPendiente;
  const esMiTurno = vista.turno === vista.miJugadorId && pendiente === null;
  const rivales = vista.asientos.filter((asiento) => asiento.id !== vista.miJugadorId);
  const acciones = vista.legalActions.filter((nombre) => nombre !== "JUGAR_CARTA");
  const accionesPie = pendiente ? [] : acciones;
  const miRespondo = pendiente?.respondedor === vista.miJugadorId;
  const restante = useRestante(pendiente?.deadlineMs);
  const enJuego = vista.fase === "JUEGO" || vista.fase === "FLOR";

  useEffect(() => {
    setElegida(null);
  }, [vista.seq]);

  function tocarCarta(carta: CartaDto) {
    const clave = `${carta.palo}-${carta.numero}`;
    if (!claveJugable.has(clave) || pendiente !== null) return;
    if (elegida && mismaCarta(elegida, carta)) {
      cliente.accion({ tipo: "JUGAR_CARTA", carta });
      setElegida(null);
      return;
    }
    setElegida(carta);
  }

  return (
    <main className={`mesa fase-${vista.fase.toLowerCase()}`}>
      <header className="barra">
        <div className="tanteador">
          <Puntos equipo={0} puntos={vista.puntajes[0]} objetivo={vista.targetScore} />
          <Puntos equipo={1} puntos={vista.puntajes[1]} objetivo={vista.targetScore} />
        </div>

        <div className="mano-datos">
          <span>Mano {vista.manoNumero + 1}</span>
          <strong className={vista.valorMano > 1 ? "dorado" : ""}>Vale {vista.valorMano}</strong>
        </div>

        <button
          type="button"
          className="icono"
          onClick={() => setVerHistoria((valor) => !valor)}
          aria-label="Ver historia de la mano"
          aria-expanded={verHistoria}
        >
          ≡
        </button>
      </header>

      <section className="rivales" aria-label="Jugadores">
        {rivales.map((asiento) => (
          <Rival
            key={asiento.id}
            asiento={asiento}
            activo={vista.turno === asiento.id && pendiente === null && enJuego}
            reveladas={vista.manosReveladas?.[asiento.id] ?? null}
            envido={vista.envidoValores[asiento.id]}
          />
        ))}
      </section>

      <section className="centro">
        <div className="bazas">
          {vista.bazas.map((baza) => (
            <BazaVista key={baza.numero} baza={baza} nombres={vista.asientos} />
          ))}
        </div>

        {pendiente ? (
          <div className="canto" role="status">
            <p>
              <strong>{nombreDe(vista, pendiente.deJugador)}</strong> cantó{" "}
              <strong className="dorado">{ETIQUETA_CANTO[pendiente.canto] ?? pendiente.canto}</strong>
            </p>
            {vista.envido && !vista.envido.resuelto && vista.envido.valorAcumulado > 0 && (
              <p className="detalle">Vale {vista.envido.valorAcumulado} puntos</p>
            )}
            <p className="reloj">{Math.ceil(restante / 1000)}s</p>
            {miRespondo && (
              <div className="botones">
                {acciones
                  .filter((nombre) => nombre.startsWith("RESPONDER_") || nombre.startsWith("CANTAR_"))
                  .map((nombre) => {
                    const accion = accionDesdeNombre(nombre);
                    if (!accion) return null;
                    return (
                      <button
                        key={nombre}
                        type="button"
                        className={estiloAccion(nombre)}
                        onClick={() => cliente.accion(accion)}
                      >
                        {ETIQUETA_ACCION[nombre] ?? nombre}
                      </button>
                    );
                  })}
              </div>
            )}
            {!miRespondo && <p className="detalle">Esperando a {nombreDe(vista, pendiente.respondedor)}…</p>}
          </div>
        ) : (
          <p className="turno">
            {textoTurno(vista)}
            {esMiTurno && <span className="turno-yo">Tocá una carta</span>}
          </p>
        )}
      </section>

      <section className={`mi-mano${elegida ? " con-elegida" : ""}`} aria-label="Tu mano">
        {vista.miMano.map((carta) => {
          const clave = `${carta.palo}-${carta.numero}`;
          const jugable = claveJugable.has(clave) && pendiente === null;
          return (
            <Carta
              key={clave}
              carta={carta}
              jugable={jugable}
              elegida={elegida !== null && mismaCarta(elegida, carta)}
              onClick={() => tocarCarta(carta)}
            />
          );
        })}
        {vista.miMano.length === 0 && <p className="vacio">Sin cartas</p>}
      </section>

      <footer className="acciones">
        {elegida ? (
          <p className="confirmar">Tocá la misma carta otra vez para jugarla</p>
        ) : (
          <p className="quien">
            {yo ? `${yo.nombre} · equipo ${yo.equipo === 0 ? "A" : "B"}` : ""}
          </p>
        )}
        <div className="botones">
          {accionesPie.map((nombre) => {
            const accion = accionDesdeNombre(nombre);
            if (!accion) return null;
            return (
              <button
                key={nombre}
                type="button"
                className={estiloAccion(nombre)}
                onClick={() => cliente.accion(accion)}
              >
                {ETIQUETA_ACCION[nombre] ?? nombre}
              </button>
            );
          })}
        </div>
      </footer>

      {vista.fase === "FIN_PARTIDA" && <Final vista={vista} />}
      {verHistoria && <Historia eventos={eventos} alCerrar={() => setVerHistoria(false)} />}
      {reconectando && <div className="reconectando">Reconectando…</div>}
    </main>
  );
}

// ---------------------------------------------------------------- piezas

function Puntos({ equipo, puntos, objetivo }: { equipo: number; puntos: number; objetivo: number }) {
  return (
    <div className={`puntos puntos-${equipo}`} title={`Equipo ${equipo === 0 ? "A" : "B"}`}>
      <span className="puntos-letra">{equipo === 0 ? "A" : "B"}</span>
      <span className="puntos-num">{puntos}</span>
      <span className="puntos-obj">/{objetivo}</span>
    </div>
  );
}

function Rival({
  asiento,
  activo,
  reveladas,
  envido,
}: {
  asiento: Asiento;
  activo: boolean;
  reveladas: CartaDto[] | null;
  envido?: number;
}) {
  return (
    <div
      className={`rival${activo ? " activo" : ""}${asiento.conectado ? "" : " desconectado"}${
        asiento.esMano ? " es-mano" : ""
      }`}
      data-equipo={asiento.equipo}
    >
      <span className="rival-nombre">
        {asiento.nombre}
        {asiento.esMano ? <em> · mano</em> : null}
      </span>

      <div className="rival-cartas">
        {reveladas
          ? reveladas.map((carta) => <Carta key={`${carta.palo}-${carta.numero}`} carta={carta} chica />)
          : Array.from({ length: asiento.cartasRestantes }, (_, indice) => (
              <span key={indice} className="dorso" aria-hidden="true" />
            ))}
      </div>

      <div className="rival-datos">
        {envido !== undefined && <span className="chip">envido {envido}</span>}
        {!asiento.conectado && <span className="chip chip-alerta">sin conexión</span>}
      </div>
    </div>
  );
}

function BazaVista({ baza, nombres }: { baza: Baza; nombres: Asiento[] }) {
  const ganador = baza.ganador !== null ? nombres[baza.ganador]?.nombre : null;
  return (
    <div className={`baza${baza.parda ? " parda" : ""}`}>
      <div className="baza-cartas">
        {baza.jugadas.map((jugada) => (
          <Carta
            key={`${jugada.jugador}-${jugada.carta.palo}-${jugada.carta.numero}`}
            carta={jugada.carta}
            chica
          />
        ))}
        {baza.jugadas.length === 0 && <span className="dorso baza-vacia" aria-hidden="true" />}
      </div>
      <span className="baza-pie">
        {baza.parda ? "parda" : ganador ? `gana ${ganador}` : "en juego"}
      </span>
    </div>
  );
}

function Final({ vista }: { vista: Vista }) {
  const gano = vista.partidaGanadaPor;
  const miEquipo = vista.asientos[vista.miJugadorId]?.equipo;
  const gane = gano !== null && gano === miEquipo;

  return (
    <div className="final" role="dialog" aria-modal="true">
      <div className="tarjeta">
        <p className="dorado">{gane ? "¡Ganaron!" : `Ganó el equipo ${gano === 0 ? "A" : "B"}`}</p>
        <h2>
          {vista.puntajes[0]} a {vista.puntajes[1]}
        </h2>
        <p className="detalle">La mesa vuelve al lobby en unos segundos para la revancha.</p>
      </div>
    </div>
  );
}

function Historia({ eventos, alCerrar }: { eventos: Evento[]; alCerrar: () => void }) {
  return (
    <aside className="historia" aria-label="Historia de la mano">
      <header>
        <h3>Lo que pasó</h3>
        <button type="button" className="icono" onClick={alCerrar} aria-label="Cerrar historia">
          ×
        </button>
      </header>
      <ol>
        {eventos.map((evento, indice) => (
          <li key={`${evento.tipo}-${indice}`}>{evento.texto}</li>
        ))}
        {eventos.length === 0 && <li className="detalle">Todavía no pasó nada.</li>}
      </ol>
    </aside>
  );
}

// ---------------------------------------------------------------- helpers

function nombreDe(vista: Vista, jugador: number): string {
  return vista.asientos[jugador]?.nombre ?? `Jugador ${jugador + 1}`;
}

function textoTurno(vista: Vista): string {
  switch (vista.fase) {
    case "MANO_CERRADA": {
      const equipo = vista.manoGanadaPor;
      return equipo === null
        ? "Mano cerrada"
        : `Mano para el equipo ${equipo === 0 ? "A" : "B"}: ${vista.puntajes[equipo]} puntos`;
    }
    case "FIN_PARTIDA":
      return "Partida terminada";
    case "FLOR":
      return "Manos con flor";
    case "JUEGO":
      return vista.turno === vista.miJugadorId ? "Tu turno" : `Juega ${nombreDe(vista, vista.turno)}`;
    default:
      return "Esperando…";
  }
}

function estiloAccion(nombre: string): string {
  if (nombre.startsWith("CANTAR_TRUCO") || nombre.startsWith("CANTAR_RETRUCO") || nombre.startsWith("CANTAR_VALE")) {
    return "peligro";
  }
  if (nombre === "RESPONDER_QUIERO") return "primario";
  if (nombre === "RESPONDER_NO_QUIERO") return "secundario";
  if (nombre === "IRSE_AL_MAZO") return "secundario";
  return "neutro";
}

function useRestante(deadlineMs: number | undefined): number {
  const [restante, setRestante] = useState(deadlineMs ?? 0);

  useEffect(() => {
    if (!deadlineMs) {
      setRestante(0);
      return;
    }
    const fin = Date.now() + deadlineMs;
    setRestante(deadlineMs);
    const timer = setInterval(() => setRestante(Math.max(0, fin - Date.now())), 200);
    return () => clearInterval(timer);
  }, [deadlineMs]);

  return restante;
}
