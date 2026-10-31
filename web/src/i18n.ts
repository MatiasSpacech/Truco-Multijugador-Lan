import { useSyncExternalStore } from "react";
import { preferencias, suscribirPreferencias } from "./preferencias";
import type { Carta, Evento, Palo } from "./tipos";

export type Idioma = "es" | "en";

/**
 * Textos de la interfaz. `es` es la fuente de verdad del tipado: si falta una clave
 * en `en`, TypeScript falla en el build (no hay huecos silenciosos).
 *
 * Los nombres de los cantos se dejan en español también en inglés: son nombres propios
 * del juego (envido, truco, retruco, vale cuatro, flor).
 */
const ES = {
  // ---------------------------------------------------------------- general
  ajustes: "Ajustes",
  ayuda: "Ayuda",
  cerrar: "Cerrar",
  si: "Sí",
  no: "No",
  equipo: "equipo {equipo}",
  jugador_n: "Jugador {n}",

  // ---------------------------------------------------------------- app
  app_entrando: "Entrando a la sala…",
  app_cerrada_titulo: "Se cerró la mesa",
  app_host_salio: "El anfitrión cerró la partida.",
  app_volver: "Volver a entrar",

  // ---------------------------------------------------------------- ingreso
  ingreso_subtitulo: "Multijugador en la red local",
  ingreso_nombre: "Tu nombre",
  ingreso_placeholder: "Ej.: Ana",
  ingreso_entrar: "Entrar a la mesa",
  ingreso_conectando: "Conectando…",
  ingreso_pie: "2, 4 o 6 jugadores. Todos tienen que estar en la misma red Wi-Fi que el anfitrión.",
  ingreso_como_jugar: "¿Cómo se juega?",

  // ---------------------------------------------------------------- lobby
  lobby_sala: "Sala",
  lobby_esperando: "Esperando jugadores",
  lobby_resumen: "{jugadores} jugadores · a {objetivo}",
  lobby_malas_buenas: "(malas y buenas)",
  lobby_con_flor: "con flor",
  lobby_sin_flor: "sin flor",
  lobby_en_mesa: "{dentro}/{total} en la mesa · {listos} listo(s)",
  lobby_anfitrion: " · anfitrión",
  lobby_sin_conexion: "sin conexión",
  lobby_listo: "listo",
  lobby_esperando_estado: "esperando",
  lobby_lugar_libre: "Lugar libre",
  lobby_estoy_listo: "Estoy listo",
  lobby_cancelar: "Cancelar",
  lobby_pie:
    "Cuando estén todos listos, la partida empieza sola. Los compañeros de equipo juegan enfrentados: los asientos impares son del equipo A y los pares del equipo B.",
  lobby_como_jugar: "¿Cómo se juega?",

  // ---------------------------------------------------------------- mesa
  mesa_historia_aria: "Historia, ayuda y ajustes",
  mesa_historia: "Lo que pasó",
  mesa_cerrar_historia: "Cerrar la historia",
  mesa_nada: "Todavía no pasó nada.",
  mesa_toca_carta: "Tocá una carta",
  mesa_confirmar: "Tocá la misma carta otra vez para jugarla",
  mesa_tu_turno: "Tu turno",
  mesa_juega: "Juega {nombre}",
  mesa_esperando: "Esperando…",
  mesa_mano_cerrada: "Mano cerrada",
  mesa_mano_para: "Mano para el equipo {equipo}: {puntos} puntos",
  mesa_fin_partida: "Partida terminada",
  mesa_manos_con_flor: "Manos con flor",
  mesa_malas: "Malas",
  mesa_buenas: "Buenas",
  mesa_mano: "Mano {n}",
  mesa_vale: "Vale {n}",
  mesa_canto: "{nombre} cantó {canto}",
  mesa_canto_verbo: "cantó",
  mesa_vale_puntos: "Vale {puntos} puntos",
  mesa_esperando_a: "Esperando a {nombre}…",
  mesa_que_es: "¿Qué es esto?",
  mesa_parda: "parda",
  mesa_gana: "gana {nombre}",
  mesa_en_juego: "en juego",
  mesa_sin_cartas: "Sin cartas",
  mesa_ausente: "ausente",
  mesa_sin_conexion: "sin conexión",
  mesa_envido_chip: "envido {valor}",
  mesa_ganaron: "¡Ganaron!",
  mesa_gano_equipo: "Ganó el equipo {equipo}",
  mesa_vuelve_lobby: "La mesa vuelve al lobby en unos segundos para la revancha.",
  mesa_malas_puntajes: "Malas: {a} a {b}",
  mesa_reconectando: "Reconectando…",
  mesa_aria_jugadores: "Jugadores",
  mesa_aria_mano: "Tu mano",
  mesa_aria_panel: "Mesa: historia, ayuda y ajustes",
  mesa_aria_mesa: "Mesa de juego",
  mesa_turno_anuncio: "Turno de {nombre}",

  // ---------------------------------------------------------------- acciones y cantos
  accion_envido: "Envido",
  accion_real_envido: "Real envido",
  accion_falta_envido: "Falta envido",
  accion_truco: "¡Truco!",
  accion_retruco: "¡Retruco!",
  accion_vale_cuatro: "¡Vale cuatro!",
  accion_flor: "Flor",
  accion_contraflor: "Contraflor",
  accion_contraflor_al_resto: "Contraflor al resto",
  accion_mazo: "Me voy al mazo",
  accion_quiero: "Quiero",
  accion_no_quiero: "No quiero",
  canto_envido: "envido",
  canto_real_envido: "real envido",
  canto_falta_envido: "falta envido",
  canto_truco: "truco",
  canto_retruco: "retruco",
  canto_vale_cuatro: "vale cuatro",
  canto_flor: "flor",
  canto_contraflor: "contraflor",
  canto_contraflor_al_resto: "contraflor al resto",

  // ---------------------------------------------------------------- ajustes
  ajustes_idioma: "Idioma",
  ajustes_tema: "Tema",
  ajustes_tema_auto: "Automático",
  ajustes_tema_claro: "Claro",
  ajustes_tema_oscuro: "Oscuro",
  ajustes_sonido: "Sonido",
  ajustes_vibracion: "Vibración",
  ajustes_probar_sonido: "Probar",
  ajustes_tutorial_titulo: "Tutorial",
  ajustes_tutorial: "Ver el tutorial",
  ajustes_diagnostico: "Diagnóstico",
  ajustes_diagnostico_texto: "Si algo falla, descargá el log y pasáselo al anfitrión.",
  ajustes_descargar: "Descargar log",
  ajustes_copiar: "Copiar",
  ajustes_copiado: "Copiado",

  // ---------------------------------------------------------------- tutorial
  tutorial_titulo: "Cómo jugar",
  tutorial_paso: "Paso {n} de 3",
  tutorial_siguiente: "Siguiente",
  tutorial_anterior: "Atrás",
  tutorial_listo: "¡A jugar!",
  tutorial_saltar: "Saltear",
  tutorial_1_titulo: "Tus cartas",
  tutorial_1_texto:
    "Tenés tres cartas. Cuando sea tu turno, tocá una y confirmá tocándola de nuevo.",
  tutorial_2_titulo: "Los cantos",
  tutorial_2_texto:
    "Con cartas del mismo palo podés cantar envido; el truco sube lo que vale la mano. Solo aparecen los cantos que podés hacer.",
  tutorial_3_titulo: "El resto es automático",
  tutorial_3_texto:
    "El servidor reparte, cuenta y decide. Si se corta la conexión, al recargar volvés a tu silla y a tu mano.",

  // ---------------------------------------------------------------- ayuda
  ayuda_titulo: "Cómo se juega",
  ayuda_intro:
    "Gana el primero que llega al objetivo. Cada mano vale 1 punto, salvo que se cante truco.",
  ayuda_cartas_titulo: "Las cartas",
  ayuda_cartas_texto:
    "De mayor a menor: 1 de espada, 1 de basto, 7 de espada, 7 de oro, los 3, los 2, los 1, los 12, los 11, los 10, los 7, los 6, los 5 y los 4.",
  ayuda_envido_titulo: "Envido",
  ayuda_envido_texto:
    "Con dos cartas del mismo palo: 20 + el valor de las otras dos (las figuras valen 0). Se canta antes de la primera baza.",
  ayuda_truco_titulo: "Truco",
  ayuda_truco_texto:
    "Truco vale 2, retruco 3 y vale cuatro 4. Si el rival no quiere, te llevás los puntos que ya estaban en juego.",
  ayuda_flor_titulo: "Flor",
  ayuda_flor_texto:
    "Tres cartas del mismo palo: 3 puntos (6 con contraflor). Se canta antes que el envido.",
  ayuda_parda_titulo: "Pardas",
  ayuda_parda_texto:
    "Si empatan una baza la gana quien venía ganando; si es la primera, la gana el mano.",
  ayuda_malas_titulo: "Malas y buenas",
  ayuda_malas_texto:
    "A 30 se juega en dos etapas: al llegar a 15 se cierra la primera y el marcador vuelve a cero.",
  ayuda_cerrar: "Entendido",

  // ---------------------------------------------------------------- eventos
  evento_reparto: "Se repartió la mano {n}",
  evento_carta: "{jugador} jugó {carta}",
  evento_baza: "{jugador} ganó la baza {n}",
  evento_baza_parda: "Parda en la baza {n}: la gana {jugador}",
  evento_canto: "{jugador} cantó {canto}",
  evento_respuesta: "{jugador} dijo {respuesta}",
  evento_quiero: "quiero",
  evento_no_quiero: "no quiero",
  evento_envido: "Envido: {puntos} punto(s) para el equipo {equipo} ({valores})",
  evento_envido_rechazado: "Envido rechazado: {puntos} punto(s) para el equipo {equipo}",
  evento_truco_aceptado: "Truco aceptado: la mano vale {valor}",
  evento_truco_rechazado: "Truco rechazado: {puntos} punto(s)",
  evento_flor: "Flor: {puntos} punto(s) para el equipo {equipo} ({valores})",
  evento_fin_malas:
    "El equipo {equipo} ganó las malas ({puntajes}). Ahora van las buenas, desde cero.",
  evento_mano: "El equipo {equipo} ganó la mano: {puntos} punto(s)",
  evento_partida: "¡El equipo {equipo} ganó la partida!",
  evento_mazo: "{jugador} se fue al mazo",
} as const;

