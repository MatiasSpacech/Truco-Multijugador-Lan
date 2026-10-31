package ar.truco.server

import ar.truco.engine.Baza
import ar.truco.engine.Canto
import ar.truco.engine.Carta
import ar.truco.engine.Cantos
import ar.truco.engine.Envido
import ar.truco.engine.Estado
import ar.truco.engine.Evento
import ar.truco.engine.Fase
import ar.truco.engine.Motor
import ar.truco.engine.Palo
import ar.truco.engine.RulesConfig
import ar.truco.engine.TipoAccion
import ar.truco.engine.TipoNegociacion
import ar.truco.protocol.AsientoDto
import ar.truco.protocol.BazaDto
import ar.truco.protocol.CantoPendienteDto
import ar.truco.protocol.CartaDto
import ar.truco.protocol.ConfigDto
import ar.truco.protocol.EnvidoDto
import ar.truco.protocol.EventoDto
import ar.truco.protocol.FlorDto
import ar.truco.protocol.JugadaDto
import ar.truco.protocol.PaloDto
import ar.truco.protocol.StatePayload

// ------------------------------------------------------------------ cartas

fun aPaloDto(palo: Palo): PaloDto = PaloDto.valueOf(palo.name)

fun aCartaDto(carta: Carta): CartaDto = CartaDto(palo = aPaloDto(carta.palo), numero = carta.numero)

fun anombreCarta(carta: Carta): String = "${carta.numero} de ${carta.palo.name.lowercase()}"

fun nombreCanto(canto: Canto): String = when (canto) {
    Canto.ENVIDO -> "envido"
    Canto.REAL_ENVIDO -> "real envido"
    Canto.FALTA_ENVIDO -> "falta envido"
    Canto.TRUCO -> "truco"
    Canto.RETRUCO -> "retruco"
    Canto.VALE_CUATRO -> "vale cuatro"
    Canto.FLOR -> "flor"
    Canto.CONTRAFLOR -> "contraflor"
    Canto.CONTRAFLOR_AL_RESTO -> "contraflor al resto"
}

// ------------------------------------------------------------------ config

fun aConfigDto(config: RulesConfig): ConfigDto = ConfigDto(
    jugadores = config.jugadores,
    targetScore = config.targetScore,
    flor = config.flor.name,
    envido = config.envido,
    realEnvido = config.realEnvido,
    faltaEnvido = config.faltaEnvido,
    trucoAntesDeEnvido = config.trucoAntesDeEnvido,
    timeoutCantoMs = config.timeoutCantoMs,
    timeoutTurnoMs = config.timeoutTurnoMs,
    contraflor = config.contraflor,
    contraflorAlResto = config.contraflorAlResto,
    malasYBuenas = config.malasYBuenas,
)

// ------------------------------------------------------------------ asientos

fun aAsientos(
    nombres: List<String>,
    equipos: List<Int>,
    conectados: List<Boolean>,
    manoIndex: Int,
    cartasRestantes: List<Int>,
    listos: List<Boolean>,
    ausentes: List<Boolean> = emptyList(),
): List<AsientoDto> = nombres.indices.map { i ->
    AsientoDto(
        id = i,
        nombre = nombres[i],
        equipo = equipos[i],
        conectado = conectados.getOrElse(i) { false },
        listo = listos.getOrElse(i) { false },
        esHost = i == 0,
        esMano = i == manoIndex,
        cartasRestantes = cartasRestantes.getOrElse(i) { 0 },
        ausente = ausentes.getOrElse(i) { false },
    )
}

// ------------------------------------------------------------------ estado

fun aBazaDto(baza: Baza): BazaDto = BazaDto(
    numero = baza.numero,
    jugadas = baza.jugadas.map { JugadaDto(it.jugador, aCartaDto(it.carta)) },
    ganador = baza.ganador,
    parda = baza.parda,
)

/**
 * Vista privada del estado para [jugadorId]: solo sus propias cartas.
 *
 * @param deadlineEnMs milisegundos restantes del canto/turno pendiente (null si no hay).
 */
