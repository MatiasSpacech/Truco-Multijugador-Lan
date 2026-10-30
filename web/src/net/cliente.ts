import type { Accion, Carta, Evento, Lobby, Palo, Vista, Welcome } from "../tipos";
import { PROTOCOL_VERSION } from "../tipos";

const CLAVE_TOKEN = "truco.token";
const CLAVE_NOMBRE = "truco.nombre";

/**
 * `crypto.randomUUID()` solo existe en contexto seguro (https o localhost).
 * En una LAN por `http://192.168.x.x` hay que tener un fallback.
 */
export function uuid(): string {
  if (typeof crypto !== "undefined" && "randomUUID" in crypto) {
    try {
      return crypto.randomUUID();
    } catch {
      /* seguimos con el fallback */
    }
  }
  return `${Date.now().toString(36)}-${Math.random().toString(36).slice(2, 10)}`;
}

export type FaseConexion = "iniciando" | "online" | "reconectando" | "cerrado";

export interface EstadoConexion {
  fase: FaseConexion;
  miId: number | null;
  token: string | null;
  lobby: Lobby | null;
  vista: Vista | null;
  eventos: Evento[];
  aviso: string | null;
  motivo: string | null;
}

export class Cliente {
  private ws: WebSocket | null = null;
  private oyentes = new Set<() => void>();
  private estado: EstadoConexion = {
    fase: "iniciando",
    miId: null,
    token: localStorage.getItem(CLAVE_TOKEN),
    lobby: null,
    vista: null,
    eventos: [],
    aviso: null,
    motivo: null,
  };
  private nombre: string | null = localStorage.getItem(CLAVE_NOMBRE);
  private intentos = 0;
  private timerReconexion: ReturnType<typeof setTimeout> | null = null;
  private timerPing: ReturnType<typeof setInterval> | null = null;
  private cerrado = false;

  constructor(private readonly url: string) {}

  obtener = (): EstadoConexion => this.estado;

  suscribir = (oyente: () => void): (() => void) => {
    this.oyentes.add(oyente);
    return () => {
      this.oyentes.delete(oyente);
    };
  };

  nombreGuardado = (): string => this.nombre ?? "";

  iniciar(): void {
    if (!this.ws && !this.cerrado) this.conectar();
  }

  entrar(nombre: string): void {
    const limpio = nombre.trim().slice(0, 24);
    if (!limpio) return;
    this.nombre = limpio;
    localStorage.setItem(CLAVE_NOMBRE, limpio);

    if (this.ws?.readyState === WebSocket.OPEN) {
      this.enviar("join", { nombre: limpio });
      return;
    }
    this.conectar();
  }

  listo(listo: boolean): void {
    this.enviar("ready", { listo });
  }

  accion(accion: Accion): void {
    const vista = this.estado.vista;
    if (!vista) return;
    this.enviar("action", { stateSeq: vista.seq, accion }, uuid());
  }

  limpiarAviso(): void {
    if (this.estado.aviso !== null) this.actualizar({ aviso: null });
  }

  /** Al volver a la pestaña: si el socket quedó muerto, reconectamos ya. */
  reintentarAhora(): void {
    if (this.cerrado) return;
    if (this.ws?.readyState === WebSocket.OPEN) return;
    if (this.timerReconexion) {
      clearTimeout(this.timerReconexion);
      this.timerReconexion = null;
    }
    this.conectar();
  }

  // ------------------------------------------------------------------ interno

  private actualizar(cambios: Partial<EstadoConexion>): void {
    this.estado = { ...this.estado, ...cambios };
    this.oyentes.forEach((oyente) => oyente());
  }

  private conectar(): void {
    if (this.cerrado) return;
    if (this.ws && (this.ws.readyState === WebSocket.OPEN || this.ws.readyState === WebSocket.CONNECTING)) return;

    this.actualizar({ fase: this.intentos === 0 ? "iniciando" : "reconectando", motivo: null });

    const ws = new WebSocket(this.url);
    this.ws = ws;

    ws.onopen = () => {
      this.intentos = 0;
      this.actualizar({ fase: "online", aviso: null });
      this.enviar("hello", { nombre: this.nombre ?? "", protocolVersion: PROTOCOL_VERSION });

      const token = localStorage.getItem(CLAVE_TOKEN);
      if (token) this.enviar("rejoin", { playerToken: token });
      else if (this.nombre) this.enviar("join", { nombre: this.nombre });

      this.iniciarPing();
    };

    ws.onmessage = (evento) => this.recibir(String(evento.data));

    ws.onclose = () => {
      this.detenerPing();
      this.ws = null;
      if (this.cerrado) return;
      this.actualizar({ fase: "reconectando" });
      this.programarReconexion();
    };

    ws.onerror = () => {
      /* el cierre siempre viene después: ahí reconectamos */
    };
  }

  private programarReconexion(): void {
    if (this.cerrado || this.timerReconexion) return;
    const espera = Math.min(800 * 2 ** this.intentos, 6_000);
    this.intentos += 1;
    this.timerReconexion = setTimeout(() => {
      this.timerReconexion = null;
      this.conectar();
    }, espera);
  }

  private iniciarPing(): void {
    this.detenerPing();
    this.timerPing = setInterval(() => this.enviar("ping", { ts: Date.now() }), 10_000);
  }

  private detenerPing(): void {
    if (this.timerPing) {
      clearInterval(this.timerPing);
      this.timerPing = null;
    }
  }

