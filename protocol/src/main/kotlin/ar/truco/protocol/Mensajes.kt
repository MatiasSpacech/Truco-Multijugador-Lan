package ar.truco.protocol

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonClassDiscriminator
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

/** Versión del protocolo. Cliente y servidor deben coincidir (docs/04-PROTOCOLO.md). */
const val PROTOCOL_VERSION: Int = 1

/** Nombres de mensajes: cliente → servidor. */
object Cliente {
    const val HELLO = "hello"
    const val JOIN = "join"
    const val REJOIN = "rejoin"
    const val READY = "ready"
    const val ACTION = "action"
    const val PING = "ping"
    const val LEAVE = "leave"
}

/** Nombres de mensajes: servidor → cliente. */
object Servidor {
    const val WELCOME = "welcome"
    const val LOBBY = "lobby"
    const val STATE = "state"
    const val EVENTS = "events"
    const val ERROR = "error"
    const val PONG = "pong"
    const val ENDED = "ended"
}

/** Códigos de error estables (los usa el cliente para decidir qué hacer). */
object Errores {
    const val PROTOCOL_VERSION = "PROTOCOL_VERSION"
    const val SALA_LLENA = "SALA_LLENA"
    const val TOKEN_INVALIDO = "TOKEN_INVALIDO"
    const val ACCION_ILEGAL = "ACCION_ILEGAL"
    const val NO_ES_TU_TURNO = "NO_ES_TU_TURNO"
    const val STALE_STATE = "STALE_STATE"
    const val PARTIDA_EN_CURSO = "PARTIDA_EN_CURSO"
    const val HOST_LEFT = "HOST_LEFT"
    const val MENSAJE_INVALIDO = "MENSAJE_INVALIDO"
    const val YA_EN_LA_SALA = "YA_EN_LA_SALA"
    const val NOMBRE_INVALIDO = "NOMBRE_INVALIDO"
    const val NO_HAY_PARTIDA = "NO_HAY_PARTIDA"
}

/**
 * Envelope de todos los mensajes:
 * `{ "t": "action", "id": "uuid", "v": 1, "d": { ... } }`
 */
@Serializable
data class Sobre(
    val t: String,
    val id: String? = null,
    val v: Int = PROTOCOL_VERSION,
    val d: JsonElement = JsonObject(emptyMap()),
)

// ------------------------------------------------------------------ cartas

@Serializable
enum class PaloDto { ESPADA, BASTO, ORO, COPA }

@Serializable
data class CartaDto(val palo: PaloDto, val numero: Int)

// ------------------------------------------------------------------ cliente → servidor

@Serializable
data class HelloPayload(
    val nombre: String = "",
    val protocolVersion: Int = PROTOCOL_VERSION,
)

@Serializable
data class JoinPayload(val nombre: String)

@Serializable
data class RejoinPayload(val playerToken: String)

@Serializable
data class ReadyPayload(val listo: Boolean = true)

@Serializable
data class ActionPayload(
    val stateSeq: Int,
    val accion: AccionDto,
)

@Serializable
data class PingPayload(val ts: Long = 0)

@Serializable
data object SinPayload

/** Acción de juego tal como viaja por la red. */
@OptIn(ExperimentalSerializationApi::class)
@Serializable
@JsonClassDiscriminator("tipo")
sealed interface AccionDto {
    @Serializable
    @SerialName("JUGAR_CARTA")
    data class JugarCarta(val carta: CartaDto) : AccionDto

    @Serializable
    @SerialName("CANTAR")
    data class Cantar(val canto: String) : AccionDto

    @Serializable
    @SerialName("RESPONDER")
    data class Responder(val respuesta: String) : AccionDto

    @Serializable
    @SerialName("IRSE_AL_MAZO")
    data object IrseAlMazo : AccionDto
}

// ------------------------------------------------------------------ servidor → cliente

@Serializable
data class ConfigDto(
    val jugadores: Int,
    val targetScore: Int,
    val flor: String,
    val envido: Boolean,
    val realEnvido: Boolean,
    val faltaEnvido: Boolean,
    val trucoAntesDeEnvido: Boolean,
    val timeoutCantoMs: Long,
    val timeoutTurnoMs: Long,
    val contraflor: Boolean = true,
    val contraflorAlResto: Boolean = true,
    val malasYBuenas: Boolean = false,
)

