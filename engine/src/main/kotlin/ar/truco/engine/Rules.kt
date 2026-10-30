package ar.truco.engine

/**
 * Configuración de reglas de una partida. Ver docs/01-REGLAS.md.
 * Todos los valores por defecto corresponden al MVP (2/4 jugadores, a 15, sin flor).
 */
data class RulesConfig(
    val jugadores: Int = 2,
    val targetScore: Int = 15,
    val flor: FlorMode = FlorMode.SIN_FLOR,
    val envido: Boolean = true,
    val realEnvido: Boolean = true,
    val faltaEnvido: Boolean = true,
    val maxEnvidos: Int = 2,
    val trucoAntesDeEnvido: Boolean = true,
    val envidoHastaSegundaCarta: Boolean = false,
    val empateEnvido: EmpateEnvido = EmpateEnvido.NO_MANO,
    val timeoutCantoMs: Long = 20_000,
    val timeoutTurnoMs: Long = 45_000,
) {
    init {
        require(jugadores == 2 || jugadores == 4 || jugadores == 6) {
            "Cantidad de jugadores inválida: $jugadores (permitidos: 2, 4, 6)"
        }
        require(targetScore in 1..30) { "targetScore inválido: $targetScore" }
        require(maxEnvidos in 1..2) { "maxEnvidos inválido: $maxEnvidos" }
    }

    /** Con flor habilitada no se juega el envido en la misma mano. */
    val envidoHabilitado: Boolean get() = envido && flor == FlorMode.SIN_FLOR
}

enum class FlorMode { SIN_FLOR, FLOR }

enum class EmpateEnvido { NO_MANO, MANO }
