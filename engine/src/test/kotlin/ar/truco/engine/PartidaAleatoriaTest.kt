package ar.truco.engine

import ar.truco.engine.Resultado.Ok
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.random.Random

/**
 * Partidas completas jugadas al azar sobre todas las configuraciones.
 * Verifica que el motor siempre puede avanzar, que nadie se pasa del objetivo
 * y que las 40 cartas se conservan.
 */
class PartidaAleatoriaTest {

    private val configuraciones = listOf(
        RulesConfig(jugadores = 2),
        RulesConfig(jugadores = 2, targetScore = 30),
        RulesConfig(jugadores = 4),
        RulesConfig(jugadores = 6),
        RulesConfig(jugadores = 2, flor = FlorMode.FLOR),
        RulesConfig(jugadores = 4, flor = FlorMode.FLOR, targetScore = 30),
        RulesConfig(jugadores = 2, trucoAntesDeEnvido = false),
        RulesConfig(jugadores = 2, envido = false, realEnvido = false, faltaEnvido = false),
    )

    @Test
    fun `mil partidas aleatorias terminan y conservan las invariantes`() {
        configuraciones.forEachIndexed { indiceConfig, config ->
            for (semilla in 0 until 60) {
                val semillaLarga = (indiceConfig * 1000 + semilla).toLong()
                jugarPartidaAlAzar(config, semillaLarga)
            }
        }
    }

    private fun jugarPartidaAlAzar(config: RulesConfig, semilla: Long) {
        val azar = Random(semilla)
        val nombres = (0 until config.jugadores).map { "Jugador $it" }

        var estado = when (val r = Motor.nuevaPartida(config, nombres, semilla)) {
            is Ok -> r.valor
            else -> error("No se pudo crear la partida: $r")
        }
        estado = Motor.repartir(estado).ok()

        var pasos = 0
        while (estado.fase != Fase.FIN_PARTIDA && pasos < 4_000) {
            if (estado.fase == Fase.MANO_CERRADA) {
                verificarInvariantes(estado, config)
                estado = Motor.repartir(estado).ok()
                pasos++
                continue
            }

            val actor = estado.asientos.indices.firstOrNull { Motor.accionesLegales(estado, it).isNotEmpty() }
            assertNotNull(actor, "Nadie puede actuar. fase=${estado.fase} neg=${estado.negociacion}")
            val opciones = Motor.accionesLegales(estado, actor!!).sortedBy { it.name }
            val tipo = opciones[azar.nextInt(opciones.size)]
            val accion = accionPara(tipo, estado, actor, azar)

            estado = when (val r = Motor.aplicar(estado, actor, accion)) {
                is Ok -> r.valor
                is Resultado.Ilegal -> error(
                    "El motor rechazó una acción que declaró legal: $tipo → ${r.motivo} " +
                        "(fase=${estado.fase}, neg=${estado.negociacion})",
                )
            }
            pasos++
            verificarInvariantes(estado, config)
        }

        assertEquals(Fase.FIN_PARTIDA, estado.fase, "La partida no terminó en 4000 pasos (semilla $semilla)")
        assertTrue(estado.puntajes.any { it == config.targetScore })
    }

    private fun accionPara(tipo: TipoAccion, estado: Estado, actor: Int, azar: Random): Accion =
        when (tipo) {
            TipoAccion.JUGAR_CARTA -> {
                val cartas = estado.manos[actor]
                Accion.JugarCarta(cartas[azar.nextInt(cartas.size)])
            }

            TipoAccion.IRSE_AL_MAZO -> Accion.IrseAlMazo
            TipoAccion.RESPONDER_QUIERO -> Accion.Responder(Respuesta.QUIERO)
            TipoAccion.RESPONDER_NO_QUIERO -> Accion.Responder(Respuesta.NO_QUIERO)
            TipoAccion.CANTAR_ENVIDO -> Accion.Cantar(Canto.ENVIDO)
            TipoAccion.CANTAR_REAL_ENVIDO -> Accion.Cantar(Canto.REAL_ENVIDO)
            TipoAccion.CANTAR_FALTA_ENVIDO -> Accion.Cantar(Canto.FALTA_ENVIDO)
            TipoAccion.CANTAR_TRUCO -> Accion.Cantar(Canto.TRUCO)
            TipoAccion.CANTAR_RETRUCO -> Accion.Cantar(Canto.RETRUCO)
            TipoAccion.CANTAR_VALE_CUATRO -> Accion.Cantar(Canto.VALE_CUATRO)
            TipoAccion.CANTAR_FLOR -> Accion.Cantar(Canto.FLOR)
        }

    private fun verificarInvariantes(estado: Estado, config: RulesConfig) {
        assertTrue(
            estado.puntajes.all { it in 0..config.targetScore },
            "Puntajes fuera de rango: ${estado.puntajes}",
        )
        assertTrue(estado.valorMano in 1..4, "Valor de mano inválido: ${estado.valorMano}")

        val enManos = estado.manos.sumOf { it.size }
        val enMesa = estado.bazas.sumOf { b -> b.jugadas.size }
        if (estado.fase != Fase.MANO_CERRADA && estado.fase != Fase.FIN_PARTIDA) {
            assertEquals(
                config.jugadores * 3,
                enManos + enMesa,
                "Cartas perdidas o duplicadas (fase=${estado.fase})",
            )
        }
        val todas = estado.manos.flatten() + estado.bazas.flatMap { b -> b.jugadas.map { it.carta } }
        assertEquals(todas.size, todas.distinct().size, "Hay cartas duplicadas en la mesa")
    }
}
