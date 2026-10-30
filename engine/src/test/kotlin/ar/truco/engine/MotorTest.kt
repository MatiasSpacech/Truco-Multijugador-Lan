package ar.truco.engine

import ar.truco.engine.Resultado.Ilegal
import ar.truco.engine.Resultado.Ok
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** Estado con manos fijas: el reparto real se hace con semilla y luego se reemplazan las cartas. */
object Manos {
    fun c(palo: Palo, numero: Int) = Carta(palo, numero)

    fun estado(
        manos: List<List<Carta>>,
        config: RulesConfig = RulesConfig(jugadores = manos.size),
        puntajes: List<Int> = List(2) { 0 },
        seed: Long = 7L,
    ): Estado {
        val nombres = manos.indices.map { "J$it" }
        val base = Motor.nuevaPartida(config, nombres, seed).ok()
        val repartido = Motor.repartir(base).ok()
        return repartido.copy(
            fase = Fase.JUEGO,
            manos = manos,
            manosRepartidas = manos,
            puntajes = puntajes,
            turno = 0,
            valorMano = 1,
            bazas = emptyList(),
            negociacion = null,
            envidoCerrado = !config.envidoHabilitado,
            florDeclaraciones = emptyList(),
            envidoValores = emptyList(),
        )
    }
}

class MotorTest {

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

    private fun Estado.falla(jugador: Int, accion: Accion): String {
        val r = Motor.aplicar(this, jugador, accion)
        assertTrue(r is Ilegal, "Se esperaba una acción ilegal y salió $r")
        return (r as Ilegal).motivo
    }

    @Test
    fun `jugar una carta avanza el turno y registra la jugada`() {
        val estado = Manos.estado(
            listOf(
                listOf(carta(e, 1), carta(o, 4), carta(c, 5)),
                listOf(carta(o, 12), carta(b, 11), carta(c, 10)),
            ),
        )
        val despues = estado.jugar(0, carta(e, 1))
        assertEquals(1, despues.turno)
        assertEquals(1, despues.bazas.size)
        assertEquals(1, despues.bazas[0].jugadas.size)
        assertEquals(2, despues.manos[0].size)
        assertEquals(3, despues.manos[1].size)
        assertTrue(despues.eventos.any { it is Evento.CartaJugada })
    }

    @Test
    fun `no se puede jugar fuera de turno ni una carta que no se tiene`() {
        val estado = Manos.estado(
            listOf(
                listOf(carta(e, 1), carta(o, 4), carta(c, 5)),
                listOf(carta(o, 12), carta(b, 11), carta(c, 10)),
            ),
        )
        estado.falla(1, Accion.JugarCarta(carta(o, 12)))
        estado.falla(0, Accion.JugarCarta(carta(o, 12)))
    }

    @Test
    fun `gana la mano el que se lleva dos bazas`() {
        var estado = Manos.estado(
            listOf(
                listOf(carta(e, 1), carta(o, 4), carta(c, 5)),
                listOf(carta(o, 12), carta(b, 11), carta(c, 10)),
            ),
        )
        estado = estado.jugar(0, carta(e, 1)).jugar(1, carta(o, 12)) // baza 1 → 0
        assertEquals(0, estado.bazas[0].ganador)
        assertEquals(0, estado.turno)

        estado = estado.jugar(0, carta(o, 4)).jugar(1, carta(b, 11)) // baza 2 → 1
        assertEquals(1, estado.bazas[1].ganador)

        estado = estado.jugar(1, carta(c, 10)).jugar(0, carta(c, 5)) // baza 3 → 1
        assertEquals(Fase.MANO_CERRADA, estado.fase)
        assertEquals(1, estado.manoGanadaPor)
        assertEquals(listOf(0, 1), estado.puntajes)
    }

    @Test
    fun `la parda la gana el mano`() {
        var estado = Manos.estado(
            listOf(
                listOf(carta(e, 3), carta(o, 4), carta(c, 5)),
                listOf(carta(b, 3), carta(o, 11), carta(c, 12)),
            ),
        )
        estado = estado.jugar(0, carta(e, 3)).jugar(1, carta(b, 3))
        assertTrue(estado.bazas[0].parda)
        assertEquals(0, estado.bazas[0].ganador)
        assertEquals(0, estado.turno)
    }

