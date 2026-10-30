package ar.truco.engine

import ar.truco.engine.Resultado.Ilegal
import ar.truco.engine.Resultado.Ok
import kotlin.random.Random

/**
 * Motor de reglas del Truco Argentino.
 *
 * Funciones puras: no mutan el estado de entrada, no leen reloj ni azar global.
 * El azar entra por [nuevaPartida] (semilla) y el tiempo por el servidor (timeouts).
 * Ver docs/01-REGLAS.md.
 */
object Motor {

    // ---------------------------------------------------------------- creación

    fun nuevaPartida(config: RulesConfig, nombres: List<String>, seed: Long): Resultado<Estado> {
        if (nombres.size != config.jugadores) {
            return Ilegal("Se esperaban ${config.jugadores} jugadores y llegaron ${nombres.size}")
        }
        if (nombres.any { it.isBlank() }) return Ilegal("Los nombres no pueden estar vacíos")
        val asientos = nombres.mapIndexed { i, nombre ->
            Asiento(id = i, nombre = nombre.trim().take(24), equipo = i % 2)
        }
        return Ok(Estado(config = config, asientos = asientos, seed = seed, turno = 0))
    }

    // ---------------------------------------------------------------- reparto

    fun repartir(estado: Estado): Resultado<Estado> {
        if (estado.fase == Fase.FIN_PARTIDA) return Ilegal("La partida ya terminó")
        if (estado.fase != Fase.ESPERANDO && estado.fase != Fase.MANO_CERRADA) {
            return Ilegal("La mano todavía está en juego")
        }

        val n = estado.asientos.size
        val primera = estado.fase == Fase.ESPERANDO
        val manoNumero = if (primera) 0 else estado.manoNumero + 1
        val manoIndex = if (primera) estado.manoIndex else (estado.manoIndex + 1) % n

        val mazo = Cartas.mezclar(Cartas.mazoCompleto(), Random(estado.seed * 31 + manoNumero))
        val manos = List(n) { i -> mazo.subList(i * 3, i * 3 + 3).toList() }

        val hayFlor = estado.config.flor != FlorMode.SIN_FLOR && manos.any { Envido.tieneFlor(it) }
        val turno = if (hayFlor) manos.indices.first { Envido.tieneFlor(manos[it]) } else manoIndex

        val evento = Evento.Reparto(manoNumero, manoIndex)
        val nuevo = estado.copy(
            fase = if (hayFlor) Fase.FLOR else Fase.JUEGO,
            manoNumero = manoNumero,
            manoIndex = manoIndex,
            manos = manos,
            manosRepartidas = manos,
            bazas = emptyList(),
            turno = turno,
            valorMano = 1,
            manoGanadaPor = null,
            negociacion = null,
            envidoCerrado = !estado.config.envidoHabilitado,
            florDeclaraciones = emptyList(),
            envidoValores = emptyList(),
            eventos = estado.eventos + evento,
        )
        return Ok(nuevo, listOf(evento))
    }

    // ---------------------------------------------------------------- acciones

    fun aplicar(estado: Estado, jugador: Int, accion: Accion): Resultado<Estado> {
        if (jugador !in estado.asientos.indices) return Ilegal("Jugador inexistente: $jugador")
        if (estado.fase == Fase.FIN_PARTIDA) return Ilegal("La partida ya terminó")

        val neg = estado.negociacion
        if (neg != null) return conNegociacionPendiente(estado, jugador, neg, accion)

        return when (estado.fase) {
            Fase.FLOR -> when (accion) {
                is Accion.Cantar ->
                    if (accion.canto == Canto.FLOR) declararFlor(estado, jugador)
                    else Ilegal("En la flor solo se canta flor")
                else -> Ilegal("Hay una flor pendiente")
            }

            Fase.JUEGO -> when (accion) {
                is Accion.JugarCarta -> jugarCarta(estado, jugador, accion.carta)
                is Accion.Cantar -> cantar(estado, jugador, accion.canto)
                Accion.IrseAlMazo -> irseAlMazo(estado, jugador)
                is Accion.Responder -> Ilegal("No hay nada para responder")
            }

            else -> Ilegal("La mano no está en juego")
        }
    }

