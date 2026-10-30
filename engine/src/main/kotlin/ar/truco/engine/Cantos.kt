package ar.truco.engine

/** Cantos posibles. Ver docs/01-REGLAS.md §5 y §6. */
enum class Canto {
    ENVIDO, REAL_ENVIDO, FALTA_ENVIDO,
    TRUCO, RETRUCO, VALE_CUATRO,
    FLOR,
}

/** Respuesta a un canto pendiente. */
enum class Respuesta { QUIERO, NO_QUIERO }

object Cantos {

    fun esEnvido(canto: Canto): Boolean =
        canto == Canto.ENVIDO || canto == Canto.REAL_ENVIDO || canto == Canto.FALTA_ENVIDO

    fun esTruco(canto: Canto): Boolean =
        canto == Canto.TRUCO || canto == Canto.RETRUCO || canto == Canto.VALE_CUATRO

    /** Valor de la mano si el canto es aceptado: truco 2, retruco 3, vale cuatro 4. */
    fun valorTruco(canto: Canto): Int = when (canto) {
        Canto.TRUCO -> 2
        Canto.RETRUCO -> 3
        Canto.VALE_CUATRO -> 4
        else -> error("No es un canto de truco: $canto")
    }

    /** Puntos para el que cantó si el rival dice "no quiero": 1, 2 y 3. */
    fun valorTrucoRechazado(canto: Canto): Int = valorTruco(canto) - 1

    fun ordenEnvido(canto: Canto): Int = when (canto) {
        Canto.ENVIDO -> 1
        Canto.REAL_ENVIDO -> 2
        Canto.FALTA_ENVIDO -> 3
        else -> 0
    }

    fun valorEnvidoDeclaracion(canto: Canto, falta: Int): Int = when (canto) {
        Canto.ENVIDO -> 2
        Canto.REAL_ENVIDO -> 3
        Canto.FALTA_ENVIDO -> falta
        else -> error("No es un canto de envido: $canto")
    }

    /** Suma de una cadena de envido aceptada. */
    fun valorEnvidoAceptado(declaraciones: List<Canto>, falta: Int): Int =
        declaraciones.sumOf { valorEnvidoDeclaracion(it, falta) }

    /**
     * Regla del "no quiero" (docs/01-REGLAS.md §5):
     * primera declaración rechazada ⇒ 1 punto; si no, vale lo que valía la cadena
     * hasta la declaración anterior.
     */
    fun valorEnvidoRechazado(declaraciones: List<Canto>, falta: Int): Int =
        if (declaraciones.size <= 1) 1 else valorEnvidoAceptado(declaraciones.dropLast(1), falta)

    /** ¿Se puede agregar [canto] a la cadena de envido [previas]? */
    fun puedeEscalarEnvido(previas: List<Canto>, canto: Canto, config: RulesConfig): Boolean {
        if (!esEnvido(canto)) return false
        return when (canto) {
            Canto.ENVIDO ->
                config.envido &&
                    previas.count { it == Canto.ENVIDO } < config.maxEnvidos &&
                    previas.none { ordenEnvido(it) > 1 }
            Canto.REAL_ENVIDO ->
                config.realEnvido &&
                    Canto.REAL_ENVIDO !in previas &&
                    Canto.FALTA_ENVIDO !in previas
            Canto.FALTA_ENVIDO ->
                config.faltaEnvido && Canto.FALTA_ENVIDO !in previas
            else -> false
        }
    }

    /** Escalada del truco: TRUCO → RETRUCO → VALE_CUATRO. */
    fun siguienteTruco(canto: Canto): Canto? = when (canto) {
        Canto.TRUCO -> Canto.RETRUCO
        Canto.RETRUCO -> Canto.VALE_CUATRO
        else -> null
    }

    /** Primer canto disponible según el valor aceptado actualmente para la mano. */
    fun trucoDisponible(valorMano: Int): Canto? = when (valorMano) {
        1 -> Canto.TRUCO
        2 -> Canto.RETRUCO
        3 -> Canto.VALE_CUATRO
        else -> null
    }
}