  private enviar(tipo: string, payload: unknown, id?: string): void {
    if (this.ws?.readyState !== WebSocket.OPEN) return;
    const sobre = { t: tipo, v: PROTOCOL_VERSION, d: payload ?? {}, ...(id ? { id } : {}) };
    this.ws.send(JSON.stringify(sobre));
  }

  private recibir(texto: string): void {
    let sobre: { t: string; d: unknown };
    try {
      sobre = JSON.parse(texto) as { t: string; d: unknown };
    } catch {
      return;
    }

    switch (sobre.t) {
      case "welcome": {
        const welcome = sobre.d as Welcome;
        if (welcome.playerToken) {
          localStorage.setItem(CLAVE_TOKEN, welcome.playerToken);
          this.actualizar({ token: welcome.playerToken });
        }
        this.actualizar({ miId: welcome.tuJugadorId ?? null });
        break;
      }

      case "lobby": {
        const lobby = sobre.d as Lobby;
        // Si la sala volvió al lobby (revancha), salimos de la mesa.
        this.actualizar(lobby.partidaEnCurso ? { lobby } : { lobby, vista: null, eventos: [] });
        break;
      }

      case "state":
        this.actualizar({ vista: sobre.d as Vista });
        break;

      case "events": {
        const { eventos } = sobre.d as { eventos: Evento[] };
        this.actualizar({ eventos: [...eventos, ...this.estado.eventos].slice(0, 80) });
        break;
      }

      case "error": {
        const error = sobre.d as { code: string; mensaje: string; fatal: boolean };
        this.manejarError(error);
        break;
      }

      case "ended": {
        const { motivo } = sobre.d as { motivo: string };
        this.cerrado = true;
        this.actualizar({ fase: "cerrado", motivo });
        this.ws?.close();
        break;
      }

      case "pong":
        break;

      default:
        break;
    }
  }

  private manejarError(error: { code: string; mensaje: string; fatal: boolean }): void {
    if (error.fatal) {
      this.cerrado = true;
      this.actualizar({ fase: "cerrado", motivo: error.mensaje });
      return;
    }

    if (error.code === "TOKEN_INVALIDO") {
      localStorage.removeItem(CLAVE_TOKEN);
      this.actualizar({ token: null });
      if (this.nombre) this.enviar("join", { nombre: this.nombre });
      return;
    }

    if (error.code === "SALA_LLENA" || error.code === "PARTIDA_EN_CURSO") {
      this.actualizar({ miId: null, vista: null });
      localStorage.removeItem(CLAVE_TOKEN);
      this.actualizar({ token: null, aviso: error.mensaje });
      return;
    }

    this.actualizar({ aviso: error.mensaje });
  }
}

/** URL del WebSocket relativa al origen que sirvió la página. */
export function urlWebSocket(): string {
  const protocolo = window.location.protocol === "https:" ? "wss:" : "ws:";
  return `${protocolo}//${window.location.host}/ws`;
}

// ------------------------------------------------------------------ utilidades de presentación

export const NOMBRE_PALO: Record<Palo, string> = {
  ESPADA: "espada",
  BASTO: "basto",
  ORO: "oro",
  COPA: "copa",
};

export function nombreCarta(carta: Carta): string {
  return `${carta.numero} de ${NOMBRE_PALO[carta.palo]}`;
}

export function mismaCarta(a: Carta, b: Carta): boolean {
  return a.palo === b.palo && a.numero === b.numero;
}

export const ETIQUETA_CANTO: Record<string, string> = {
  ENVIDO: "envido",
  REAL_ENVIDO: "real envido",
  FALTA_ENVIDO: "falta envido",
  TRUCO: "truco",
  RETRUCO: "retruco",
  VALE_CUATRO: "vale cuatro",
  FLOR: "flor",
  CONTRAFLOR: "contraflor",
  CONTRAFLOR_AL_RESTO: "contraflor al resto",
};

export const ETIQUETA_ACCION: Record<string, string> = {
  CANTAR_ENVIDO: "Envido",
  CANTAR_REAL_ENVIDO: "Real envido",
  CANTAR_FALTA_ENVIDO: "Falta envido",
  CANTAR_TRUCO: "¡Truco!",
  CANTAR_RETRUCO: "¡Retruco!",
  CANTAR_VALE_CUATRO: "¡Vale cuatro!",
  CANTAR_FLOR: "Flor",
  CANTAR_CONTRAFLOR: "Contraflor",
  CANTAR_CONTRAFLOR_AL_RESTO: "Contraflor al resto",
  IRSE_AL_MAZO: "Me voy al mazo",
  RESPONDER_QUIERO: "Quiero",
  RESPONDER_NO_QUIERO: "No quiero",
};

export function accionDesdeNombre(nombre: string): Accion | null {
  if (nombre.startsWith("CANTAR_")) {
    const canto = nombre.slice("CANTAR_".length);
    return { tipo: "CANTAR", canto };
  }
  switch (nombre) {
    case "RESPONDER_QUIERO":
      return { tipo: "RESPONDER", respuesta: "QUIERO" };
    case "RESPONDER_NO_QUIERO":
      return { tipo: "RESPONDER", respuesta: "NO_QUIERO" };
    case "IRSE_AL_MAZO":
      return { tipo: "IRSE_AL_MAZO" };
    default:
      return null;
  }
}
