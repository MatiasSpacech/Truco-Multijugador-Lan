package ar.truco.engine

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class EnvidoTest {

    private fun c(palo: Palo, numero: Int) = Carta(palo, numero)

    @Test
    fun `sin cartas del mismo palo vale la mayor suelta`() {
        assertEquals(5, Envido.valor(listOf(c(Palo.COPA, 12), c(Palo.ORO, 5), c(Palo.BASTO, 4))))
        assertEquals(7, Envido.valor(listOf(c(Palo.COPA, 7), c(Palo.ORO, 2), c(Palo.BASTO, 1))))
    }

    @Test
    fun `las figuras valen cero y no suman`() {
        assertEquals(0, Envido.valor(listOf(c(Palo.COPA, 12), c(Palo.ORO, 11), c(Palo.BASTO, 10))))
        assertEquals(5, Envido.valor(listOf(c(Palo.COPA, 5), c(Palo.ORO, 11), c(Palo.BASTO, 10))))
    }

    @Test
    fun `con dos del mismo palo es 20 mas la suma`() {
        assertEquals(33, Envido.valor(listOf(c(Palo.COPA, 7), c(Palo.COPA, 6), c(Palo.BASTO, 12))))
        assertEquals(25, Envido.valor(listOf(c(Palo.COPA, 3), c(Palo.COPA, 2), c(Palo.BASTO, 12))))
        assertEquals(28, Envido.valor(listOf(c(Palo.COPA, 1), c(Palo.COPA, 7), c(Palo.BASTO, 4))))
    }

    @Test
    fun `con tres del mismo palo es 20 mas las dos mayores`() {
        assertEquals(25, Envido.valor(listOf(c(Palo.COPA, 3), c(Palo.COPA, 2), c(Palo.COPA, 1))))
        assertEquals(31, Envido.valor(listOf(c(Palo.COPA, 4), c(Palo.COPA, 5), c(Palo.COPA, 6))))
        assertEquals(31, Envido.valor(listOf(c(Palo.COPA, 7), c(Palo.COPA, 4), c(Palo.COPA, 3))))
        assertEquals(20, Envido.valor(listOf(c(Palo.COPA, 12), c(Palo.COPA, 11), c(Palo.COPA, 10))))
    }

    @Test
    fun `la flor es 20 mas las tres cartas y requiere mismo palo`() {
        val flor = listOf(c(Palo.ESPADA, 7), c(Palo.ESPADA, 6), c(Palo.ESPADA, 5))
        assertTrue(Envido.tieneFlor(flor))
        assertEquals(38, Envido.valorFlor(flor))

        val sinFlor = listOf(c(Palo.ESPADA, 7), c(Palo.ESPADA, 6), c(Palo.ORO, 5))
        assertFalse(Envido.tieneFlor(sinFlor))
        assertThrows<IllegalArgumentException> { Envido.valorFlor(sinFlor) }
    }

    @Test
    fun `el envido por equipo toma el mayor de cada equipo`() {
        val manos = listOf(
            listOf(c(Palo.COPA, 3), c(Palo.COPA, 2), c(Palo.BASTO, 12)), // 25
            listOf(c(Palo.ORO, 7), c(Palo.ORO, 6), c(Palo.COPA, 12)),    // 33
            listOf(c(Palo.BASTO, 5), c(Palo.BASTO, 4), c(Palo.ESPADA, 1)), // 29
            listOf(c(Palo.COPA, 1), c(Palo.COPA, 2), c(Palo.ORO, 4)),    // 23
        )
        val equipos = listOf(0, 1, 0, 1)
        assertEquals(listOf(29, 33), Envido.valorPorEquipo(manos, equipos))
    }
}
