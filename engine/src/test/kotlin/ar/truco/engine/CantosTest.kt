package ar.truco.engine

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CantosTest {

    private val config = RulesConfig()

    @Test
    fun `tabla completa de envido aceptado y rechazado`() {
        // (declaraciones, puntos si quiero, puntos si no quiero) con falta = 6
        val falta = 6
        val casos = listOf(
            Triple(listOf(Canto.ENVIDO), 2, 1),
            Triple(listOf(Canto.ENVIDO, Canto.ENVIDO), 4, 2),
            Triple(listOf(Canto.REAL_ENVIDO), 3, 1),
            Triple(listOf(Canto.ENVIDO, Canto.REAL_ENVIDO), 5, 2),
            Triple(listOf(Canto.ENVIDO, Canto.ENVIDO, Canto.REAL_ENVIDO), 7, 4),
            Triple(listOf(Canto.FALTA_ENVIDO), 6, 1),
            Triple(listOf(Canto.ENVIDO, Canto.FALTA_ENVIDO), 8, 2),
        )
        casos.forEach { (declaraciones, quiero, noQuiero) ->
            assertEquals(quiero, Cantos.valorEnvidoAceptado(declaraciones, falta), "aceptado $declaraciones")
            assertEquals(noQuiero, Cantos.valorEnvidoRechazado(declaraciones, falta), "rechazado $declaraciones")
        }
    }

    @Test
    fun `tabla de truco retruco y vale cuatro`() {
        assertEquals(2, Cantos.valorTruco(Canto.TRUCO))
        assertEquals(1, Cantos.valorTrucoRechazado(Canto.TRUCO))
        assertEquals(3, Cantos.valorTruco(Canto.RETRUCO))
        assertEquals(2, Cantos.valorTrucoRechazado(Canto.RETRUCO))
        assertEquals(4, Cantos.valorTruco(Canto.VALE_CUATRO))
        assertEquals(3, Cantos.valorTrucoRechazado(Canto.VALE_CUATRO))
    }

    @Test
    fun `la escalada de envido respeta el orden y los limites`() {
        assertTrue(Cantos.puedeEscalarEnvido(emptyList(), Canto.ENVIDO, config))
        assertTrue(Cantos.puedeEscalarEnvido(emptyList(), Canto.REAL_ENVIDO, config))
        assertTrue(Cantos.puedeEscalarEnvido(emptyList(), Canto.FALTA_ENVIDO, config))

        assertTrue(Cantos.puedeEscalarEnvido(listOf(Canto.ENVIDO), Canto.ENVIDO, config))
        assertFalse(Cantos.puedeEscalarEnvido(listOf(Canto.ENVIDO, Canto.ENVIDO), Canto.ENVIDO, config))

        // no se puede volver atrás en la escalera
        assertFalse(Cantos.puedeEscalarEnvido(listOf(Canto.REAL_ENVIDO), Canto.ENVIDO, config))
        assertFalse(Cantos.puedeEscalarEnvido(listOf(Canto.FALTA_ENVIDO), Canto.ENVIDO, config))
        assertFalse(Cantos.puedeEscalarEnvido(listOf(Canto.FALTA_ENVIDO), Canto.ENVIDO, config))

        // un solo real envido
        assertFalse(Cantos.puedeEscalarEnvido(listOf(Canto.REAL_ENVIDO), Canto.REAL_ENVIDO, config))
        // nada después de la falta envido
        assertFalse(Cantos.puedeEscalarEnvido(listOf(Canto.FALTA_ENVIDO), Canto.FALTA_ENVIDO, config))
        assertFalse(Cantos.puedeEscalarEnvido(listOf(Canto.ENVIDO, Canto.FALTA_ENVIDO), Canto.REAL_ENVIDO, config))
    }

    @Test
    fun `se respeta la configuracion de cantos habilitados`() {
        val sinReal = RulesConfig(realEnvido = false)
        assertFalse(Cantos.puedeEscalarEnvido(emptyList(), Canto.REAL_ENVIDO, sinReal))
        assertTrue(Cantos.puedeEscalarEnvido(emptyList(), Canto.ENVIDO, sinReal))

        val sinFalta = RulesConfig(faltaEnvido = false)
        assertFalse(Cantos.puedeEscalarEnvido(emptyList(), Canto.FALTA_ENVIDO, sinFalta))

        val unSoloEnvido = RulesConfig(maxEnvidos = 1)
        assertFalse(Cantos.puedeEscalarEnvido(listOf(Canto.ENVIDO), Canto.ENVIDO, unSoloEnvido))
    }

    @Test
    fun `la escalera de truco es unica y ascendente`() {
        assertEquals(Canto.TRUCO, Cantos.trucoDisponible(1))
        assertEquals(Canto.RETRUCO, Cantos.trucoDisponible(2))
        assertEquals(Canto.VALE_CUATRO, Cantos.trucoDisponible(3))
        assertEquals(null, Cantos.trucoDisponible(4))
        assertEquals(Canto.RETRUCO, Cantos.siguienteTruco(Canto.TRUCO))
        assertEquals(Canto.VALE_CUATRO, Cantos.siguienteTruco(Canto.RETRUCO))
        assertEquals(null, Cantos.siguienteTruco(Canto.VALE_CUATRO))
    }

    @Test
    fun `con flor habilitada el envido no se juega en la misma mano`() {
        assertFalse(RulesConfig(flor = FlorMode.FLOR).envidoHabilitado)
        assertTrue(RulesConfig(flor = FlorMode.SIN_FLOR).envidoHabilitado)
        assertTrue(RulesConfig(flor = FlorMode.SIN_FLOR, envido = false).envidoHabilitado.not())
    }
}