    private fun conNegociacionPendiente(
        estado: Estado,
        jugador: Int,
        neg: Negociacion,
        accion: Accion,
    ): Resultado<Estado> = when (accion) {
        is Accion.Responder ->
            if (jugador != neg.respondedor) Ilegal("No te toca responder")
            else responder(estado, jugador, neg, accion.respuesta)

        is Accion.Cantar -> escalar(estado, jugador, neg, accion.canto)

        else -> Ilegal("Hay un canto pendiente de respuesta")
    }

    // ---------------------------------------------------------------- juego de cartas

    private fun jugarCarta(estado: Estado, jugador: Int, carta: Carta): Resultado<Estado> {
        if (estado.fase != Fase.JUEGO) return Ilegal("La mano no está en juego")
        if (jugador != estado.turno) return Ilegal("No es tu turno")
        if (carta !in estado.manos[jugador]) return Ilegal("Esa carta no está en tu mano")

        val n = estado.asientos.size
        val bazas = estado.bazas.toMutableList()
        val ultima = bazas.lastOrNull()
        val enCurso = ultima != null && ultima.jugadas.size < n
        if (!enCurso) bazas.add(Baza(numero = bazas.size + 1))

        val idx = bazas.lastIndex
        bazas[idx] = bazas[idx].copy(jugadas = bazas[idx].jugadas + Jugada(jugador, carta))

        val manos = estado.manos.mapIndexed { i, cartas -> if (i == jugador) cartas - carta else cartas }
        val evento = Evento.CartaJugada(jugador, carta, bazas[idx].numero)

        val nuevo = estado.copy(
            manos = manos,
            bazas = bazas,
            turno = (jugador + 1) % n,
            eventos = estado.eventos + evento,
        )
        return if (bazas[idx].jugadas.size == n) resolverBaza(nuevo, idx, listOf(evento))
        else Ok(nuevo, listOf(evento))
    }

    private fun resolverBaza(estado: Estado, idx: Int, previos: List<Evento>): Resultado<Estado> {
        val baza = estado.bazas[idx]
        val maximo = baza.jugadas.maxOf { Cartas.poder(it.carta) }
        val candidatos = baza.jugadas.filter { Cartas.poder(it.carta) == maximo }
        val lider = equipoLider(estado, idx)
        val ganador =
            if (candidatos.size == 1) candidatos.first().jugador
            else desempatarParda(estado, candidatos, lider)

        val resuelta = baza.copy(ganador = ganador, parda = candidatos.size > 1)
        val bazas = estado.bazas.toMutableList().also { it[idx] = resuelta }

        val evento = Evento.BazaResuelta(resuelta.numero, ganador, resuelta.parda)
        val nuevos = previos + evento

        val nuevo = estado.copy(
            bazas = bazas,
            turno = ganador,
            envidoCerrado = estado.envidoCerrado || idx == 0,
            eventos = estado.eventos + evento,
        )

        val ganadas = (0 until estado.equipos).map { eq ->
            bazas.count { it.ganador != null && estado.equipoDe(it.ganador) == eq }
        }
        val campeon = (0 until estado.equipos).firstOrNull { ganadas[it] == 2 }
        if (campeon != null) return cerrarMano(nuevo, campeon, estado.valorMano, nuevos)

        if (bazas.size == 3) {
            val maximoGanadas = ganadas.max()
            val empatados = (0 until estado.equipos).filter { ganadas[it] == maximoGanadas }
            val equipoGanador =
                if (empatados.size == 1) empatados.first()
                else estado.equipoDe(estado.manoIndex)
            return cerrarMano(nuevo, equipoGanador, estado.valorMano, nuevos)
        }
        return Ok(nuevo, nuevos)
    }

    /** Equipo que viene ganando la mano; en empate, el equipo del mano. */
    private fun equipoLider(estado: Estado, hastaBazaExclusive: Int): Int {
        val previas = estado.bazas.take(hastaBazaExclusive)
        val ganadas = (0 until estado.equipos).map { eq ->
            previas.count { it.ganador != null && estado.equipoDe(it.ganador) == eq }
        }
        val maximo = ganadas.max()
        val lideres = (0 until estado.equipos).filter { ganadas[it] == maximo }
        return if (lideres.size == 1) lideres.first() else estado.equipoDe(estado.manoIndex)
    }