@Serializable
data class WelcomePayload(
    val protocolVersion: Int,
    val serverVersion: String,
    val codigo: String,
    val config: ConfigDto,
    val tuJugadorId: Int? = null,
    val playerToken: String? = null,
)

@Serializable
data class AsientoDto(
    val id: Int,
    val nombre: String,
    val equipo: Int,
    val conectado: Boolean,
    val listo: Boolean,
    val esHost: Boolean,
    val esMano: Boolean,
    val cartasRestantes: Int,
    val ausente: Boolean = false,
)

@Serializable
data class LobbyPayload(
    val codigo: String,
    val config: ConfigDto,
    val asientos: List<AsientoDto>,
    val jugadores: Int,
    val partidaEnCurso: Boolean,
)

@Serializable
data class JugadaDto(val jugador: Int, val carta: CartaDto)

@Serializable
data class BazaDto(
    val numero: Int,
    val jugadas: List<JugadaDto>,
    val ganador: Int? = null,
    val parda: Boolean = false,
)

@Serializable
data class CantoPendienteDto(
    val canto: String,
    val deJugador: Int,
    val respondedor: Int,
    val deadlineMs: Long,
)

@Serializable
data class EnvidoDto(
    val declaraciones: List<String>,
    val valorAcumulado: Int,
    val resuelto: Boolean,
)

@Serializable
data class FlorDto(
    val declaraciones: List<Int>,
    val valores: List<Int> = emptyList(),
)

/**
 * Estado completo filtrado para un jugador: nunca incluye cartas ajenas.
 * [seq] identifica la versión; el cliente la devuelve en cada acción.
 */
@Serializable
data class StatePayload(
    val seq: Int,
    val fase: String,
    val miJugadorId: Int,
    val asientos: List<AsientoDto>,
    val puntajes: List<Int>,
    val targetScore: Int,
    val etapa: String = "UNICA",
    val objetivoEtapa: Int = 15,
    val malasGanadasPor: Int? = null,
    val puntajesMalas: List<Int> = emptyList(),
    val manoIndex: Int,
    val manoNumero: Int,
    val turno: Int,
    val valorMano: Int,
    val miMano: List<CartaDto>,
    val manosReveladas: List<List<CartaDto>>? = null,
    val bazas: List<BazaDto>,
    val cantoPendiente: CantoPendienteDto? = null,
    val envido: EnvidoDto? = null,
    val flor: FlorDto? = null,
    val legalActions: List<String>,
    val cartasJugables: List<CartaDto>,
    val envidoValores: List<Int> = emptyList(),
    val faltaEnvido: Int,
    val manoGanadaPor: Int? = null,
    val partidaGanadaPor: Int? = null,
)

/**
 * Evento para la historia, el log y los sonidos del cliente.
 *
 * `texto` viene armado en español (compatibilidad y debug), pero el cliente traduce a partir de
 * los campos estructurados (jugador, carta, canto, puntos, valores...). Todos son opcionales:
 * si a un tipo de evento le falta un dato, el cliente cae al `texto` del servidor.
 */
@Serializable
data class EventoDto(
    val tipo: String,
    val texto: String,
    val jugador: Int? = null,
    val carta: CartaDto? = null,
    val baza: Int? = null,
    val canto: String? = null,
    val puntos: Int? = null,
    val equipo: Int? = null,
    val valores: List<Int>? = null,
    val parda: Boolean? = null,
    val quiero: Boolean? = null,
    val aceptado: Boolean? = null,
    val porRechazo: Boolean? = null,
    val manoNumero: Int? = null,
    val valorMano: Int? = null,
    val puntajes: List<Int>? = null,
)

@Serializable
data class EventosPayload(val seq: Int, val eventos: List<EventoDto>)

@Serializable
data class ErrorPayload(val code: String, val mensaje: String, val fatal: Boolean = false)

@Serializable
data class PongPayload(val ts: Long)

@Serializable
data class EndedPayload(val motivo: String)

/** Respuesta de `GET /api/info` para diagnóstico y para el lobby sin abrir WS. */
@Serializable
data class InfoPayload(
    val appVersion: String,
    val protocolVersion: Int,
    val codigo: String,
    val jugadores: Int,
    val maxJugadores: Int,
    val fase: String,
    val partidaEnCurso: Boolean,
)
