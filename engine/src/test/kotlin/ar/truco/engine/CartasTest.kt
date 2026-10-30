package ar.truco.engine

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CartasTest {

    private fun c(palo: Palo, numero: Int) = Carta(palo, numero)

    @Test
    fun `la jerarquia completa va de mayor a menor`() {
        val ordenados = listOf(
            c(Palo.ESPADA, 1),
            c(Palo.BASTO, 1),
            c(Palo.ESPADA, 7),
            c(Palo.ORO, 7),
            c(Palo.COPA, 3),
            c(Palo.ORO, 2),
            c(Palo.COPA, 1),
            c(Palo.ORO, 12),
            c(Palo.ORO, 11),
            c(Palo.ORO, 10),
            c(Palo.COPA, 7),
            c(Palo.ORO, 6),
            c(Palo.ORO, 5),
            c(Palo.ORO, 4),
        )
        val poderes = ordenados.map { Cartas.poder(it) }
        assertEquals(poderes.sortedDescending(), poderes, "La lista debe estar ordenada de mayor a menor")
        assertEquals(poderes.distinct().size, poderes.size, "No debería haber poderes repetidos en esta lista")
    }

    @Test
    fun `los tres empatan entre si y los sietes falsos tambien`() {
        assertEquals(Cartas.poder(c(Palo.ESPADA, 3)), Cartas.poder(c(Palo.ORO, 3)))
        assertEquals(Cartas.poder(c(Palo.COPA, 7)), Cartas.poder(c(Palo.BASTO, 7)))
        assertEquals(Cartas.poder(c(Palo.COPA, 1)), Cartas.poder(c(Palo.ORO, 1)))
        assertTrue(Cartas.poder(c(Palo.ESPADA, 3)) > Cartas.poder(c(Palo.ORO, 2)))
        assertTrue(Cartas.poder(c(Palo.COPA, 1)) > Cartas.poder(c(Palo.ORO, 12)))
    }

    @Test
    fun `el mazo tiene 40 cartas distintas`() {
        val mazo = Cartas.mazoCompleto()
        assertEquals(40, mazo.size)
        assertEquals(40, mazo.distinct().size)
        assertEquals(4, mazo.groupBy { it.palo }.size)
        assertTrue(mazo.none { it.numero in 8..9 })
    }

    @Test
    fun `el mezclado es determinista con la misma semilla`() {
        val a = Cartas.mezclar(Cartas.mazoCompleto(), kotlin.random.Random(42))
        val b = Cartas.mezclar(Cartas.mazoCompleto(), kotlin.random.Random(42))
        val c = Cartas.mezclar(Cartas.mazoCompleto(), kotlin.random.Random(43))
        assertEquals(a, b)
        assertTrue(a != c)
        assertEquals(Cartas.mazoCompleto().toSet(), a.toSet())
    }
}