    /** Parda: gana el que viene ganando la mano; si no hay, el equipo del mano. */
    private fun desempatarParda(estado: Estado, candidatos: List<Jugada>, equipoLider: Int): Int {
        val porLider = candidatos.firstOrNull { estado.equipoDe(it.jugador) == equipoLider }
        if (porLider != null) return porLider.jugador

        val equipoMano = estado.equipoDe(estado.manoIndex)
        val porMano = candidatos.firstOrNull { estado.equipoDe(it.jugador) == equipoMano }
        if (porMano != null) return porMano.jugador

        val n = estado.asientos.size
        val ordenMano = (0 until n).map { (estado.manoIndex + it) % n }
        return candidatos.minBy { ordenMano.indexOf(it.jugador) }.jugador
    }

    // ---------------------------------------------------------------- cantos

    private fun cantar(estado: Estado, jugador: Int, canto: Canto): Resultado<Estado> {
        if (Cantos.esTruco(canto)) return cantarTruco(estado, jugador, canto)
        if (Cantos.esEnvido(canto)) return cantarEnvido(estado, jugador, canto)
        return Ilegal("Ese canto no se puede hacer ahora")
    }

    private fun cantarTruco(estado: Estado, jugador: Int, canto: Canto): Resultado<Estado> {
        val disponible = Cantos.trucoDisponible(estado.valorMano)
            ?: return Ilegal("La mano ya está en vale cuatro")
        if (canto != disponible) return Ilegal("Todavía no se puede cantar $canto")
        if (!estado.config.trucoAntesDeEnvido && envidoDisponible(estado)) {
            return Ilegal("Primero se canta el envido")
        }
        return abrirNegociacion(estado, TipoNegociacion.TRUCO, jugador, canto)
    }

    private fun cantarEnvido(estado: Estado, jugador: Int, canto: Canto): Resultado<Estado> {
        if (!estado.config.envidoHabilitado) return Ilegal("El envido no está habilitado en esta partida")
        if (!envidoDisponible(estado)) return Ilegal("Ya no se puede cantar el envido")
        if (!Cantos.puedeEscalarEnvido(emptyList(), canto, estado.config)) {
            return Ilegal("Ese canto no está habilitado")
        }
        return abrirNegociacion(estado, TipoNegociacion.ENVIDO, jugador, canto)
    }

    private fun abrirNegociacion(
        estado: Estado,
        tipo: TipoNegociacion,
        jugador: Int,
        canto: Canto,
    ): Resultado<Estado> {
        val neg = Negociacion(
            tipo = tipo,
            declaraciones = listOf(Declaracion(canto, jugador)),
            respondedor = siguienteRival(estado, jugador),
        )
        val evento = Evento.CantoHecho(jugador, canto)
        val nuevo = estado.copy(negociacion = neg, eventos = estado.eventos + evento)
        return Ok(nuevo, listOf(evento))
    }

    private fun escalar(estado: Estado, jugador: Int, neg: Negociacion, canto: Canto): Resultado<Estado> {
        if (jugador != neg.respondedor) return Ilegal("No te toca responder")
        return when (neg.tipo) {
            TipoNegociacion.ENVIDO -> {
                val previas = neg.declaraciones.map { it.canto }
                if (!Cantos.puedeEscalarEnvido(previas, canto, estado.config)) {
                    return Ilegal("No se puede escalar a $canto")
                }
                agregarDeclaracion(estado, neg, jugador, canto)
            }

            TipoNegociacion.TRUCO -> {
                if (Cantos.siguienteTruco(neg.canto) != canto) {
                    return Ilegal("No se puede escalar a $canto")
                }
                agregarDeclaracion(estado, neg, jugador, canto)
            }
        }
    }

    private fun agregarDeclaracion(
        estado: Estado,
        neg: Negociacion,
        jugador: Int,
        canto: Canto,
    ): Resultado<Estado> {
        val nueva = neg.copy(
            declaraciones = neg.declaraciones + Declaracion(canto, jugador),
            respondedor = siguienteRival(estado, jugador),
        )
        val evento = Evento.CantoHecho(jugador, canto)
        val nuevo = estado.copy(negociacion = nueva, eventos = estado.eventos + evento)
        return Ok(nuevo, listOf(evento))
    }

