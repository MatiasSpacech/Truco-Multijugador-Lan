import { useEffect, useRef, useState } from "react";
import { etiquetaAccion, nombreCanto, nombreEquipo, t, usarIdioma } from "../i18n";
import type { Cliente } from "../net/cliente";
import { accionDesdeNombre, mismaCarta } from "../net/cliente";
import { cambiarPreferencias, preferencias } from "../preferencias";
import { sonar, vibrar } from "../sonido";
import type { Asiento, Baza, Carta as CartaDto, Evento, Vista } from "../tipos";
import { Carta } from "./Carta";
import { Panel } from "./Panel";
import type { Pestana } from "./Panel";
import { Tutorial } from "./Tutorial";

interface Props {
  vista: Vista;
  cliente: Cliente;
  eventos: Evento[];
  reconectando: boolean;
}

export function Mesa({ vista, cliente, eventos, reconectando }: Props) {
  usarIdioma();
  const [elegida, setElegida] = useState<CartaDto | null>(null);
  const [panel, setPanel] = useState<Pestana | null>(null);
  const [tutorial, setTutorial] = useState(() => !preferencias().tutorialVisto);

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
  const nombres = vista.asientos.map((asiento) => asiento.nombre);

  useEffect(() => {
    setElegida(null);
  }, [vista.seq]);

  // Si el teclado del ingreso dejó el documento scrolleado, volvemos arriba.
  useEffect(() => {
    window.scrollTo(0, 0);
  }, []);

  useSonidosDeEventos(eventos);
  useAvisoDeTurno(esMiTurno);

  function cerrarTutorial() {
    cambiarPreferencias({ tutorialVisto: true });
    setTutorial(false);
  }

  function tocarCarta(carta: CartaDto) {
    const clave = `${carta.palo}-${carta.numero}`;
    if (!claveJugable.has(clave) || pendiente !== null) return;
    if (elegida && mismaCarta(elegida, carta)) {
      cliente.accion({ tipo: "JUGAR_CARTA", carta });
      setElegida(null);
      vibrar(10);
      return;
    }
    setElegida(carta);
    vibrar(6);
  }

  const anuncio = pendiente
    ? t("mesa_canto", {
        nombre: nombreDe(vista, pendiente.deJugador),
        canto: nombreCanto(pendiente.canto),
      })
    : esMiTurno
      ? t("mesa_tu_turno")
      : t("mesa_turno_anuncio", { nombre: nombreDe(vista, vista.turno) });

  return (
    <main className={`mesa fase-${vista.fase.toLowerCase()}`} aria-label={t("mesa_aria_mesa")}>
      <header className="barra">
        <div className="tanteador">
          <Puntos equipo={0} puntos={vista.puntajes[0]} objetivo={vista.objetivoEtapa ?? vista.targetScore} />
          <Puntos equipo={1} puntos={vista.puntajes[1]} objetivo={vista.objetivoEtapa ?? vista.targetScore} />
        </div>

        <div className="mano-datos">
          {vista.etapa && vista.etapa !== "UNICA" && (
            <span className={`etapa etapa-${vista.etapa.toLowerCase()}`}>
              {vista.etapa === "MALAS" ? t("mesa_malas") : t("mesa_buenas")}
            </span>
          )}
          <span>{t("mesa_mano", { n: vista.manoNumero + 1 })}</span>
          <strong className={vista.valorMano > 1 ? "dorado" : ""}>
            {t("mesa_vale", { n: vista.valorMano })}
          </strong>
        </div>

        <button
          type="button"
          className="icono"
          onClick={() => setPanel((valor) => (valor ? null : "historia"))}
          aria-label={t("mesa_historia_aria")}
          aria-expanded={panel !== null}
        >
          ≡
        </button>
      </header>

      <section className="rivales" aria-label={t("mesa_aria_jugadores")}>
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
              <strong>{nombreDe(vista, pendiente.deJugador)}</strong> {t("mesa_canto_verbo")}
              <strong className="dorado"> {nombreCanto(pendiente.canto)}</strong>
            </p>
            {vista.envido && !vista.envido.resuelto && vista.envido.valorAcumulado > 0 && (
              <p className="detalle">{t("mesa_vale_puntos", { puntos: vista.envido.valorAcumulado })}</p>
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
                        {etiquetaAccion(nombre)}
                      </button>
                    );
                  })}
              </div>
            )}
            {!miRespondo && (
              <p className="detalle">{t("mesa_esperando_a", { nombre: nombreDe(vista, pendiente.respondedor) })}</p>
            )}
            <button type="button" className="enlace" onClick={() => setPanel("ayuda")}>
              {t("mesa_que_es")}
            </button>
          </div>
        ) : (
          <p className="turno">
            {textoTurno(vista)}
            {esMiTurno && <span className="turno-yo">{t("mesa_toca_carta")}</span>}
          </p>
        )}
      </section>

      <section
        key={vista.manoNumero}
        className={`mi-mano${elegida ? " con-elegida" : ""}`}
        aria-label={t("mesa_aria_mano")}
      >
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
        {vista.miMano.length === 0 && <p className="vacio">{t("mesa_sin_cartas")}</p>}
      </section>

      <footer className="acciones">
        {elegida ? (
          <p className="confirmar">{t("mesa_confirmar")}</p>
        ) : (
          <p className="quien">
            {yo ? `${yo.nombre} · ${t("equipo", { equipo: nombreEquipo(yo.equipo) })}` : ""}
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
                {etiquetaAccion(nombre)}
              </button>
            );
          })}
        </div>
      </footer>

      <p className="solo-lectores" role="status" aria-live="polite">
        {anuncio}
      </p>

      {vista.fase === "FIN_PARTIDA" && <Final vista={vista} />}
      {panel && (
        <Panel
          cliente={cliente}
          eventos={eventos}
          nombres={nombres}
          pestanaInicial={panel}
          alCerrar={() => setPanel(null)}
          alVerTutorial={() => {
            setPanel(null);
            setTutorial(true);
          }}
        />
      )}
      {tutorial && <Tutorial alCerrar={cerrarTutorial} />}
      {reconectando && <div className="reconectando">{t("mesa_reconectando")}</div>}
    </main>
  );
}

