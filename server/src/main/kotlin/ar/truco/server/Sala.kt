package ar.truco.server

import ar.truco.engine.Accion
import ar.truco.engine.Canto
import ar.truco.engine.Carta
import ar.truco.engine.Cartas
import ar.truco.engine.Estado
import ar.truco.engine.Evento
import ar.truco.engine.Fase
import ar.truco.engine.Motor
import ar.truco.engine.Palo
import ar.truco.engine.Respuesta
import ar.truco.engine.Resultado.Ilegal
import ar.truco.engine.Resultado.Ok
import ar.truco.engine.RulesConfig
import ar.truco.protocol.AccionDto
import ar.truco.protocol.ActionPayload
import ar.truco.protocol.Cliente
import ar.truco.protocol.EndedPayload
import ar.truco.protocol.Errores
import ar.truco.protocol.EventosPayload
import ar.truco.protocol.HelloPayload
import ar.truco.protocol.InfoPayload
import ar.truco.protocol.JoinPayload
import ar.truco.protocol.LobbyPayload
import ar.truco.protocol.PROTOCOL_VERSION
import ar.truco.protocol.PingPayload
import ar.truco.protocol.PongPayload
import ar.truco.protocol.ReadyPayload
import ar.truco.protocol.RejoinPayload
import ar.truco.protocol.Servidor
import ar.truco.protocol.Sobre
import ar.truco.protocol.WelcomePayload
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID
import kotlin.random.Random

/**
 * Una sala = una partida. Todas las mutaciones pasan por un único [Mutex], así que el orden de
 * las acciones es total y no hay carreras (ver docs/02-ARQUITECTURA.md §3).
 *
 * Reglas duras:
 * - Nadie ve cartas ajenas: el estado se arma por jugador ([aStatePayload]).
 * - El servidor es el único que decide: el cliente solo manda intenciones.
 */