    private fun responder(
        estado: Estado,
        jugador: Int,
        neg: Negociacion,
        respuesta: Respuesta,
    ): Resultado<Estado> {
        val evento = Evento.RespuestaDada(jugador, respuesta == Respuesta.QUIERO)
        val base = estado.copy(eventos = estado.eventos + evento)
        val nuevos = listOf(evento)
        return when (neg.tipo) {
            TipoNegociacion.ENVIDO -> resolverEnvido(base, neg, respuesta == Respuesta.QUIERO, nuevos)
            TipoNegociacion.TRUCO -> resolverTruco(base, neg, respuesta == Respuesta.QUIERO, nuevos)
        }
    }

    private fun resolverEnvido(
        estado: Estado,
        neg: Negociacion,
        quiero: Boolean,
        previos: List<Evento>,
    ): Resultado<Estado> {
        val falta = faltaEnvido(estado)
        val declaraciones = neg.declaraciones.map { it.canto }
        val equipoDeclarante = estado.equipoDe(neg.declarante)

        if (!quiero) {
            val puntos = Cantos.valorEnvidoRechazado(declaraciones, falta)
            val evento = Evento.EnvidoResuelto(emptyList(), equipoDeclarante, puntos, porRechazo = true)
            val conjunto = estado.copy(
                negociacion = null,
                envidoCerrado = true,
                eventos = estado.eventos + evento,
            )
            val (conPuntos, termina) = otorgarPuntos(conjunto, equipoDeclarante, puntos)
            val (final, todos) = conFinDePartida(conPuntos, termina, equipoDeclarante, previos + evento)
            return Ok(final, todos)
        }

        val valores = estado.manosRepartidas.map { Envido.valor(it) }
        val porEquipo = Envido.valorPorEquipo(estado.manosRepartidas, estado.asientos.map { it.equipo })
        val ganadorEquipo = when {
            porEquipo[0] > porEquipo[1] -> 0
            porEquipo[1] > porEquipo[0] -> 1
            estado.config.empateEnvido == EmpateEnvido.NO_MANO ->
                estado.equipoRival(estado.equipoDe(estado.manoIndex))
            else -> estado.equipoDe(estado.manoIndex)
        }
        val puntos = Cantos.valorEnvidoAceptado(declaraciones, falta)

        val evento = Evento.EnvidoResuelto(valores, ganadorEquipo, puntos, porRechazo = false)
        val conjunto = estado.copy(
            negociacion = null,
            envidoCerrado = true,
            envidoValores = valores,
            eventos = estado.eventos + evento,
        )
        val (conPuntos, termina) = otorgarPuntos(conjunto, ganadorEquipo, puntos)
        val (final, todos) = conFinDePartida(conPuntos, termina, ganadorEquipo, previos + evento)
        return Ok(final, todos)
    }

    private fun resolverTruco(
        estado: Estado,
        neg: Negociacion,
        quiero: Boolean,
        previos: List<Evento>,
    ): Resultado<Estado> {
        val canto = neg.canto
        val equipoDeclarante = estado.equipoDe(neg.declarante)

        if (quiero) {
            val valor = Cantos.valorTruco(canto)
            val evento = Evento.TrucoResuelto(canto, aceptado = true, puntos = 0, valorMano = valor)
            val nuevo = estado.copy(
                valorMano = valor,
                negociacion = null,
                envidoCerrado = true,
            )
            return Ok(nuevo.copy(eventos = nuevo.eventos + evento), previos + evento)
        }

        val puntos = Cantos.valorTrucoRechazado(canto)
        val evento = Evento.TrucoResuelto(canto, aceptado = false, puntos = puntos, valorMano = estado.valorMano)
        val conjunto = estado.copy(negociacion = null, eventos = estado.eventos + evento)
        return cerrarMano(conjunto, equipoDeclarante, puntos, previos + evento)
    }

    // ---------------------------------------------------------------- flor