// ---------------------------------------------------------------- piezas

function Puntos({ equipo, puntos, objetivo }: { equipo: number; puntos: number; objetivo: number }) {
  return (
    <div className={`puntos puntos-${equipo}`} title={`${t("equipo", { equipo: nombreEquipo(equipo) })}`}>
      <span className="puntos-letra">{nombreEquipo(equipo)}</span>
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
        {envido !== undefined && <span className="chip">{t("mesa_envido_chip", { valor: envido })}</span>}
        {asiento.ausente && <span className="chip chip-alerta">{t("mesa_ausente")}</span>}
        {!asiento.conectado && !asiento.ausente && (
          <span className="chip chip-alerta">{t("mesa_sin_conexion")}</span>
        )}
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
        {baza.parda
          ? t("mesa_parda")
          : ganador
            ? t("mesa_gana", { nombre: ganador })
            : t("mesa_en_juego")}
      </span>
    </div>
  );
}

function Final({ vista }: { vista: Vista }) {
  const gano = vista.partidaGanadaPor;
  const miEquipo = vista.asientos[vista.miJugadorId]?.equipo;
  const gane = gano !== null && gano === miEquipo;

  return (
    <div className="final">
      <div className="tarjeta" role="dialog" aria-modal="true" aria-label={t("mesa_fin_partida")}>
        <p className="dorado">{gane ? t("mesa_ganaron") : t("mesa_gano_equipo", { equipo: nombreEquipo(gano) })}</p>
        <h2>
          {vista.puntajes[0]} a {vista.puntajes[1]}
        </h2>
        <p className="detalle">{t("mesa_vuelve_lobby")}</p>
        {vista.puntajesMalas && vista.puntajesMalas.length === 2 && (
          <p className="detalle">
            {t("mesa_malas_puntajes", { a: vista.puntajesMalas[0]!, b: vista.puntajesMalas[1]! })}
          </p>
        )}
      </div>
    </div>
  );
}

// ---------------------------------------------------------------- helpers

function nombreDe(vista: Vista, jugador: number): string {
  return vista.asientos[jugador]?.nombre ?? t("jugador_n", { n: jugador + 1 });
}

function textoTurno(vista: Vista): string {
  switch (vista.fase) {
    case "MANO_CERRADA": {
      const equipo = vista.manoGanadaPor;
      return equipo === null
        ? t("mesa_mano_cerrada")
        : t("mesa_mano_para", { equipo: nombreEquipo(equipo), puntos: vista.puntajes[equipo] });
    }
    case "FIN_PARTIDA":
      return t("mesa_fin_partida");
    case "FLOR":
      return t("mesa_manos_con_flor");
    case "JUEGO":
      return vista.turno === vista.miJugadorId
        ? t("mesa_tu_turno")
        : t("mesa_juega", { nombre: nombreDe(vista, vista.turno) });
    default:
      return t("mesa_esperando");
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

/** Sonidos y vibración de los eventos nuevos (los que ya estaban al entrar no suenan). */
function useSonidosDeEventos(eventos: Evento[]): void {
  const ultimo = useRef<Evento | null>(null);
  const arrancado = useRef(false);

  useEffect(() => {
    if (!arrancado.current) {
      arrancado.current = true;
      ultimo.current = eventos[0] ?? null;
      return;
    }
    const nuevos: Evento[] = [];
    for (const evento of eventos) {
      if (evento === ultimo.current) break;
      nuevos.push(evento);
    }
    if (eventos.length > 0) ultimo.current = eventos[0]!;

    for (const evento of nuevos.reverse()) {
      switch (evento.tipo) {
        case "REPARTO":
          sonar("reparto");
          break;
        case "CARTA_JUGADA":
          sonar("carta");
          vibrar(8);
          break;
        case "CANTO":
          sonar("canto");
          vibrar([15, 30, 15]);
          break;
        case "BAZA_RESUELTA":
          sonar("carta");
          break;
        case "ENVIDO_RESUELTO":
        case "TRUCO_RESUELTO":
        case "FLOR_RESUELTA":
        case "MANO_GANADA":
        case "FIN_DE_MALAS":
          sonar("punto");
          break;
        case "PARTIDA_GANADA":
          sonar("fin");
          break;
        default:
          break;
      }
    }
  }, [eventos]);
}

/** Un toque de atención cuando pasa a ser tu turno (no al recargar la página). */
function useAvisoDeTurno(esMiTurno: boolean): void {
  const anterior = useRef(esMiTurno);
  const arrancado = useRef(false);

  useEffect(() => {
    if (!arrancado.current) {
      arrancado.current = true;
      anterior.current = esMiTurno;
      return;
    }
    if (esMiTurno && !anterior.current) {
      sonar("turno");
      vibrar(14);
    }
    anterior.current = esMiTurno;
  }, [esMiTurno]);
}