class Sala(
    val config: RulesConfig,
    private val codigo: String,
    private val serverVersion: String,
    private val scope: CoroutineScope,
    private val pausaEntreManosMs: Long = 4_000,
    private val ahora: () -> Long = System::currentTimeMillis,
) {
    private class Jugador(val token: String, val nombre: String) {
        var listo: Boolean = false
        var sesion: Sesion? = null
    }

    private val mutex = Mutex()
    private val sesiones = mutableSetOf<Sesion>()
    private val jugadores = mutableListOf<Jugador>()

    private var estado: Estado? = null
    private var seq = 0
    private var deadline: Long? = null
    private var timeoutJob: Job? = null
    private val accionesRecientes = ArrayDeque<String>()

    // ------------------------------------------------------------ conexiones

    suspend fun conectar(sesion: Sesion) = mutex.withLock {
        sesiones += sesion
        enviarLobby()
    }

    suspend fun desconectar(sesion: Sesion) = mutex.withLock {
        sesiones -= sesion
        val jugador = jugadores.firstOrNull { it.sesion === sesion }
        sesion.jugadorId = null
        if (jugador == null) return@withLock

        jugador.sesion = null
        if (estado == null) {
            // En el lobby la silla se libera; en partida se reserva para reconectar.
            jugadores.remove(jugador)
            reasignarIds()
            enviarLobby()
        } else {
            enviarEstados(emptyList())
        }
    }

    /** Sala cerrada (el anfitrión se fue o se detuvo el servidor). */
    suspend fun cerrar(motivo: String = "HOST_LEFT"): Unit = mutex.withLock {
        timeoutJob?.cancel()
        estado = null
        broadcast(sobre(Servidor.ENDED, comoJson(EndedPayload(motivo))))
    }

    suspend fun info(): InfoPayload = mutex.withLock {
        InfoPayload(
            appVersion = serverVersion,
            protocolVersion = PROTOCOL_VERSION,
            codigo = codigo,
            jugadores = jugadores.size,
            maxJugadores = config.jugadores,
            fase = estado?.fase?.name ?: "LOBBY",
            partidaEnCurso = estado != null,
        )
    }

    // ------------------------------------------------------------ mensajes

    suspend fun procesar(sesion: Sesion, texto: String) = mutex.withLock {
        val sobre = runCatching { JsonTruco.decodeFromString(Sobre.serializer(), texto) }.getOrNull()
        if (sobre == null) {
            sesion.error(Errores.MENSAJE_INVALIDO, "Mensaje ilegible")
            return@withLock
        }
        if (sobre.v != PROTOCOL_VERSION) {
            sesion.error(
                Errores.PROTOCOL_VERSION,
                "Versión de protocolo incompatible (servidor $PROTOCOL_VERSION, cliente ${sobre.v})",
                fatal = true,
            )
            return@withLock
        }

        when (sobre.t) {
            Cliente.HELLO -> recibirHello(sesion, sobre)
            Cliente.JOIN -> recibirJoin(sesion, sobre)
            Cliente.REJOIN -> recibirRejoin(sesion, sobre)
            Cliente.READY -> recibirReady(sesion, sobre)
            Cliente.ACTION -> recibirAction(sesion, sobre)
            Cliente.PING -> recibirPing(sesion, sobre)
            Cliente.LEAVE -> recibirLeave(sesion)
            else -> sesion.error(Errores.MENSAJE_INVALIDO, "Tipo de mensaje desconocido: ${sobre.t}")
        }
    }

    private fun recibirHello(sesion: Sesion, sobre: Sobre) {
        val payload = runCatching { desdeJson<HelloPayload>(sobre.d) }.getOrElse { HelloPayload() }
        if (payload.protocolVersion != PROTOCOL_VERSION) {
            sesion.error(
                Errores.PROTOCOL_VERSION,
                "Versión de protocolo incompatible (servidor $PROTOCOL_VERSION)",
                fatal = true,
            )
            return
        }
        sesion.enviarWelcome()
    }

    private fun recibirJoin(sesion: Sesion, sobre: Sobre) {
        val payload = runCatching { desdeJson<JoinPayload>(sobre.d) }.getOrNull()
        if (payload == null) return sesion.error(Errores.MENSAJE_INVALIDO, "Faltan los datos para entrar")

        if (sesion.jugadorId != null) return sesion.error(Errores.YA_EN_LA_SALA, "Ya estás en la sala")
        val nombre = payload.nombre.trim()
        if (nombre.isBlank()) return sesion.error(Errores.NOMBRE_INVALIDO, "Elegí un nombre")
        if (estado != null) return sesion.error(Errores.PARTIDA_EN_CURSO, "La partida ya empezó")
        if (jugadores.size >= config.jugadores) {
            return sesion.error(Errores.SALA_LLENA, "La sala está llena (${config.jugadores} jugadores)")
        }

        val jugador = Jugador(token = UUID.randomUUID().toString(), nombre = nombre.take(24))
        jugador.sesion = sesion
        jugadores += jugador
        sesion.token = jugador.token
        sesion.jugadorId = jugadores.lastIndex

        sesion.enviarWelcome()
        enviarLobby()
    }

    private fun recibirRejoin(sesion: Sesion, sobre: Sobre) {
        val payload = runCatching { desdeJson<RejoinPayload>(sobre.d) }.getOrNull()
            ?: return sesion.error(Errores.MENSAJE_INVALIDO, "Falta el token")

        val id = jugadores.indexOfFirst { it.token == payload.playerToken }
        if (id < 0) {
            return sesion.error(Errores.TOKEN_INVALIDO, "Token desconocido; entrá con un nombre")
        }

        val jugador = jugadores[id]
        jugador.sesion?.let { anterior -> anterior.jugadorId = null }
        jugador.sesion = sesion
        sesion.token = jugador.token
        sesion.jugadorId = id

        sesion.enviarWelcome()
        if (estado == null) enviarLobby() else enviarEstadoA(id)
    }

    private fun recibirReady(sesion: Sesion, sobre: Sobre) {
        val id = sesion.jugadorId ?: return sesion.error(Errores.MENSAJE_INVALIDO, "No estás en la sala")
        if (estado != null) return sesion.error(Errores.PARTIDA_EN_CURSO, "La partida ya empezó")

        val payload = runCatching { desdeJson<ReadyPayload>(sobre.d) }.getOrDefault(ReadyPayload())
        jugadores.getOrNull(id)?.listo = payload.listo
        enviarLobby()

        if (jugadores.size == config.jugadores && jugadores.all { it.listo }) iniciarPartida()
    }

    private fun recibirAction(sesion: Sesion, sobre: Sobre) {
        val id = sesion.jugadorId ?: return sesion.error(Errores.MENSAJE_INVALIDO, "No estás en la partida")
        val actual = estado ?: return sesion.error(Errores.NO_HAY_PARTIDA, "La partida no empezó")

        val payload = runCatching { desdeJson<ActionPayload>(sobre.d) }.getOrNull()
            ?: return sesion.error(Errores.MENSAJE_INVALIDO, "Faltan los datos de la acción")

        val accionId = sobre.id
        if (accionId != null && accionId in accionesRecientes) return // reintento: ya aplicada

        if (payload.stateSeq != seq) {
            sesion.error(Errores.STALE_STATE, "El estado cambió; reintentá sobre el estado actual")
            enviarEstadoA(id)
            return
        }

        val accion = payload.accion.aMotor()
            ?: return sesion.error(Errores.ACCION_ILEGAL, "Acción desconocida")

        when (val resultado = Motor.aplicar(actual, id, accion)) {
            is Ok -> {
                if (accionId != null) recordar(accionId)
                estado = resultado.valor
                seq++
                enviarEstados(resultado.eventos)
                reprogramar()
            }

            is Ilegal -> {
                val code = if (resultado.motivo.contains("turno", ignoreCase = true)) {
                    Errores.NO_ES_TU_TURNO
                } else {
                    Errores.ACCION_ILEGAL
                }
                sesion.error(code, resultado.motivo)
            }
        }
    }

    private fun recibirPing(sesion: Sesion, sobre: Sobre) {
        val payload = runCatching { desdeJson<PingPayload>(sobre.d) }.getOrDefault(PingPayload())
        sesion.enviar(Servidor.PONG, comoJson(PongPayload(payload.ts)))
    }

    private fun recibirLeave(sesion: Sesion) {
        val id = sesion.jugadorId ?: return
        sesion.jugadorId = null
        sesion.token = null

        if (estado == null) {
            jugadores.removeAt(id)
            reasignarIds()
            enviarLobby()
        } else {
            jugadores.getOrNull(id)?.sesion = null
            enviarEstados(emptyList())
        }
    }

    // ------------------------------------------------------------ partida

    private fun iniciarPartida() {
        val nueva = Motor.nuevaPartida(config, jugadores.map { it.nombre }, Random.nextLong())
        val base = when (nueva) {
            is Ok -> nueva.valor
            is Ilegal -> return
        }
        val repartida = when (val r = Motor.repartir(base)) {
            is Ok -> r.valor
            is Ilegal -> return
        }
        estado = repartida
        seq = 1
        enviarEstados(repartida.eventos)
        reprogramar()
    }

    private fun reprogramar() {
        timeoutJob?.cancel()
        timeoutJob = null
        deadline = null

        val actual = estado ?: return
        val ms = when {
            actual.fase == Fase.FIN_PARTIDA -> return
            actual.negociacion != null -> config.timeoutCantoMs
            actual.fase == Fase.JUEGO || actual.fase == Fase.FLOR -> config.timeoutTurnoMs
            actual.fase == Fase.MANO_CERRADA -> pausaEntreManosMs
            else -> return
        }

        deadline = ahora() + ms
        timeoutJob = scope.launch {
            delay(ms)
            mutex.withLock { vencerTimeout() }
        }
    }

    /** El tiempo se acabó: respondemos/repartimos por el jugador para que la mesa no se trabe. */
    private fun vencerTimeout() {
        val actual = estado ?: return

        if (actual.fase == Fase.MANO_CERRADA) {
            val repartida = Motor.repartir(actual)
            if (repartida is Ok) {
                estado = repartida.valor
                seq++
                enviarEstados(repartida.eventos)
                reprogramar()
            }
            return
        }

        val negociacion = actual.negociacion
        val forzada: Pair<Int, Accion> = when {
            negociacion != null -> negociacion.respondedor to Accion.Responder(Respuesta.NO_QUIERO)
            actual.fase == Fase.FLOR && actual.tieneFlor(actual.turno) ->
                actual.turno to Accion.Cantar(Canto.FLOR)
            actual.fase == Fase.JUEGO && actual.manos[actual.turno].isNotEmpty() ->
                actual.turno to Accion.JugarCarta(actual.manos[actual.turno].minBy { Cartas.poder(it) })
            else -> null
        } ?: return

        val resultado = Motor.aplicar(actual, forzada.first, forzada.second)
        if (resultado is Ok) {
            estado = resultado.valor
            seq++
            enviarEstados(resultado.eventos)
            reprogramar()
        }
    }

    // ------------------------------------------------------------ envío

    private fun broadcast(texto: String) {
        sesiones.forEach { it.enviar(texto) }
    }

    private fun enviarLobby() {
        val actual = estado
        val asientos = aAsientos(
            nombres = jugadores.map { it.nombre },
            equipos = jugadores.indices.map { it % 2 },
            conectados = jugadores.map { it.sesion != null },
            manoIndex = actual?.manoIndex ?: 0,
            cartasRestantes = actual?.manos?.map { it.size } ?: List(jugadores.size) { 0 },
            listos = jugadores.map { it.listo },
        )
        val payload = LobbyPayload(
            codigo = codigo,
            config = aConfigDto(config),
            asientos = asientos,
            jugadores = jugadores.size,
            partidaEnCurso = actual != null,
        )
        broadcast(sobre(Servidor.LOBBY, comoJson(payload)))
    }

    private fun enviarEstados(eventos: List<Evento>) {
        val actual = estado ?: return
        val conectados = jugadores.map { it.sesion != null }
        val listos = jugadores.map { it.listo }
        val restante = deadline?.let { (it - ahora()).coerceAtLeast(0) }

        jugadores.forEachIndexed { id, jugador ->
            val sesion = jugador.sesion ?: return@forEachIndexed
            val vista = aStatePayload(actual, id, seq, restante, conectados, listos)
            sesion.enviar(Servidor.STATE, comoJson(vista))
        }

        if (eventos.isNotEmpty()) {
            val nombres = actual.asientos.map { it.nombre }
            val payload = EventosPayload(seq, eventos.map { aEventoDto(it, nombres) })
            broadcast(sobre(Servidor.EVENTS, comoJson(payload)))
        }
    }

    private fun enviarEstadoA(id: Int) {
        val actual = estado ?: return
        val sesion = jugadores.getOrNull(id)?.sesion ?: return
        val restante = deadline?.let { (it - ahora()).coerceAtLeast(0) }
        val vista = aStatePayload(
            actual, id, seq, restante,
            jugadores.map { it.sesion != null },
            jugadores.map { it.listo },
        )
        sesion.enviar(Servidor.STATE, comoJson(vista))
    }

    private fun Sesion.enviarWelcome() {
        val jugador = jugadorId?.let { jugadores.getOrNull(it) }
        val payload = WelcomePayload(
            protocolVersion = PROTOCOL_VERSION,
            serverVersion = serverVersion,
            codigo = codigo,
            config = aConfigDto(config),
            tuJugadorId = jugadorId,
            playerToken = jugador?.token,
        )
        enviar(Servidor.WELCOME, comoJson(payload))
    }

    // ------------------------------------------------------------ utilidades

    private fun reasignarIds() {
        jugadores.forEachIndexed { id, jugador -> jugador.sesion?.jugadorId = id }
    }

    private fun recordar(accionId: String) {
        accionesRecientes.addLast(accionId)
        while (accionesRecientes.size > 200) accionesRecientes.removeFirst()
    }

    private fun AccionDto.aMotor(): Accion? = runCatching {
        when (this) {
            is AccionDto.JugarCarta -> Accion.JugarCarta(
                Carta(Palo.valueOf(this.carta.palo.name), this.carta.numero),
            )

            is AccionDto.Cantar -> Accion.Cantar(Canto.valueOf(this.canto))
            is AccionDto.Responder -> Accion.Responder(Respuesta.valueOf(this.respuesta))
            AccionDto.IrseAlMazo -> Accion.IrseAlMazo
        }
    }.getOrNull()
}
