package ar.truco.server

import ar.truco.engine.RulesConfig
import ar.truco.protocol.AccionDto
import ar.truco.protocol.ActionPayload
import ar.truco.protocol.CartaDto
import ar.truco.protocol.Cliente
import ar.truco.protocol.ErrorPayload
import ar.truco.protocol.Errores
import ar.truco.protocol.HelloPayload
import ar.truco.protocol.InfoPayload
import ar.truco.protocol.JoinPayload
import ar.truco.protocol.LobbyPayload
import ar.truco.protocol.PROTOCOL_VERSION
import ar.truco.protocol.ReadyPayload
import ar.truco.protocol.RejoinPayload
import ar.truco.protocol.Servidor
import ar.truco.protocol.Sobre
import ar.truco.protocol.StatePayload
import ar.truco.protocol.WelcomePayload
import io.ktor.client.HttpClient
import io.ktor.client.plugins.websocket.DefaultClientWebSocketSession
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocketSession
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.cancel
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.JsonElement
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.seconds

class SalaTest {

    private val configPrueba = ServerConfig(
        rules = RulesConfig(
            jugadores = 2,
            timeoutCantoMs = 250,
            timeoutTurnoMs = 300,
        ),
        pausaEntreManosMs = 80,
        serverVersion = "test",
    )

    // ---------------------------------------------------------------- helpers

    private fun testApp(
        config: ServerConfig = configPrueba,
        bloque: suspend ApplicationTestBuilder.(HttpClient) -> Unit,
    ) = testApplication {
        application { trucoModule(config) }
        startApplication()
        val cliente = createClient { install(WebSockets) }
        bloque(cliente)
    }

    private suspend fun HttpClient.entrar(nombre: String): Par {
        val sesion = webSocketSession("/ws")
        sesion.enviar(Cliente.HELLO, comoJson(HelloPayload(nombre)))
        sesion.esperar(Servidor.WELCOME)

        sesion.enviar(Cliente.JOIN, comoJson(JoinPayload(nombre)))
        val welcome = sesion.esperar(Servidor.WELCOME)
        val payload = desdeJson<WelcomePayload>(welcome.d)
        return Par(sesion, payload.playerToken ?: error("El servidor no devolvió token"))
    }

    private suspend fun DefaultClientWebSocketSession.enviar(
        tipo: String,
        payload: JsonElement? = null,
        id: String? = null,
    ) {
        send(Frame.Text(sobre(tipo, payload, id)))
    }

    private suspend fun DefaultClientWebSocketSession.leer(): Sobre = withTimeout(4.seconds) {
        while (true) {
            val frame = incoming.receive()
            if (frame is Frame.Text) return@withTimeout JsonTruco.decodeFromString(Sobre.serializer(), frame.readText())
        }
        @Suppress("UNREACHABLE_CODE")
        error("El socket se cerró")
    }

    private suspend fun DefaultClientWebSocketSession.esperar(tipo: String): Sobre {
        repeat(60) {
            val sobre = leer()
            if (sobre.t == tipo) return sobre
        }
        error("Nunca llegó un mensaje '$tipo'")
    }

    /** Espera el `state` con `seq >= minimo` desde esta sesión. */
    private suspend fun DefaultClientWebSocketSession.vista(minimo: Int): StatePayload {
        repeat(60) {
            val sobre = leer()
            if (sobre.t == Servidor.STATE) {
                val vista = desdeJson<StatePayload>(sobre.d)
                if (vista.seq >= minimo) return vista
            }
        }
        error("Nunca llegó un state con seq >= $minimo")
    }

    private suspend fun Par.listarse() {
        sesion.enviar(Cliente.READY, comoJson(ReadyPayload(true)))
    }

    private suspend fun Par.accion(vista: StatePayload, accion: AccionDto, id: String) {
        sesion.enviar(
            Cliente.ACTION,
            comoJson(ActionPayload(stateSeq = vista.seq, accion = accion)),
            id = id,
        )
    }

    private data class Par(val sesion: DefaultClientWebSocketSession, val token: String)

    // ---------------------------------------------------------------- tests

    @Test
    fun `api info responde el estado de la sala`() = testApplication {
        application { trucoModule(ServerConfig(serverVersion = "test")) }

        val respuesta = client.get("/api/info")
        assertEquals(HttpStatusCode.OK, respuesta.status)

        val info = JsonTruco.decodeFromString(InfoPayload.serializer(), respuesta.bodyAsText())
        assertEquals(PROTOCOL_VERSION, info.protocolVersion)
        assertEquals(0, info.jugadores)
        assertEquals(2, info.maxJugadores)
        assertFalse(info.partidaEnCurso)
    }