fun aStatePayload(
    estado: Estado,
    jugadorId: Int,
    seq: Int,
    deadlineEnMs: Long?,
    conectados: List<Boolean>,
    listos: List<Boolean>,
    ausentes: List<Boolean> = emptyList(),
): StatePayload {
    val legal = Motor.accionesLegales(estado, jugadorId)
    val miMano = estado.manos.getOrElse(jugadorId) { emptyList() }
    val revelar = estado.fase == Fase.MANO_CERRADA || estado.fase == Fase.FIN_PARTIDA
    val neg = estado.negociacion
    val envidoPendiente = neg?.tipo == TipoNegociacion.ENVIDO

    return StatePayload(
        seq = seq,
        fase = estado.fase.name,
        miJugadorId = jugadorId,
        asientos = aAsientos(
            nombres = estado.asientos.map { it.nombre },
            equipos = estado.asientos.map { it.equipo },
            conectados = conectados,
            manoIndex = estado.manoIndex,
            cartasRestantes = estado.manos.map { it.size },
            listos = listos,
            ausentes = ausentes,
        ),
        puntajes = estado.puntajes,
        targetScore = estado.config.targetScore,
        etapa = estado.etapa.name,
        objetivoEtapa = estado.objetivoEtapa,
        malasGanadasPor = estado.malasGanadasPor,
        puntajesMalas = estado.puntajesMalas,
        manoIndex = estado.manoIndex,
        manoNumero = estado.manoNumero,
        turno = estado.turno,
        valorMano = estado.valorMano,
        miMano = miMano.map(::aCartaDto),
        manosReveladas = if (revelar) estado.manos.map { mano -> mano.map(::aCartaDto) } else null,
        bazas = estado.bazas.map(::aBazaDto),
        cantoPendiente = neg?.let {
            CantoPendienteDto(
                canto = it.canto.name,
                deJugador = it.declarante,
                respondedor = it.respondedor,
                deadlineMs = deadlineEnMs ?: 0,
            )
        },
        envido = if (envidoPendiente) {
            EnvidoDto(
                declaraciones = neg.declaraciones.map { it.canto.name },
                valorAcumulado = Cantos.valorEnvidoAceptado(
                    neg.declaraciones.map { it.canto },
                    Motor.faltaEnvido(estado),
                ),
                resuelto = false,
            )
        } else if (estado.envidoValores.isNotEmpty() || estado.envidoCerrado) {
            EnvidoDto(declaraciones = emptyList(), valorAcumulado = 0, resuelto = estado.envidoCerrado)
        } else {
            null
        },
        flor = if (estado.fase == Fase.FLOR || estado.florDeclaraciones.isNotEmpty()) {
            FlorDto(
                declaraciones = estado.florDeclaraciones.map { it.jugador },
                valores = estado.florDeclaraciones.map {
                    Envido.valorFlor(estado.manosRepartidas[it.jugador])
                },
            )
        } else {
            null
        },
        legalActions = legal.sorted().map { it.name },
        cartasJugables = if (TipoAccion.JUGAR_CARTA in legal) miMano.map(::aCartaDto) else emptyList(),
        envidoValores = estado.envidoValores,
        faltaEnvido = Motor.faltaEnvido(estado),
        manoGanadaPor = estado.manoGanadaPor,
        partidaGanadaPor = if (estado.fase == Fase.FIN_PARTIDA) {
            estado.puntajes.indices.firstOrNull { estado.puntajes[it] >= estado.config.targetScore }
        } else {
            null
        },
    )
}

// ------------------------------------------------------------------ eventos