    @Test
    fun `la parda de la segunda baza la gana quien viene ganando`() {
        var estado = Manos.estado(
            listOf(
                listOf(carta(e, 1), carta(o, 3), carta(c, 5)),
                listOf(carta(e, 12), carta(c, 3), carta(b, 4)),
            ),
        )
        estado = estado.jugar(0, carta(e, 1)).jugar(1, carta(e, 12)) // baza 1 → 0
        estado = estado.jugar(0, carta(o, 3)).jugar(1, carta(c, 3))   // parda → gana el 0
        assertTrue(estado.bazas[1].parda)
        assertEquals(0, estado.bazas[1].ganador)
        assertEquals(Fase.MANO_CERRADA, estado.fase)
        assertEquals(0, estado.manoGanadaPor)
    }

    @Test
    fun `truco aceptado hace valer dos puntos la mano`() {
        var estado = Manos.estado(
            listOf(
                listOf(carta(e, 1), carta(o, 7), carta(c, 5)),
                listOf(carta(o, 12), carta(b, 11), carta(c, 10)),
            ),
        )
        estado = estado.cantar(0, Canto.TRUCO)
        assertEquals(1, estado.negociacion?.respondedor)
        estado = estado.responder(1, Respuesta.QUIERO)
        assertEquals(2, estado.valorMano)
        assertEquals(null, estado.negociacion)

        estado = estado.jugar(0, carta(e, 1)).jugar(1, carta(o, 12))
            .jugar(0, carta(o, 7)).jugar(1, carta(b, 11))
        assertEquals(Fase.MANO_CERRADA, estado.fase)
        assertEquals(listOf(2, 0), estado.puntajes)
    }

    @Test
    fun `truco no querido da un punto y termina la mano`() {
        var estado = Manos.estado(
            listOf(
                listOf(carta(e, 1), carta(o, 4), carta(c, 5)),
                listOf(carta(o, 12), carta(b, 11), carta(c, 10)),
            ),
        )
        estado = estado.cantar(0, Canto.TRUCO).responder(1, Respuesta.NO_QUIERO)
        assertEquals(Fase.MANO_CERRADA, estado.fase)
        assertEquals(listOf(1, 0), estado.puntajes)
    }

    @Test
    fun `retruco y vale cuatro respetan los valores`() {
        var estado = Manos.estado(
            listOf(
                listOf(carta(e, 1), carta(o, 4), carta(c, 5)),
                listOf(carta(o, 12), carta(b, 11), carta(c, 10)),
            ),
        )
        // retruco rechazado: 2 puntos
        estado = estado.cantar(0, Canto.TRUCO).responder(1, Respuesta.QUIERO)
        estado = estado.cantar(0, Canto.RETRUCO).responder(1, Respuesta.NO_QUIERO)
        assertEquals(listOf(2, 0), estado.puntajes)
        assertEquals(Fase.MANO_CERRADA, estado.fase)

        // vale cuatro rechazado: 3 puntos
        var otro = Manos.estado(
            listOf(
                listOf(carta(e, 1), carta(o, 4), carta(c, 5)),
                listOf(carta(o, 12), carta(b, 11), carta(c, 10)),
            ),
        )
        otro = otro.cantar(0, Canto.TRUCO).responder(1, Respuesta.QUIERO)
        otro = otro.cantar(0, Canto.RETRUCO).responder(1, Respuesta.QUIERO)
        assertEquals(3, otro.valorMano)
        otro = otro.cantar(0, Canto.VALE_CUATRO).responder(1, Respuesta.NO_QUIERO)
        assertEquals(listOf(3, 0), otro.puntajes)
    }

    @Test
    fun `se puede escalar el truco en la misma negociacion`() {
        var estado = Manos.estado(
            listOf(
                listOf(carta(e, 1), carta(o, 4), carta(c, 5)),
                listOf(carta(o, 12), carta(b, 11), carta(c, 10)),
            ),
        )
        estado = estado.cantar(0, Canto.TRUCO)
        assertEquals(1, estado.negociacion?.respondedor)
        estado = estado.cantar(1, Canto.RETRUCO) // escalada del respondedor
        assertEquals(0, estado.negociacion?.respondedor)
        estado = estado.responder(0, Respuesta.QUIERO)
        assertEquals(3, estado.valorMano)
    }