type Clave = keyof typeof ES;

const EN: Record<Clave, string> = {
  ajustes: "Settings",
  ayuda: "Help",
  cerrar: "Close",
  si: "On",
  no: "Off",
  equipo: "team {equipo}",
  jugador_n: "Player {n}",

  app_entrando: "Joining the room…",
  app_cerrada_titulo: "The table closed",
  app_host_salio: "The host ended the game.",
  app_volver: "Join again",

  ingreso_subtitulo: "Local network multiplayer",
  ingreso_nombre: "Your name",
  ingreso_placeholder: "e.g. Ana",
  ingreso_entrar: "Join the table",
  ingreso_conectando: "Connecting…",
  ingreso_pie: "2, 4 or 6 players. Everyone must be on the same Wi-Fi network as the host.",
  ingreso_como_jugar: "How to play?",

  lobby_sala: "Room",
  lobby_esperando: "Waiting for players",
  lobby_resumen: "{jugadores} players · to {objetivo}",
  lobby_malas_buenas: "(first to 15, then to 30)",
  lobby_con_flor: "with flor",
  lobby_sin_flor: "no flor",
  lobby_en_mesa: "{dentro}/{total} at the table · {listos} ready",
  lobby_anfitrion: " · host",
  lobby_sin_conexion: "offline",
  lobby_listo: "ready",
  lobby_esperando_estado: "waiting",
  lobby_lugar_libre: "Open seat",
  lobby_estoy_listo: "I'm ready",
  lobby_cancelar: "Cancel",
  lobby_pie:
    "When everyone is ready the game starts on its own. Teammates sit across from each other: odd seats are team A and even seats are team B.",
  lobby_como_jugar: "How to play?",

  mesa_historia_aria: "History, help and settings",
  mesa_historia: "What happened",
  mesa_cerrar_historia: "Close the history",
  mesa_nada: "Nothing yet.",
  mesa_toca_carta: "Tap a card",
  mesa_confirmar: "Tap the same card again to play it",
  mesa_tu_turno: "Your turn",
  mesa_juega: "{nombre} to play",
  mesa_esperando: "Waiting…",
  mesa_mano_cerrada: "Hand closed",
  mesa_mano_para: "Hand for team {equipo}: {puntos} points",
  mesa_fin_partida: "Game over",
  mesa_manos_con_flor: "Hands with flor",
  mesa_malas: "First half",
  mesa_buenas: "Second half",
  mesa_mano: "Hand {n}",
  mesa_vale: "Worth {n}",
  mesa_canto: "{nombre} called {canto}",
  mesa_canto_verbo: "called",
  mesa_vale_puntos: "Worth {puntos} points",
  mesa_esperando_a: "Waiting for {nombre}…",
  mesa_que_es: "What is this?",
  mesa_parda: "tie",
  mesa_gana: "{nombre} wins",
  mesa_en_juego: "in play",
  mesa_sin_cartas: "No cards",
  mesa_ausente: "away",
  mesa_sin_conexion: "offline",
  mesa_envido_chip: "envido {valor}",
  mesa_ganaron: "You won!",
  mesa_gano_equipo: "Team {equipo} won",
  mesa_vuelve_lobby: "Back to the lobby in a few seconds for a rematch.",
  mesa_malas_puntajes: "First half: {a} to {b}",
  mesa_reconectando: "Reconnecting…",
  mesa_aria_jugadores: "Players",
  mesa_aria_mano: "Your hand",
  mesa_aria_panel: "Table: history, help and settings",
  mesa_aria_mesa: "Card table",
  mesa_turno_anuncio: "{nombre}'s turn",

  accion_envido: "Envido",
  accion_real_envido: "Real envido",
  accion_falta_envido: "Falta envido",
  accion_truco: "Truco!",
  accion_retruco: "Retruco!",
  accion_vale_cuatro: "Vale cuatro!",
  accion_flor: "Flor",
  accion_contraflor: "Contraflor",
  accion_contraflor_al_resto: "Contraflor al resto",
  accion_mazo: "I fold",
  accion_quiero: "I accept",
  accion_no_quiero: "No",
  canto_envido: "envido",
  canto_real_envido: "real envido",
  canto_falta_envido: "falta envido",
  canto_truco: "truco",
  canto_retruco: "retruco",
  canto_vale_cuatro: "vale cuatro",
  canto_flor: "flor",
  canto_contraflor: "contraflor",
  canto_contraflor_al_resto: "contraflor al resto",

  ajustes_idioma: "Language",
  ajustes_tema: "Theme",
  ajustes_tema_auto: "Auto",
  ajustes_tema_claro: "Light",
  ajustes_tema_oscuro: "Dark",
  ajustes_sonido: "Sound",
  ajustes_vibracion: "Vibration",
  ajustes_probar_sonido: "Try it",
  ajustes_tutorial_titulo: "Tutorial",
  ajustes_tutorial: "Show the tutorial",
  ajustes_diagnostico: "Diagnostics",
  ajustes_diagnostico_texto: "If something breaks, download the log and hand it to the host.",
  ajustes_descargar: "Download log",
  ajustes_copiar: "Copy",
  ajustes_copiado: "Copied",

  tutorial_titulo: "How to play",
  tutorial_paso: "Step {n} of 3",
  tutorial_siguiente: "Next",
  tutorial_anterior: "Back",
  tutorial_listo: "Let's play",
  tutorial_saltar: "Skip",
  tutorial_1_titulo: "Your cards",
  tutorial_1_texto: "You have three cards. On your turn, tap one and confirm by tapping it again.",
  tutorial_2_titulo: "The calls",
  tutorial_2_texto:
    "Two cards of the same suit let you call envido; truco raises what the hand is worth. Only the calls you can make show up.",
  tutorial_3_titulo: "The rest is automatic",
  tutorial_3_texto:
    "The server deals, scores and decides. If the connection drops, reload and you get your seat and your hand back.",

  ayuda_titulo: "How to play",
  ayuda_intro: "First to the target score wins. Each hand is worth 1 point unless truco is called.",
  ayuda_cartas_titulo: "The cards",
  ayuda_cartas_texto:
    "High to low: ace of swords, ace of clubs, seven of swords, seven of golds, the threes, the twos, the aces, the 12s, the 11s, the 10s, the sevens, the sixes, the fives and the fours.",
  ayuda_envido_titulo: "Envido",
  ayuda_envido_texto:
    "With two cards of the same suit: 20 + the value of the other two (figures count 0). It is called before the first trick.",
  ayuda_truco_titulo: "Truco",
  ayuda_truco_texto:
    "Truco is worth 2, retruco 3 and vale cuatro 4. If the other side declines, you still take the points at stake.",
  ayuda_flor_titulo: "Flor",
  ayuda_flor_texto:
    "Three cards of the same suit: 3 points (6 with contraflor). It is called before envido.",
  ayuda_parda_titulo: "Ties",
  ayuda_parda_texto:
    "A tied trick goes to whoever was already winning; the first one goes to the hand player.",
  ayuda_malas_titulo: "First and second half",
  ayuda_malas_texto:
    "Playing to 30 has two halves: at 15 the first one closes and the score goes back to zero.",
  ayuda_cerrar: "Got it",

  evento_reparto: "Hand {n} dealt",
  evento_carta: "{jugador} played {carta}",
  evento_baza: "{jugador} won trick {n}",
  evento_baza_parda: "Trick {n} tied: {jugador} takes it",
  evento_canto: "{jugador} called {canto}",
  evento_respuesta: "{jugador} said {respuesta}",
  evento_quiero: "yes",
  evento_no_quiero: "no",
  evento_envido: "Envido: {puntos} point(s) for team {equipo} ({valores})",
  evento_envido_rechazado: "Envido declined: {puntos} point(s) for team {equipo}",
  evento_truco_aceptado: "Truco accepted: the hand is worth {valor}",
  evento_truco_rechazado: "Truco declined: {puntos} point(s)",
  evento_flor: "Flor: {puntos} point(s) for team {equipo} ({valores})",
  evento_fin_malas: "Team {equipo} won the first half ({puntajes}). Now the second half, from zero.",
  evento_mano: "Team {equipo} won the hand: {puntos} point(s)",
  evento_partida: "Team {equipo} won the game!",
  evento_mazo: "{jugador} folded",
};

