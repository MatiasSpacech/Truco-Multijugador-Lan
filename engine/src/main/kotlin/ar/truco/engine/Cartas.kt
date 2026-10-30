package ar.truco.engine

import kotlin.random.Random

/** Palos del mazo español de 40 cartas. */
enum class Palo { ESPADA, BASTO, ORO, COPA }

/** Carta del mazo español: números 1..7, 10 (sota), 11 (caballo), 12 (rey). */
data class Carta(val palo: Palo, val numero: Int) {
    init {
        require(numero in NUMEROS) { "Número de carta inválido: $numero" }
    }

    override fun toString(): String = "$numero de ${palo.name.lowercase()}"

    companion object {
        val NUMEROS: Set<Int> = setOf(1, 2, 3, 4, 5, 6, 7, 10, 11, 12)
    }
}

/** Jerarquía, valores de envido y mazo. Ver docs/01-REGLAS.md §2 y §3. */
object Cartas {

    /** Poder relativo 1..14. Mayor poder gana la baza; poderes iguales = parda. */
    fun poder(carta: Carta): Int = when {
        carta.palo == Palo.ESPADA && carta.numero == 1 -> 14 // ancho de espada
        carta.palo == Palo.BASTO && carta.numero == 1 -> 13  // ancho de basto
        carta.palo == Palo.ESPADA && carta.numero == 7 -> 12
        carta.palo == Palo.ORO && carta.numero == 7 -> 11
        carta.numero == 3 -> 10
        carta.numero == 2 -> 9
        carta.numero == 1 -> 8                               // anchos falsos (copa / oro)
        carta.numero == 12 -> 7
        carta.numero == 11 -> 6
        carta.numero == 10 -> 5
        carta.numero == 7 -> 4                               // sietes falsos (copa / basto)
        carta.numero == 6 -> 3
        carta.numero == 5 -> 2
        carta.numero == 4 -> 1
        else -> error("Carta sin poder: $carta")
    }

    /** Figuras (10, 11, 12) valen 0 para el envido. */
    fun valorEnvido(carta: Carta): Int = if (carta.numero <= 7) carta.numero else 0

    fun mazoCompleto(): List<Carta> =
        Palo.entries.flatMap { palo -> Carta.NUMEROS.sorted().map { Carta(palo, it) } }

    /** Mezcla determinista: mismo [random] ⇒ mismo resultado. */
    fun mezclar(mazo: List<Carta>, random: Random): List<Carta> = mazo.shuffled(random)
}