    @Test
    fun `sin cliente compilado la raiz explica el estado`() = testApplication {
        application { trucoModule(ServerConfig(assetsDir = null, serverVersion = "test")) }

        val respuesta = client.get("/")
        assertEquals(HttpStatusCode.OK, respuesta.status)
        assertTrue(respuesta.bodyAsText().contains("Servidor de Truco"))
    }

    @Test
    fun `dos jugadores listos arrancan la partida`() = testApp { cliente ->
        val ana = cliente.entrar("Ana")
        val beto = cliente.entrar("Beto")

        ana.listarse()
        beto.listarse()

        val vista = ana.sesion.vista(1)
        assertEquals("JUEGO", vista.fase)
        assertEquals(0, vista.miJugadorId)
        assertEquals(3, vista.miMano.size)
        assertEquals(0, vista.turno)
        assertEquals(1, vista.manoNumero + 1)
        assertEquals(15, vista.targetScore)
        assertTrue("JUGAR_CARTA" in vista.legalActions)
        assertEquals(3, vista.cartasJugables.size)
    }

    @Test
    fun `nadie ve las cartas de los demas`() = testApp { cliente ->
        val ana = cliente.entrar("Ana")
        val beto = cliente.entrar("Beto")
        ana.listarse()
        beto.listarse()

        val vistaAna = ana.sesion.vista(1)
        val vistaBeto = beto.sesion.vista(1)

        assertEquals(3, vistaAna.miMano.size)
        assertEquals(3, vistaBeto.miMano.size)
        assertNull(vistaAna.manosReveladas)
        assertTrue(
            vistaAna.miMano.intersect(vistaBeto.miMano.toSet()).isEmpty(),
            "Las manos de dos jugadores no pueden compartir cartas",
        )

        val textoAna = JsonTruco.encodeToString(StatePayload.serializer(), vistaAna)
        vistaBeto.miMano.forEach { carta: CartaDto ->
            val json = JsonTruco.encodeToString(CartaDto.serializer(), carta)
            assertFalse(textoAna.contains(json), "El estado de Ana filtra una carta de Beto: $json")
        }
    }

    @Test
    fun `una mano completa se juega por websocket`() = testApp { cliente ->
        val ana = cliente.entrar("Ana")
        val beto = cliente.entrar("Beto")
        ana.listarse()
        beto.listarse()

        var observado = ana.sesion.vista(1)
        var paso = 0

        while (observado.fase != "MANO_CERRADA" && paso < 20) {
            val esAna = observado.turno == 0
            val vista = if (esAna) observado else beto.sesion.vista(observado.seq)
            val carta = vista.cartasJugables.firstOrNull()
            assertNotNull(carta, "El jugador en turno debería tener cartas jugables")
            val par = if (esAna) ana else beto
            par.accion(vista, AccionDto.JugarCarta(carta!!), "carta-$paso")
            observado = ana.sesion.vista(observado.seq + 1)
            paso++
        }

        assertEquals("MANO_CERRADA", observado.fase)
        assertNotNull(observado.manoGanadaPor)
        assertEquals(1, observado.puntajes.sum(), "La mano vale 1 punto si nadie cantó")
        assertNotNull(observado.manosReveladas, "Al cerrar la mano se revelan las cartas")
        assertTrue(observado.bazas.isNotEmpty())
    }

    @Test
    fun `un estado viejo se rechaza y se reenvia el actual`() = testApp { cliente ->
        val ana = cliente.entrar("Ana")
        val beto = cliente.entrar("Beto")
        ana.listarse()
        beto.listarse()
        val vista = ana.sesion.vista(1)

        ana.sesion.enviar(
            Cliente.ACTION,
            comoJson(ActionPayload(stateSeq = 999, accion = AccionDto.IrseAlMazo)),
            id = "viejo",
        )

        val error = desdeJson<ErrorPayload>(ana.sesion.esperar(Servidor.ERROR).d)
        assertEquals(Errores.STALE_STATE, error.code)

        val reconstruido = ana.sesion.vista(vista.seq)
        assertEquals(vista.seq, reconstruido.seq, "El servidor debe reenviar el estado actual")
    }

    @Test
    fun `una accion fuera de turno se rechaza`() = testApp { cliente ->
        val ana = cliente.entrar("Ana")
        val beto = cliente.entrar("Beto")
        ana.listarse()
        beto.listarse()
        val vista = ana.sesion.vista(1)

        // Beto intenta jugar en el turno de Ana.
        beto.sesion.enviar(
            Cliente.ACTION,
            comoJson(ActionPayload(vista.seq, AccionDto.IrseAlMazo)),
            id = "colado",
        )

        val error = desdeJson<ErrorPayload>(beto.sesion.esperar(Servidor.ERROR).d)
        assertEquals(Errores.NO_ES_TU_TURNO, error.code)
    }

