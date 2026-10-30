package ar.truco.engine

enum class Fase { ESPERANDO, FLOR, JUEGO, MANO_CERRADA, FIN_PARTIDA }

data class Asiento(
    val id: Int,
    val nombre: String,
    val equipo: Int,
    val conectado: Boolean = true,
    val listo: Boolean = false,
)

data class Jugada(val jugador: Int, val carta: Carta)

data class Baza(
    val numero: Int,
    val jugadas: List<Jugada> = emptyList(),
    val ganador: Int? = null,
    val parda: Boolean = false,
)

data class Declaracion(val canto: Canto, val jugador: Int)

enum class TipoNegociacion { ENVIDO, TRUCO }

/** Canto pendiente de respuesta. Mientras exista, la mesa está congelada. */
data class Negociacion(
    val tipo: TipoNegociacion,
    val declaraciones: List<Declaracion>,
    val respondedor: Int,
) {
    val ultima: Declaracion get() = declaraciones.last()
    val declarante: Int get() = ultima.jugador
    val canto: Canto get() = ultima.canto
}

data class Estado(
    val config: RulesConfig,
    val asientos: List<Asiento>,
    val seed: Long,
    val fase: Fase = Fase.ESPERANDO,
    val manoNumero: Int = 0,
    val manoIndex: Int = 0,
    val puntajes: List<Int> = List(2) { 0 },
    /** Manos actuales (van perdiendo cartas). */
    val manos: List<List<Carta>> = List(asientos.size) { emptyList() },
    /** Las 3 cartas repartidas a cada jugador: base del envido y la flor. */
    val manosRepartidas: List<List<Carta>> = List(asientos.size) { emptyList() },
    val bazas: List<Baza> = emptyList(),
    val turno: Int = 0,
    val valorMano: Int = 1,
    val manoGanadaPor: Int? = null,
    val negociacion: Negociacion? = null,
    val envidoCerrado: Boolean = false,
    val florDeclaraciones: List<Declaracion> = emptyList(),
    /** Valores de envido revelados al resolverse (para mostrar en la mesa). */
    val envidoValores: List<Int> = emptyList(),
    val eventos: List<Evento> = emptyList(),
) {
    val equipos: Int get() = puntajes.size

    fun equipoDe(jugador: Int): Int = asientos[jugador].equipo

    fun equipoRival(equipo: Int): Int = (equipo + 1) % equipos

    val mano: Int get() = manoIndex

    fun tieneFlor(jugador: Int): Boolean = Envido.tieneFlor(manosRepartidas[jugador])

    fun activo(): Boolean = fase == Fase.JUEGO || fase == Fase.FLOR
}

sealed interface Accion {
    data class JugarCarta(val carta: Carta) : Accion
    data class Cantar(val canto: Canto) : Accion
    data class Responder(val respuesta: Respuesta) : Accion
    data object IrseAlMazo : Accion
}

/** Acciones legales expuestas al cliente (el cliente no calcula reglas). */
enum class TipoAccion {
    JUGAR_CARTA,
    IRSE_AL_MAZO,
    CANTAR_ENVIDO,
    CANTAR_REAL_ENVIDO,
    CANTAR_FALTA_ENVIDO,
    CANTAR_TRUCO,
    CANTAR_RETRUCO,
    CANTAR_VALE_CUATRO,
    CANTAR_FLOR,
    RESPONDER_QUIERO,
    RESPONDER_NO_QUIERO,
}

sealed interface Evento {
    data class Reparto(val manoNumero: Int, val mano: Int) : Evento
    data class CartaJugada(val jugador: Int, val carta: Carta, val baza: Int) : Evento
    data class BazaResuelta(val baza: Int, val ganador: Int, val parda: Boolean) : Evento
    data class CantoHecho(val jugador: Int, val canto: Canto) : Evento
    data class RespuestaDada(val jugador: Int, val quiero: Boolean) : Evento
    data class EnvidoResuelto(
        val valores: List<Int>,
        val ganadorEquipo: Int,
        val puntos: Int,
        val porRechazo: Boolean,
    ) : Evento
    data class TrucoResuelto(
        val canto: Canto,
        val aceptado: Boolean,
        val puntos: Int,
        val valorMano: Int,
    ) : Evento
    data class FlorResuelta(val ganadorEquipo: Int, val puntos: Int, val valores: List<Int>) : Evento
    data class ManoGanada(val equipo: Int, val puntos: Int, val manoNumero: Int) : Evento
    data class PartidaGanada(val equipo: Int) : Evento
    data class SeFueAlMazo(val jugador: Int, val equipoGanador: Int, val puntos: Int) : Evento
}

sealed interface Resultado<out T> {
    data class Ok<T>(val valor: T, val eventos: List<Evento> = emptyList()) : Resultado<T>
    data class Ilegal(val motivo: String) : Resultado<Nothing>
}

fun <T> Resultado<T>.ok(): T = when (this) {
    is Resultado.Ok -> valor
    is Resultado.Ilegal -> error("Se esperaba un resultado válido y llegó ilegal: $motivo")
}