fun aEventoDto(evento: Evento, nombres: List<String>): EventoDto {
    fun nombre(jugador: Int): String = nombres.getOrElse(jugador) { "Jugador $jugador" }

    return when (evento) {
        is Evento.Reparto -> EventoDto(
            tipo = "REPARTO",
            texto = "Se repartió la mano ${evento.manoNumero + 1}",
            manoNumero = evento.manoNumero,
        )

        is Evento.CartaJugada -> EventoDto(
            tipo = "CARTA_JUGADA",
            texto = "${nombre(evento.jugador)} jugó ${anombreCarta(evento.carta)}",
            jugador = evento.jugador,
            carta = aCartaDto(evento.carta),
            baza = evento.baza,
        )

        is Evento.BazaResuelta -> EventoDto(
            tipo = "BAZA_RESUELTA",
            texto = if (evento.parda) {
                "Parda en la baza ${evento.baza}: la gana ${nombre(evento.ganador)}"
            } else {
                "${nombre(evento.ganador)} ganó la baza ${evento.baza}"
            },
            jugador = evento.ganador,
            baza = evento.baza,
            parda = evento.parda,
        )

        is Evento.CantoHecho -> EventoDto(
            tipo = "CANTO",
            texto = "${nombre(evento.jugador)} cantó ${nombreCanto(evento.canto)}",
            jugador = evento.jugador,
            canto = evento.canto.name,
        )

        is Evento.RespuestaDada -> EventoDto(
            tipo = "RESPUESTA",
            texto = "${nombre(evento.jugador)} dijo ${if (evento.quiero) "quiero" else "no quiero"}",
            jugador = evento.jugador,
            quiero = evento.quiero,
        )

        is Evento.EnvidoResuelto -> EventoDto(
            tipo = "ENVIDO_RESUELTO",
            texto = if (evento.porRechazo) {
                "Envido rechazado: ${evento.puntos} punto(s) para el equipo ${evento.ganadorEquipo + 1}"
            } else {
                "Envido: ${evento.puntos} punto(s) para el equipo ${evento.ganadorEquipo + 1} " +
                    "(${evento.valores.joinToString(" / ")})"
            },
            puntos = evento.puntos,
            equipo = evento.ganadorEquipo,
            valores = evento.valores,
            porRechazo = evento.porRechazo,
        )

        is Evento.TrucoResuelto -> EventoDto(
            tipo = "TRUCO_RESUELTO",
            texto = if (evento.aceptado) {
                "Truco aceptado: la mano vale ${evento.valorMano}"
            } else {
                "Truco rechazado: ${evento.puntos} punto(s)"
            },
            canto = evento.canto.name,
            puntos = evento.puntos,
            aceptado = evento.aceptado,
            valorMano = evento.valorMano,
        )

        is Evento.FlorResuelta -> EventoDto(
            tipo = "FLOR_RESUELTA",
            texto = "Flor: ${evento.puntos} punto(s) para el equipo ${evento.ganadorEquipo + 1} " +
                "(${evento.valores.joinToString(" / ")})",
            puntos = evento.puntos,
            equipo = evento.ganadorEquipo,
            valores = evento.valores,
        )

        is Evento.FinDeMalas -> EventoDto(
            tipo = "FIN_DE_MALAS",
            texto = "El equipo ${evento.equipo + 1} ganó las malas " +
                "(${evento.puntajes.joinToString("-")}). Ahora van las buenas, desde cero.",
            equipo = evento.equipo,
            puntajes = evento.puntajes,
        )

        is Evento.ManoGanada -> EventoDto(
            tipo = "MANO_GANADA",
            texto = "El equipo ${evento.equipo + 1} ganó la mano: ${evento.puntos} punto(s)",
            puntos = evento.puntos,
            equipo = evento.equipo,
            manoNumero = evento.manoNumero,
        )

        is Evento.PartidaGanada -> EventoDto(
            tipo = "PARTIDA_GANADA",
            texto = "¡El equipo ${evento.equipo + 1} ganó la partida!",
            equipo = evento.equipo,
        )

        is Evento.SeFueAlMazo -> EventoDto(
            tipo = "FUE_AL_MAZO",
            texto = "${nombre(evento.jugador)} se fue al mazo",
            jugador = evento.jugador,
            puntos = evento.puntos,
            equipo = evento.equipoGanador,
        )
    }
}