    @Test
    fun `irse al mazo cobra el valor vigente`() {
        var estado = Manos.estado(
            listOf(
                listOf(carta(e, 1), carta(o, 4), carta(c, 5)),
                listOf(carta(o, 12), carta(b, 11), carta(c, 10)),
            ),
        )
        estado = estado.cantar(0, Canto.TRUCO).responder(1, Respuesta.QUIERO)
        estado = estado.jugada(0, Accion.IrseAlMazo)
        assertEquals(Fase.MANO_CERRADA, estado.fase)
        assertEquals(listOf(0, 2), estado.puntajes)
    }

    @Test
    fun `el envido lo gana quien tiene mas puntos de envido aunque no haya cantado`() {
        var estado = Manos.estado(
            listOf(
                listOf(carta(c, 7), carta(c, 6), carta(o, 12)), // 33
                listOf(carta(b, 12), carta(e, 11), carta(c, 10)), // 0
            ),
        )
        estado = estado.cantar(1, Canto.ENVIDO) // canta el que tiene 0
        assertEquals(0, estado.negociacion?.respondedor)
        estado = estado.responder(0, Respuesta.QUIERO)
        assertEquals(listOf(2, 0), estado.puntajes)
        assertEquals(listOf(33, 0), estado.envidoValores)
        assertTrue(estado.envidoCerrado)
        assertEquals(Fase.JUEGO, estado.fase)
    }

    @Test
    fun `envido escalado y rechazado paga la cadena anterior`() {
        var estado = Manos.estado(
            listOf(
                listOf(carta(c, 7), carta(c, 6), carta(o, 12)),
                listOf(carta(b, 12), carta(e, 11), carta(c, 10)),
            ),
        )
        estado = estado.cantar(0, Canto.ENVIDO)
        estado = estado.cantar(1, Canto.REAL_ENVIDO)
        assertEquals(0, estado.negociacion?.respondedor)
        estado = estado.responder(0, Respuesta.NO_QUIERO)
        assertEquals(listOf(0, 2), estado.puntajes)
    }

    @Test
    fun `falta envido vale lo que le falta al que va primero y puede cerrar la partida`() {
        var estado = Manos.estado(
            listOf(
                listOf(carta(c, 7), carta(c, 6), carta(o, 12)), // 33
                listOf(carta(b, 12), carta(e, 11), carta(c, 10)), // 0
            ),
            puntajes = listOf(12, 9),
        )
        assertEquals(3, Motor.faltaEnvido(estado))
        estado = estado.cantar(0, Canto.FALTA_ENVIDO).responder(1, Respuesta.QUIERO)
        assertEquals(Fase.FIN_PARTIDA, estado.fase)
        assertEquals(listOf(15, 9), estado.puntajes)
        assertTrue(estado.eventos.any { it is Evento.PartidaGanada })
    }

    @Test
    fun `el envido no se puede cantar despues de la primera carta ni despues del truco`() {
        var estado = Manos.estado(
            listOf(
                listOf(carta(c, 7), carta(c, 6), carta(o, 12)),
                listOf(carta(b, 12), carta(e, 11), carta(c, 10)),
            ),
        )
        estado = estado.jugar(0, carta(c, 7))
        estado.falla(1, Accion.Cantar(Canto.ENVIDO))

        var otro = Manos.estado(
            listOf(
                listOf(carta(c, 7), carta(c, 6), carta(o, 12)),
                listOf(carta(b, 12), carta(e, 11), carta(c, 10)),
            ),
        )
        otro = otro.cantar(0, Canto.TRUCO).responder(1, Respuesta.QUIERO)
        otro.falla(1, Accion.Cantar(Canto.ENVIDO))
    }

    @Test
    fun `con trucoAntesDeEnvido apagado primero va el envido`() {
        val estado = Manos.estado(
            listOf(
                listOf(carta(c, 7), carta(c, 6), carta(o, 12)),
                listOf(carta(b, 12), carta(e, 11), carta(c, 10)),
            ),
            config = RulesConfig(jugadores = 2, trucoAntesDeEnvido = false),
        )
        estado.falla(0, Accion.Cantar(Canto.TRUCO))
    }

