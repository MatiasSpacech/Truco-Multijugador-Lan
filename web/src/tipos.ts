/**
 * Tipos del protocolo. Espejo de `protocol/src/main/kotlin/ar/truco/protocol/Mensajes.kt`.
 * Si cambia el protocolo, subir PROTOCOL_VERSION en ambos lados.
 */
export const PROTOCOL_VERSION = 1;

export type Palo = "ESPADA" | "BASTO" | "ORO" | "COPA";

export interface Carta {
  palo: Palo;
  numero: number;
}

export interface Sobre<T = unknown> {
  t: string;
  id?: string;
  v: number;
  d: T;
}

export interface Config {
  jugadores: number;
  targetScore: number;
  flor: string;
  envido: boolean;
  realEnvido: boolean;
  faltaEnvido: boolean;
  trucoAntesDeEnvido: boolean;
  timeoutCantoMs: number;
  timeoutTurnoMs: number;
}

export interface Asiento {
  id: number;
  nombre: string;
  equipo: number;
  conectado: boolean;
  listo: boolean;
  esHost: boolean;
  esMano: boolean;
  cartasRestantes: number;
}

export interface Jugada {
  jugador: number;
  carta: Carta;
}

export interface Baza {
  numero: number;
  jugadas: Jugada[];
  ganador: number | null;
  parda: boolean;
}

export interface CantoPendiente {
  canto: string;
  deJugador: number;
  respondedor: number;
  deadlineMs: number;
}

export interface Envido {
  declaraciones: string[];
  valorAcumulado: number;
  resuelto: boolean;
}

export interface Flor {
  declaraciones: number[];
  valores: number[];
}

export interface Vista {
  seq: number;
  fase: "ESPERANDO" | "FLOR" | "JUEGO" | "MANO_CERRADA" | "FIN_PARTIDA";
  miJugadorId: number;
  asientos: Asiento[];
  puntajes: number[];
  targetScore: number;
  manoIndex: number;
  manoNumero: number;
  turno: number;
  valorMano: number;
  miMano: Carta[];
  manosReveladas: Carta[][] | null;
  bazas: Baza[];
  cantoPendiente: CantoPendiente | null;
  envido: Envido | null;
  flor: Flor | null;
  legalActions: string[];
  cartasJugables: Carta[];
  envidoValores: number[];
  faltaEnvido: number;
  manoGanadaPor: number | null;
  partidaGanadaPor: number | null;
}

export interface Lobby {
  codigo: string;
  config: Config;
  asientos: Asiento[];
  jugadores: number;
  partidaEnCurso: boolean;
}

export interface Evento {
  tipo: string;
  texto: string;
  jugador?: number;
  carta?: Carta;
  baza?: number;
  canto?: string;
  puntos?: number;
  equipo?: number;
}

export interface Welcome {
  protocolVersion: number;
  serverVersion: string;
  codigo: string;
  config: Config;
  tuJugadorId: number | null;
  playerToken: string | null;
}

export interface ErrorServidor {
  code: string;
  mensaje: string;
  fatal: boolean;
}

export type Accion =
  | { tipo: "JUGAR_CARTA"; carta: Carta }
  | { tipo: "CANTAR"; canto: string }
  | { tipo: "RESPONDER"; respuesta: "QUIERO" | "NO_QUIERO" }
  | { tipo: "IRSE_AL_MAZO" };

export interface Info {
  appVersion: string;
  protocolVersion: number;
  codigo: string;
  jugadores: number;
  maxJugadores: number;
  fase: string;
  partidaEnCurso: boolean;
}
