package ar.truco.engine

import ar.truco.engine.Resultado.Ilegal
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** Flor con contraflor y partidos a 30 con malas y buenas. */
class FlorYEtapasTest {

    private val e = Palo.ESPADA
    private val b = Palo.BASTO
    private val o = Palo.ORO
    private val c = Palo.COPA

    private fun carta(palo: Palo, numero: Int) = Manos.c(palo, numero)

    private fun Estado.jugada(jugador: Int, accion: Accion): Estado = Motor.aplicar(this, jugador, accion).ok()

    private fun Estado.jugar(jugador: Int, carta: Carta): Estado = jugada(jugador, Accion.JugarCarta(carta))

    private fun Estado.cantar(jugador: Int, canto: Canto): Estado = jugada(jugador, Accion.Cantar(canto))

    private fun Estado.responder(jugador: Int, respuesta: Respuesta): Estado =
        jugada(jugador, Accion.Responder(respuesta))

    private val configFlor = RulesConfig(jugadores = 2, flor = FlorMode.FLOR)

    /** Los dos equipos tienen flor; el equipo 0 tiene 38 y el 1 tiene 29. */
    private fun mesaConFlor(): Estado = Manos.estado(
        listOf(
            listOf(carta(e, 7), carta(e, 6), carta(e, 5)), // 20+7+6+5 = 38
            listOf(carta(o, 4), carta(o, 3), carta(o, 2)), // 20+4+3+2 = 29
        ),
        config = configFlor,
    ).copy(fase = Fase.FLOR, turno = 0, envidoCerrado = true)

    @Test
    fun `flor con rival se compara y paga tres`() {
        var estado = mesaConFlor()
        estado = estado.cantar(0, Canto.FLOR)
        assertEquals(TipoNegociacion.FLOR, estado.negociacion?.tipo)
        assertEquals(1, estado.negociacion?.respondedor)

        estado = estado.responder(1, Respuesta.QUIERO)
        assertEquals(listOf(3, 0), estado.puntajes)
        assertEquals(Fase.JUEGO, estado.fase)
        assertEquals(0, estado.turno)
        assertNull(estado.negociacion)
    }

    @Test
    fun `la contraflor duplica a seis`() {
        var estado = mesaConFlor()
        estado = estado.cantar(0, Canto.FLOR)
        estado = estado.cantar(1, Canto.CONTRAFLOR)
        assertEquals(Canto.CONTRAFLOR, estado.negociacion?.canto)
        assertEquals(0, estado.negociacion?.respondedor)

        estado = estado.responder(0, Respuesta.QUIERO)
        assertEquals(listOf(6, 0), estado.puntajes)
        assertEquals(Fase.JUEGO, estado.fase)
    }

    @Test
    fun `la contraflor al resto vale lo que falta para ganar`() {
        var estado = mesaConFlor().copy(puntajes = listOf(12, 9))
        estado = estado.cantar(0, Canto.FLOR)
        estado = estado.cantar(1, Canto.CONTRAFLOR)
        estado = estado.cantar(0, Canto.CONTRAFLOR_AL_RESTO)
        assertEquals(1, estado.negociacion?.respondedor)

        estado = estado.responder(1, Respuesta.QUIERO)
        // Falta = 15 - 12 = 3, y lo gana el equipo 0 (tiene la flor más alta).
        assertEquals(Fase.FIN_PARTIDA, estado.fase)
        assertEquals(listOf(15, 9), estado.puntajes)
    }

    @Test
    fun `si el rival no tiene flor se cobra directo`() {
        var estado = Manos.estado(
            listOf(
                listOf(carta(e, 7), carta(e, 6), carta(e, 5)), // flor
                listOf(carta(o, 12), carta(b, 11), carta(c, 10)), // sin flor
            ),
            config = configFlor,
        ).copy(fase = Fase.FLOR, turno = 0, envidoCerrado = true)

        assertEquals(setOf(TipoAccion.CANTAR_FLOR), Motor.accionesLegales(estado, 0))
        estado = estado.cantar(0, Canto.FLOR)
        assertEquals(listOf(3, 0), estado.puntajes)
        assertEquals(Fase.JUEGO, estado.fase)
        assertNull(estado.negociacion)
    }