    @Test
    fun `en cuatro jugadores el envido se compara por equipo`() {
        var estado = Manos.estado(
            listOf(
                listOf(carta(c, 7), carta(c, 6), carta(o, 12)),   // 33 (equipo 0)
                listOf(carta(b, 12), carta(e, 11), carta(c, 10)), // 0  (equipo 1)
                listOf(carta(b, 3), carta(b, 2), carta(o, 5)),    // 25 (equipo 0)
                listOf(carta(e, 4), carta(e, 5), carta(o, 6)),    // 29 (equipo 1)
            ),
            config = RulesConfig(jugadores = 4),
        )
        assertEquals(listOf(0, 1, 0, 1), estado.asientos.map { it.equipo })
        estado = estado.cantar(1, Canto.ENVIDO)
        assertEquals(2, estado.negociacion?.respondedor)
        estado = estado.responder(2, Respuesta.QUIERO)
        assertEquals(listOf(2, 0), estado.puntajes)
        assertEquals(listOf(33, 0, 25, 29), estado.envidoValores)
    }

    @Test
    fun `en seis jugadores hay dos equipos de tres y el rival siguiente alterna`() {
        val estado = Manos.estado(
            listOf(
                listOf(carta(c, 1), carta(c, 2), carta(c, 3)),
                listOf(carta(b, 1), carta(b, 2), carta(b, 3)),
                listOf(carta(o, 1), carta(o, 2), carta(o, 3)),
                listOf(carta(e, 1), carta(e, 2), carta(e, 3)),
                listOf(carta(c, 4), carta(c, 5), carta(c, 6)),
                listOf(carta(b, 4), carta(b, 5), carta(b, 6)),
            ),
            config = RulesConfig(jugadores = 6),
        )
        assertEquals(listOf(0, 1, 0, 1, 0, 1), estado.asientos.map { it.equipo })
        assertEquals(1, Motor.siguienteRival(estado, 0))
        assertEquals(2, Motor.siguienteRival(estado, 1))
        assertEquals(3, Motor.siguienteRival(estado, 2))
        assertEquals(4, Motor.siguienteRival(estado, 3))
        assertEquals(5, Motor.siguienteRival(estado, 4))
        assertEquals(0, Motor.siguienteRival(estado, 5))
    }

    @Test
    fun `la flor se canta y paga tres puntos`() {
        var estado = Manos.estado(
            listOf(
                listOf(carta(e, 7), carta(e, 6), carta(e, 5)),   // flor 38
                listOf(carta(b, 12), carta(e, 11), carta(c, 10)),
            ),
            config = RulesConfig(jugadores = 2, flor = FlorMode.FLOR),
        ).copy(fase = Fase.FLOR, turno = 0)

        assertTrue(estado.tieneFlor(0))
        estado = estado.cantar(0, Canto.FLOR)
        assertEquals(Fase.JUEGO, estado.fase)
        assertEquals(listOf(3, 0), estado.puntajes)
        assertTrue(estado.envidoCerrado)
    }

    @Test
    fun `la mano rota despues de cada mano terminada`() {
        var estado = Manos.estado(
            listOf(
                listOf(carta(e, 1), carta(o, 4), carta(c, 5)),
                listOf(carta(o, 12), carta(b, 11), carta(c, 10)),
            ),
        )
        estado = estado.jugada(0, Accion.IrseAlMazo)
        assertEquals(Fase.MANO_CERRADA, estado.fase)
        assertEquals(0, estado.manoNumero)
        assertEquals(0, estado.manoIndex)

        val siguiente = Motor.repartir(estado).ok()
        assertEquals(1, siguiente.manoNumero)
        assertEquals(1, siguiente.manoIndex)
        assertEquals(1, siguiente.turno)
        assertEquals(Fase.JUEGO, siguiente.fase)
        assertEquals(1, siguiente.valorMano)
    }