    @Test
    fun `el tiempo del canto se resuelve con no quiero`() = testApp { cliente ->
        val ana = cliente.entrar("Ana")
        val beto = cliente.entrar("Beto")
        ana.listarse()
        beto.listarse()
        var vista = ana.sesion.vista(1)

        ana.accion(vista, AccionDto.Cantar("TRUCO"), "truco-1")
        vista = ana.sesion.vista(vista.seq + 1)
        assertEquals("TRUCO", vista.cantoPendiente?.canto)
        assertEquals(1, vista.cantoPendiente?.respondedor)

        // Nadie responde: a los 250 ms el servidor dice "no quiero" por Beto.
        vista = ana.sesion.vista(vista.seq + 1)
        assertNull(vista.cantoPendiente)
        assertEquals("MANO_CERRADA", vista.fase)
        assertEquals(listOf(1, 0), vista.puntajes)
    }

    @Test
    fun `el que se desconecta puede volver con su token y recupera su mano`() = testApp { cliente ->
        val ana = cliente.entrar("Ana")
        val beto = cliente.entrar("Beto")
        ana.listarse()
        beto.listarse()
        val antes = beto.sesion.vista(1)

        beto.sesion.cancel(CancellationException("se cortó la red"))

        val reconectado = cliente.webSocketSession("/ws")
        reconectado.enviar(Cliente.REJOIN, comoJson(RejoinPayload(beto.token)))
        val welcome = desdeJson<WelcomePayload>(reconectado.esperar(Servidor.WELCOME).d)
        assertEquals(1, welcome.tuJugadorId)

        val despues = reconectado.vista(antes.seq)
        assertEquals(1, despues.miJugadorId)
        assertEquals(antes.miMano, despues.miMano, "Debe recuperar exactamente su mano")
        assertTrue(despues.asientos[1].conectado)
    }

    @Test
    fun `la sala llena rechaza al que sobra`() = testApp { cliente ->
        cliente.entrar("Ana")
        cliente.entrar("Beto")

        val tercero = cliente.webSocketSession("/ws")
        tercero.enviar(Cliente.JOIN, comoJson(JoinPayload("Caro")))

        val error = desdeJson<ErrorPayload>(tercero.esperar(Servidor.ERROR).d)
        assertEquals(Errores.SALA_LLENA, error.code)
    }

    @Test
    fun `una version de protocolo distinta es fatal`() = testApp { cliente ->
        val sesion = cliente.webSocketSession("/ws")
        sesion.enviar(Cliente.HELLO, comoJson(HelloPayload("Ana", protocolVersion = 99)))

        val error = desdeJson<ErrorPayload>(sesion.esperar(Servidor.ERROR).d)
        assertEquals(Errores.PROTOCOL_VERSION, error.code)
        assertTrue(error.fatal)
    }

    @Test
    fun `un token desconocido no deja entrar`() = testApp { cliente ->
        val sesion = cliente.webSocketSession("/ws")
        sesion.enviar(Cliente.REJOIN, comoJson(RejoinPayload("token-inventado")))

        val error = desdeJson<ErrorPayload>(sesion.esperar(Servidor.ERROR).d)
        assertEquals(Errores.TOKEN_INVALIDO, error.code)
    }

    @Test
    fun `cuatro jugadores juegan en dos equipos alternados`() = testApp(
        configPrueba.copy(rules = RulesConfig(jugadores = 4, timeoutCantoMs = 250, timeoutTurnoMs = 300)),
    ) { cliente ->
        val jugadores = listOf("Ana", "Beto", "Caro", "Dani").map { cliente.entrar(it) }
        jugadores.forEach { it.listarse() }

        val vista = jugadores[0].sesion.vista(1)
        assertEquals(listOf(0, 1, 0, 1), vista.asientos.map { it.equipo })
        assertEquals(4, vista.asientos.size)
        assertEquals(3, vista.miMano.size)
        assertTrue(vista.asientos[0].esMano)
        assertTrue(vista.asientos[0].esHost)

        // Envido por equipo: el que responde en segundo lugar debe ser del equipo rival.
        jugadores[1].accion(vista, AccionDto.Cantar("ENVIDO"), "envido-4p")
        val conEnvido = jugadores[1].sesion.vista(vista.seq + 1)
        assertEquals(2, conEnvido.cantoPendiente?.respondedor)
    }