const TEXTOS: Record<Idioma, Record<Clave, string>> = { es: ES, en: EN };

const PALOS: Record<Idioma, Record<Palo, string>> = {
  es: { ESPADA: "espada", BASTO: "basto", ORO: "oro", COPA: "copa" },
  en: { ESPADA: "swords", BASTO: "clubs", ORO: "golds", COPA: "cups" },
};

const CONECTOR_CARTA: Record<Idioma, string> = { es: "de", en: "of" };

const FIGURAS: Record<Idioma, Record<number, string>> = {
  es: { 10: "sota", 11: "caballo", 12: "rey" },
  en: { 10: "jack", 11: "knight", 12: "king" },
};

const CANTO_CLAVE: Record<string, Clave> = {
  ENVIDO: "canto_envido",
  REAL_ENVIDO: "canto_real_envido",
  FALTA_ENVIDO: "canto_falta_envido",
  TRUCO: "canto_truco",
  RETRUCO: "canto_retruco",
  VALE_CUATRO: "canto_vale_cuatro",
  FLOR: "canto_flor",
  CONTRAFLOR: "canto_contraflor",
  CONTRAFLOR_AL_RESTO: "canto_contraflor_al_resto",
};

const ACCION_CLAVE: Record<string, Clave> = {
  CANTAR_ENVIDO: "accion_envido",
  CANTAR_REAL_ENVIDO: "accion_real_envido",
  CANTAR_FALTA_ENVIDO: "accion_falta_envido",
  CANTAR_TRUCO: "accion_truco",
  CANTAR_RETRUCO: "accion_retruco",
  CANTAR_VALE_CUATRO: "accion_vale_cuatro",
  CANTAR_FLOR: "accion_flor",
  CANTAR_CONTRAFLOR: "accion_contraflor",
  CANTAR_CONTRAFLOR_AL_RESTO: "accion_contraflor_al_resto",
  IRSE_AL_MAZO: "accion_mazo",
  RESPONDER_QUIERO: "accion_quiero",
  RESPONDER_NO_QUIERO: "accion_no_quiero",
};

