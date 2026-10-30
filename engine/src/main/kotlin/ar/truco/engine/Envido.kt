package ar.truco.engine

/** Cálculos de envido y flor. Ver docs/01-REGLAS.md §3 y §4. */
object Envido {

    fun tieneFlor(cartas: List<Carta>): Boolean =
        cartas.size == 3 && cartas.map { it.palo }.distinct().size == 1

    /**
     * Envido: sin cartas del mismo palo, la mayor suelta;
     * con 2 o 3 del mismo palo, 20 + las dos mayores de ese palo.
     */
    fun valor(cartas: List<Carta>): Int {
        require(cartas.isNotEmpty()) { "No se puede calcular el envido de una mano vacía" }
        val porPalo = cartas.groupBy { it.palo }.values.maxBy { it.size }
        return if (porPalo.size == 1) {
            cartas.maxOf { Cartas.valorEnvido(it) }
        } else {
            20 + porPalo.map { Cartas.valorEnvido(it) }.sortedDescending().take(2).sum()
        }
    }

    /** Solo válido si [tieneFlor]. */
    fun valorFlor(cartas: List<Carta>): Int {
        require(tieneFlor(cartas)) { "La mano no tiene flor: $cartas" }
        return 20 + cartas.sumOf { Cartas.valorEnvido(it) }
    }

    /** Máximo envido por equipo, indexado por número de equipo. */
    fun valorPorEquipo(manos: List<List<Carta>>, equipos: List<Int>): List<Int> {
        val equiposDistintos = equipos.distinct().sorted()
        return equiposDistintos.map { eq ->
            manos.filterIndexed { i, _ -> equipos[i] == eq }.maxOfOrNull { valor(it) } ?: 0
        }
    }
}