    @Test
    fun `el envido se resuelve por websocket`() = testApp { cliente ->
        val ana = cliente.entrar("Ana")
        val beto = cliente.entrar("Beto")
        ana.listarse()
        beto.listarse()
        val vista = ana.sesion.vista(1)

        ana.accion(vista, AccionDto.Cantar("ENVIDO"), "envido-1")
        val conEnvido = ana.sesion.vista(vista.seq + 1)
        assertEquals("ENVIDO", conEnvido.cantoPendiente?.canto)
        assertEquals(1, conEnvido.cantoPendiente?.respondedor)

        beto.accion(conEnvido, AccionDto.Responder("QUIERO"), "quiero-1")
        val resuelto = ana.sesion.vista(conEnvido.seq + 1)
        assertEquals(2, resuelto.puntajes.sum(), "El envido vale 2 cuando se quiere")
        assertEquals(2, resuelto.envidoValores.size)
        assertNull(resuelto.cantoPendiente)
    }

    @Test
    fun `despues del final la sala vuelve al lobby para la revancha`() = testApp(
        configPrueba.copy(
            rules = RulesConfig(jugadores = 2, targetScore = 1, timeoutCantoMs = 250, timeoutTurnoMs = 300),
            pausaFinalMs = 120,
        ),
    ) { cliente ->
        val ana = cliente.entrar("Ana")
        val beto = cliente.entrar("Beto")
        ana.listarse()
        beto.listarse()

        var vista = ana.sesion.vista(1)
        ana.accion(vista, AccionDto.IrseAlMazo, "mazo-final")
        vista = ana.sesion.vista(vista.seq + 1)
        assertEquals("FIN_PARTIDA", vista.fase)

        val lobby = desdeJson<LobbyPayload>(ana.sesion.esperar(Servidor.LOBBY).d)
        assertFalse(lobby.partidaEnCurso)
        assertTrue(lobby.asientos.none { it.listo }, "La revancha arranca sin nadie listo")
    }

    @Test
    fun `a treinta el estado muestra malas y buenas`() = testApp(
        configPrueba.copy(rules = RulesConfig(jugadores = 2, targetScore = 30, malasYBuenas = true)),
    ) { cliente ->
        val ana = cliente.entrar("Ana")
        val beto = cliente.entrar("Beto")
        ana.listarse()
        beto.listarse()

        val vista = ana.sesion.vista(1)
        assertEquals("MALAS", vista.etapa)
        assertEquals(15, vista.objetivoEtapa)
        assertEquals(30, vista.targetScore)
        assertTrue(vista.puntajesMalas.isEmpty())
        assertNull(vista.malasGanadasPor)
    }

    @Test
    fun `si alguien se va se juega por el y queda marcado como ausente`() = testApp(
        configPrueba.copy(
            rules = RulesConfig(jugadores = 2, timeoutCantoMs = 400, timeoutTurnoMs = 400),
            esperaAusenteMs = 120,
        ),
    ) { cliente ->
        val ana = cliente.entrar("Ana")
        val beto = cliente.entrar("Beto")
        ana.listarse()
        beto.listarse()

        var vista = ana.sesion.vista(1)
        beto.sesion.cancel(CancellationException("se fue la luz"))

        // El servidor juega por Beto sin esperar el timeout largo, y lo marca ausente.
        var intentos = 0
        while (!vista.asientos[1].ausente && intentos < 30) {
            vista = ana.sesion.vista(vista.seq + 1)
            intentos++
        }

        assertTrue(vista.asientos[1].ausente, "Beto debería quedar marcado como ausente")
        assertTrue(vista.seq > 2, "La mesa tuvo que avanzar sola")
    }

    @Test
    fun `despues de una mano el servidor reparte la siguiente`() = testApp { cliente ->
        val ana = cliente.entrar("Ana")
        val beto = cliente.entrar("Beto")
        ana.listarse()
        beto.listarse()
        var vista = ana.sesion.vista(1)

        ana.accion(vista, AccionDto.IrseAlMazo, "mazo-1")
        vista = ana.sesion.vista(vista.seq + 1)
        assertEquals("MANO_CERRADA", vista.fase)

        // La pausa entre manos (80 ms) dispara el reparto automático.
        vista = ana.sesion.vista(vista.seq + 1)
        assertEquals("JUEGO", vista.fase)
        assertEquals(2, vista.manoNumero + 1)
        assertEquals(3, vista.miMano.size)
        assertEquals(1, vista.manoIndex, "El mano rota al siguiente jugador")
    }
}