    @Test
    fun `la flor no se puede rechazar`() {
        val estado = mesaConFlor().cantar(0, Canto.FLOR)
        val resultado = Motor.aplicar(estado, 1, Accion.Responder(Respuesta.NO_QUIERO))
        assertTrue(resultado is Ilegal)
        assertTrue(Motor.accionesLegales(estado, 1).none { it == TipoAccion.RESPONDER_NO_QUIERO })
    }

    @Test
    fun `la contraflor se puede desactivar por configuracion`() {
        val sinContraflor = configFlor.copy(contraflor = false, contraflorAlResto = false)
        val estado = mesaConFlor().copy(config = sinContraflor).cantar(0, Canto.FLOR)
        assertEquals(setOf(TipoAccion.RESPONDER_QUIERO), Motor.accionesLegales(estado, 1))
        assertTrue(Motor.aplicar(estado, 1, Accion.Cantar(Canto.CONTRAFLOR)) is Ilegal)
    }

    // ------------------------------------------------------------ malas y buenas

    private val configTreinta = RulesConfig(jugadores = 2, targetScore = 30, malasYBuenas = true)

    private fun mesaFuerte(): Estado = Manos.estado(
        listOf(
            listOf(carta(e, 1), carta(b, 1), carta(c, 3)),
            listOf(carta(o, 12), carta(b, 11), carta(c, 10)),
        ),
        config = configTreinta,
    )

    private fun Estado.ganarManoEntera(): Estado =
        jugar(0, carta(e, 1))
            .jugar(1, carta(o, 12))
            .jugar(0, carta(b, 1))
            .jugar(1, carta(b, 11))

    @Test
    fun `en malas al llegar a quince vuelve el marcador a cero y pasa a buenas`() {
        var estado = mesaFuerte().copy(puntajes = listOf(14, 8), etapa = Etapa.MALAS)
        assertEquals(15, estado.objetivoEtapa)
        assertEquals(1, estado.puntajes.size - 1)

        estado = estado.ganarManoEntera()
        assertEquals(Fase.MANO_CERRADA, estado.fase)
        assertEquals(Etapa.BUENAS, estado.etapa)
        assertEquals(listOf(0, 0), estado.puntajes)
        assertEquals(listOf(15, 8), estado.puntajesMalas)
        assertEquals(0, estado.malasGanadasPor)
        assertTrue(estado.eventos.any { it is Evento.FinDeMalas })
        assertTrue(estado.eventos.none { it is Evento.PartidaGanada })

        val siguiente = Motor.repartir(estado).ok()
        assertEquals(Etapa.BUENAS, siguiente.etapa)
        assertEquals(listOf(0, 0), siguiente.puntajes)
        assertEquals(1, siguiente.manoNumero)
    }

    @Test
    fun `en buenas al llegar a quince termina la partida`() {
        var estado = mesaFuerte().copy(puntajes = listOf(14, 10), etapa = Etapa.BUENAS, malasGanadasPor = 1, puntajesMalas = listOf(9, 15))
        estado = estado.ganarManoEntera()
        assertEquals(Fase.FIN_PARTIDA, estado.fase)
        assertEquals(listOf(15, 10), estado.puntajes)
        assertEquals(1, estado.malasGanadasPor)
        assertTrue(estado.eventos.any { it is Evento.PartidaGanada })
    }

    @Test
    fun `la falta envido usa el objetivo de la etapa`() {
        val enMalas = mesaFuerte().copy(puntajes = listOf(12, 9), etapa = Etapa.MALAS)
        assertEquals(3, Motor.faltaEnvido(enMalas))

        val enUnica = mesaFuerte().copy(config = configTreinta.copy(malasYBuenas = false), puntajes = listOf(20, 9), etapa = Etapa.UNICA)
        assertEquals(10, Motor.faltaEnvido(enUnica))
    }

    @Test
    fun `malas y buenas exige partido a treinta`() {
        org.junit.jupiter.api.assertThrows<IllegalArgumentException> {
            RulesConfig(jugadores = 2, targetScore = 15, malasYBuenas = true)
        }
    }
}
