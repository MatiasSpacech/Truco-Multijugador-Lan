package ar.truco.engine

/**
 * Configuración de reglas de una partida. Ver docs/01-REGLAS.md.
 * Todos los valores por defecto corresponden al MVP (2/4 jugadores, a 15, sin flor).
 */
data class RulesConfig(
    val jugadores: Int = 2,
    val targetScore: Int = 15,
    val flor: FlorMode = FlorMode.SIN_FLOR,
    val contraflor: Boolean = true,
    val contraflorAlResto: Boolean = true,
    val envido: Boolean = true,
    val realEnvido: Boolean = true,
    val faltaEnvido: Boolean = true,
    val maxEnvidos: Int = 2,
    val trucoAntesDeEnvido: Boolean = true,
    val envidoHastaSegundaCarta: Boolean = false,
    val empateEnvido: EmpateEnvido = EmpateEnvido.NO_MANO,
    /** A 30: se juegan malas (a 15) y buenas (otra vez a 15, desde cero). */
    val malasYBuenas: Boolean = false,
    val timeoutCantoMs: Long = 20_000,
    val timeoutTurnoMs: Long = 45_000,
) {
    init {
        require(jugadores == 2 || jugadores == 4 || jugadores == 6) {
            "Cantidad de jugadores inválida: $jugadores (permitidos: 2, 4, 6)"
        }
        require(targetScore in 1..30) { "targetScore inválido: $targetScore" }
        require(maxEnvidos in 1..2) { "maxEnvidos inválido: $maxEnvidos" }
        require(!malasYBuenas || targetScore == 30) { "malas y buenas se juega a 30" }
    }

    /** Con flor habilitada no se juega el envido en la misma mano. */
    val envidoHabilitado: Boolean get() = envido && flor == FlorMode.SIN_FLOR
}

enum class FlorMode { SIN_FLOR, FLOR }

enum class EmpateEnvido { NO_MANO, MANO }