/** Suscribe el componente al idioma elegido: al cambiarlo, se vuelve a renderizar. */
export function usarIdioma(): Idioma {
  return useSyncExternalStore(
    suscribirPreferencias,
    () => preferencias().idioma,
    () => "es" as Idioma,
  );
}

export function idiomaActual(): Idioma {
  return preferencias().idioma;
}

/** Traduce una clave y reemplaza `{param}`. */
export function t(clave: Clave, params?: Record<string, string | number>): string {
  const idioma = idiomaActual();
  const plantilla = TEXTOS[idioma][clave] ?? TEXTOS.es[clave] ?? clave;
  if (!params) return plantilla;
  return plantilla.replace(/\{(\w+)\}/g, (todo, nombre: string) =>
    nombre in params ? String(params[nombre]) : todo,
  );
}

export function nombrePalo(palo: Palo, idioma: Idioma = idiomaActual()): string {
  return PALOS[idioma][palo] ?? PALOS.es[palo];
}

export function nombreCarta(carta: Carta, idioma: Idioma = idiomaActual()): string {
  return `${carta.numero} ${CONECTOR_CARTA[idioma]} ${nombrePalo(carta.palo, idioma)}`;
}

export function nombreFigura(numero: number, idioma: Idioma = idiomaActual()): string | null {
  return FIGURAS[idioma][numero] ?? null;
}