    private fun declararFlor(estado: Estado, jugador: Int): Resultado<Estado> {
        if (estado.fase != Fase.FLOR) return Ilegal("No es el momento de la flor")
        if (jugador != estado.turno) return Ilegal("No es tu turno")
        if (!estado.tieneFlor(jugador)) return Ilegal("No tenés flor")
        if (estado.florDeclaraciones.any { it.jugador == jugador }) return Ilegal("Ya declaraste la flor")

        val declaraciones = estado.florDeclaraciones + Declaracion(Canto.FLOR, jugador)
        val evento = Evento.CantoHecho(jugador, Canto.FLOR)
        val n = estado.asientos.size
        val siguiente = (1..n)
            .map { (jugador + it) % n }
            .firstOrNull { j -> estado.tieneFlor(j) && declaraciones.none { it.jugador == j } }

        if (siguiente != null) {
            val nuevo = estado.copy(
                florDeclaraciones = declaraciones,
                turno = siguiente,
                eventos = estado.eventos + evento,
            )
            return Ok(nuevo, listOf(evento))
        }
        return resolverFlor(estado.copy(florDeclaraciones = declaraciones), listOf(evento))
    }

    private fun resolverFlor(estado: Estado, previos: List<Evento>): Resultado<Estado> {
        val valores = estado.florDeclaraciones.map { it.jugador to Envido.valorFlor(estado.manosRepartidas[it.jugador]) }
        val maximo = valores.maxOf { it.second }
        val n = estado.asientos.size
        val ordenMano = (0 until n).map { (estado.manoIndex + it) % n }
        val candidatos = valores.filter { it.second == maximo }.map { it.first }
        val ganadorJugador = candidatos.minBy { ordenMano.indexOf(it) }
        val equipo = estado.equipoDe(ganadorJugador)

        val puntos = 3
        val evento = Evento.FlorResuelta(equipo, puntos, valores.map { it.second })
        val conjunto = estado.copy(
            fase = Fase.JUEGO,
            turno = estado.manoIndex,
            envidoCerrado = true,
            eventos = estado.eventos + evento,
        )
        val (conPuntos, termina) = otorgarPuntos(conjunto, equipo, puntos)
        val (final, todos) = conFinDePartida(conPuntos, termina, equipo, previos + evento)
        return Ok(final, todos)
    }

    // ---------------------------------------------------------------- mazo / cierre

    private fun irseAlMazo(estado: Estado, jugador: Int): Resultado<Estado> {
        if (estado.fase != Fase.JUEGO) return Ilegal("La mano no está en juego")
        if (jugador != estado.turno) return Ilegal("No es tu turno")
        val rival = estado.equipoRival(estado.equipoDe(jugador))
        val evento = Evento.SeFueAlMazo(jugador, rival, estado.valorMano)
        val base = estado.copy(eventos = estado.eventos + evento)
        return cerrarMano(base, rival, estado.valorMano, listOf(evento))
    }

    private fun cerrarMano(
        estado: Estado,
        equipoGanador: Int,
        puntos: Int,
        previos: List<Evento>,
    ): Resultado<Estado> {
        val (conPuntos, termina) = otorgarPuntos(estado, equipoGanador, puntos)
        val evento = Evento.ManoGanada(equipoGanador, puntos, estado.manoNumero)
        val nuevo = conPuntos.copy(
            fase = if (termina) Fase.FIN_PARTIDA else Fase.MANO_CERRADA,
            manoGanadaPor = equipoGanador,
            negociacion = null,
            eventos = conPuntos.eventos + evento,
        )
        val (final, todos) = conFinDePartida(nuevo, termina, equipoGanador, previos + evento)
        return Ok(final, todos)
    }

    private fun otorgarPuntos(estado: Estado, equipo: Int, puntos: Int): Pair<Estado, Boolean> {
        val objetivo = estado.config.targetScore
        val puntajes = estado.puntajes.mapIndexed { i, p ->
            if (i == equipo) (p + puntos).coerceAtMost(objetivo) else p
        }
        val termina = puntajes[equipo] >= objetivo
        return estado.copy(puntajes = puntajes) to termina
    }

    private fun conFinDePartida(
        estado: Estado,
        termina: Boolean,
        equipoGanador: Int,
        todos: List<Evento>,
    ): Pair<Estado, List<Evento>> {
        if (!termina) return estado to todos
        val evento = Evento.PartidaGanada(equipoGanador)
        val final = estado.copy(
            fase = Fase.FIN_PARTIDA,
            negociacion = null,
            eventos = estado.eventos + evento,
        )
        return final to (todos + evento)
    }