    @Test
    fun `las acciones legales reflejan turno y cantos disponibles`() {
        val estado = Manos.estado(
            listOf(
                listOf(carta(e, 1), carta(o, 4), carta(c, 5)),
                listOf(carta(o, 12), carta(b, 11), carta(c, 10)),
            ),
        )
        val deMano = Motor.accionesLegales(estado, 0)
        assertTrue(TipoAccion.JUGAR_CARTA in deMano)
        assertTrue(TipoAccion.IRSE_AL_MAZO in deMano)
        assertTrue(TipoAccion.CANTAR_ENVIDO in deMano)
        assertTrue(TipoAccion.CANTAR_REAL_ENVIDO in deMano)
        assertTrue(TipoAccion.CANTAR_FALTA_ENVIDO in deMano)
        assertTrue(TipoAccion.CANTAR_TRUCO in deMano)

        val dePie = Motor.accionesLegales(estado, 1)
        assertTrue(TipoAccion.JUGAR_CARTA !in dePie)
        assertTrue(TipoAccion.CANTAR_ENVIDO in dePie)

        val conTruco = estado.cantar(0, Canto.TRUCO)
        val respuesta = Motor.accionesLegales(conTruco, 1)
        assertTrue(TipoAccion.RESPONDER_QUIERO in respuesta)
        assertTrue(TipoAccion.RESPONDER_NO_QUIERO in respuesta)
        assertTrue(TipoAccion.CANTAR_RETRUCO in respuesta)
        assertEquals(emptySet<TipoAccion>(), Motor.accionesLegales(conTruco, 0))
    }

    @Test
    fun `no se puede responder ni cantar cuando no corresponde`() {
        val estado = Manos.estado(
            listOf(
                listOf(carta(e, 1), carta(o, 4), carta(c, 5)),
                listOf(carta(o, 12), carta(b, 11), carta(c, 10)),
            ),
        )
        estado.falla(0, Accion.Responder(Respuesta.QUIERO))
        val conTruco = estado.cantar(0, Canto.TRUCO)
        conTruco.falla(0, Accion.Responder(Respuesta.QUIERO))
    }

    @Test
    fun `no se puede cantar un canto que todavia no corresponde`() {
        val estado = Manos.estado(
            listOf(
                listOf(carta(e, 1), carta(o, 4), carta(c, 5)),
                listOf(carta(o, 12), carta(b, 11), carta(c, 10)),
            ),
        )
        estado.falla(0, Accion.Cantar(Canto.RETRUCO))
        estado.falla(0, Accion.Cantar(Canto.VALE_CUATRO))
        val conTruco = estado.cantar(0, Canto.TRUCO)
        conTruco.falla(1, Accion.Cantar(Canto.VALE_CUATRO))
    }

    @Test
    fun `con flor apagada no se canta flor`() {
        val estado = Manos.estado(
            listOf(
                listOf(carta(e, 7), carta(e, 6), carta(e, 5)),
                listOf(carta(b, 12), carta(e, 11), carta(c, 10)),
            ),
        )
        estado.falla(0, Accion.Cantar(Canto.FLOR))
    }

    @Test
    fun `la partida no sigue despues del final`() {
        val estado = Manos.estado(
            listOf(
                listOf(carta(e, 1), carta(o, 4), carta(c, 5)),
                listOf(carta(o, 12), carta(b, 11), carta(c, 10)),
            ),
            puntajes = listOf(15, 0),
        ).copy(fase = Fase.FIN_PARTIDA)
        estado.falla(0, Accion.JugarCarta(carta(e, 1)))
        assertTrue(Motor.repartir(estado) is Ilegal)
    }

    @Test
    fun `el reparto siempre entrega tres cartas distintas por jugador`() {
        for (n in listOf(2, 4, 6)) {
            val nombres = (0 until n).map { "J$it" }
            val config = RulesConfig(jugadores = n)
            val estado = Motor.repartir(Motor.nuevaPartida(config, nombres, 1234L).ok()).ok()
            assertEquals(n, estado.manos.size)
            estado.manos.forEach { assertEquals(3, it.size) }
            val todas = estado.manos.flatten()
            assertEquals(n * 3, todas.distinct().size)
            assertTrue(todas.all { it in Cartas.mazoCompleto() })
        }
    }

    @Test
    fun `nuevaPartida valida la cantidad de jugadores`() {
        assertTrue(Motor.nuevaPartida(RulesConfig(jugadores = 4), listOf("a", "b"), 1) is Ilegal)
        assertTrue(Motor.nuevaPartida(RulesConfig(jugadores = 2), listOf("a", " "), 1) is Ilegal)
        assertTrue(Motor.nuevaPartida(RulesConfig(jugadores = 2), listOf("a", "b"), 1) is Ok)
    }
}