export function nombreCanto(canto: string): string {
  const clave = CANTO_CLAVE[canto];
  return clave ? t(clave) : canto.toLowerCase().replace(/_/g, " ");
}

export function etiquetaAccion(nombre: string): string {
  const clave = ACCION_CLAVE[nombre];
  return clave ? t(clave) : nombre;
}

export function nombreEquipo(equipo: number | null | undefined): string {
  return equipo === 1 ? "B" : "A";
}

/**
 * Traduce un evento de la historia a partir de sus campos estructurados.
 * Si el servidor no mandó el dato que hace falta, cae al `texto` en español.
 */
export function textoEvento(evento: Evento, nombres: string[]): string {
  const jugador = evento.jugador !== undefined ? nombres[evento.jugador] ?? `#${evento.jugador + 1}` : "—";
  const equipo = nombreEquipo(evento.equipo);
  const valores = evento.valores?.join(" / ");

  switch (evento.tipo) {
    case "REPARTO":
      if (evento.manoNumero === null || evento.manoNumero === undefined) break;
      return t("evento_reparto", { n: evento.manoNumero + 1 });
    case "CARTA_JUGADA":
      if (!evento.carta) break;
      return t("evento_carta", { jugador, carta: nombreCarta(evento.carta) });
    case "BAZA_RESUELTA":
      if (evento.baza === undefined) break;
      return evento.parda
        ? t("evento_baza_parda", { n: evento.baza, jugador })
        : t("evento_baza", { n: evento.baza, jugador });
    case "CANTO":
      if (!evento.canto) break;
      return t("evento_canto", { jugador, canto: nombreCanto(evento.canto) });
    case "RESPUESTA":
      if (evento.quiero === null || evento.quiero === undefined) break;
      return t("evento_respuesta", {
        jugador,
        respuesta: t(evento.quiero ? "evento_quiero" : "evento_no_quiero"),
      });
    case "ENVIDO_RESUELTO":
      if (evento.puntos === undefined) break;
      // Sin los valores no se puede armar la frase completa: texto del servidor.
      if (!evento.porRechazo && !valores) break;
      return evento.porRechazo
        ? t("evento_envido_rechazado", { puntos: evento.puntos, equipo })
        : t("evento_envido", { puntos: evento.puntos, equipo, valores: valores ?? "" });
    case "TRUCO_RESUELTO":
      if (evento.puntos === undefined) break;
      if (evento.aceptado) {
        if (evento.valorMano === undefined || evento.valorMano === null) break;
        return t("evento_truco_aceptado", { valor: evento.valorMano });
      }
      return t("evento_truco_rechazado", { puntos: evento.puntos });
    case "FLOR_RESUELTA":
      if (evento.puntos === undefined || !valores) break;
      return t("evento_flor", { puntos: evento.puntos, equipo, valores });
    case "FIN_DE_MALAS":
      if (!evento.puntajes) break;
      return t("evento_fin_malas", { equipo, puntajes: evento.puntajes.join("-") });
    case "MANO_GANADA":
      if (evento.puntos === undefined) break;
      return t("evento_mano", { equipo, puntos: evento.puntos });
    case "PARTIDA_GANADA":
      return t("evento_partida", { equipo });
    case "FUE_AL_MAZO":
      return t("evento_mazo", { jugador });
    default:
      break;
  }

  return evento.texto;
}

export type { Clave };

/** Claves del diccionario, en el orden de `es`. Lo usan los tests de paridad es/en. */
export const CLAVES = Object.keys(ES) as Clave[];

export const IDIOMAS: Idioma[] = ["es", "en"];

/** Texto sin resolver parámetros ni mirar las preferencias (para tests). */
export function textoCrudo(idioma: Idioma, clave: Clave): string {
  return TEXTOS[idioma][clave];
}