    // ---------------------------------------------------------------- consultas

    fun envidoDisponible(estado: Estado): Boolean {
        if (!estado.config.envidoHabilitado || estado.envidoCerrado) return false
        if (estado.bazas.isEmpty()) return true
        if (!estado.config.envidoHastaSegundaCarta) return false
        return estado.bazas.size <= 1
    }

    fun faltaEnvido(estado: Estado): Int =
        (estado.config.targetScore - estado.puntajes.max()).coerceAtLeast(1)

    fun siguienteRival(estado: Estado, desde: Int): Int {
        val n = estado.asientos.size
        val equipo = estado.equipoDe(desde)
        return (1..n).map { (desde + it) % n }.first { estado.equipoDe(it) != equipo }
    }

    fun accionesLegales(estado: Estado, jugador: Int): Set<TipoAccion> {
        if (estado.fase != Fase.JUEGO && estado.fase != Fase.FLOR) return emptySet()

        val neg = estado.negociacion
        if (neg != null) {
            if (jugador != neg.respondedor) return emptySet()
            return when (neg.tipo) {
                TipoNegociacion.ENVIDO -> {
                    val previas = neg.declaraciones.map { it.canto }
                    buildSet {
                        add(TipoAccion.RESPONDER_QUIERO)
                        add(TipoAccion.RESPONDER_NO_QUIERO)
                        if (Cantos.puedeEscalarEnvido(previas, Canto.ENVIDO, estado.config)) add(TipoAccion.CANTAR_ENVIDO)
                        if (Cantos.puedeEscalarEnvido(previas, Canto.REAL_ENVIDO, estado.config)) add(TipoAccion.CANTAR_REAL_ENVIDO)
                        if (Cantos.puedeEscalarEnvido(previas, Canto.FALTA_ENVIDO, estado.config)) add(TipoAccion.CANTAR_FALTA_ENVIDO)
                    }
                }

                TipoNegociacion.TRUCO -> buildSet {
                    add(TipoAccion.RESPONDER_QUIERO)
                    add(TipoAccion.RESPONDER_NO_QUIERO)
                    when (Cantos.siguienteTruco(neg.canto)) {
                        Canto.RETRUCO -> add(TipoAccion.CANTAR_RETRUCO)
                        Canto.VALE_CUATRO -> add(TipoAccion.CANTAR_VALE_CUATRO)
                        else -> Unit
                    }
                }
            }
        }

        if (estado.fase == Fase.FLOR) {
            return if (jugador == estado.turno && estado.tieneFlor(jugador)) setOf(TipoAccion.CANTAR_FLOR)
            else emptySet()
        }

        val acciones = mutableSetOf<TipoAccion>()
        if (jugador == estado.turno) {
            acciones += TipoAccion.JUGAR_CARTA
            acciones += TipoAccion.IRSE_AL_MAZO
        }
        if (envidoDisponible(estado)) {
            if (Cantos.puedeEscalarEnvido(emptyList(), Canto.ENVIDO, estado.config)) acciones += TipoAccion.CANTAR_ENVIDO
            if (Cantos.puedeEscalarEnvido(emptyList(), Canto.REAL_ENVIDO, estado.config)) acciones += TipoAccion.CANTAR_REAL_ENVIDO
            if (Cantos.puedeEscalarEnvido(emptyList(), Canto.FALTA_ENVIDO, estado.config)) acciones += TipoAccion.CANTAR_FALTA_ENVIDO
        }
        val trucoPosible = Cantos.trucoDisponible(estado.valorMano)
        val envidoAbierto = envidoDisponible(estado)
        if (trucoPosible != null && (estado.config.trucoAntesDeEnvido || !envidoAbierto)) {
            when (trucoPosible) {
                Canto.TRUCO -> acciones += TipoAccion.CANTAR_TRUCO
                Canto.RETRUCO -> acciones += TipoAccion.CANTAR_RETRUCO
                Canto.VALE_CUATRO -> acciones += TipoAccion.CANTAR_VALE_CUATRO
                else -> Unit
            }
        }
        return acciones
    }
}
