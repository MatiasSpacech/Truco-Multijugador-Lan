package ar.truco.server

import ar.truco.protocol.ErrorPayload
import ar.truco.protocol.Servidor
import kotlinx.coroutines.channels.Channel
import kotlinx.serialization.json.JsonElement

/**
 * Conexión de un cliente: cola de salida propia para que el salón nunca se bloquee
 * esperando a un socket lento. La escribe el loop del WebSocket.
 */
class Sesion {
    val salida: Channel<String> = Channel(Channel.UNLIMITED)

    var token: String? = null
    var jugadorId: Int? = null

    fun enviar(texto: String) {
        salida.trySend(texto)
    }

    fun enviar(tipo: String, payload: JsonElement? = null, id: String? = null) {
        enviar(sobre(tipo, payload, id))
    }

    fun error(code: String, mensaje: String, fatal: Boolean = false) {
        enviar(Servidor.ERROR, comoJson(ErrorPayload(code, mensaje, fatal)))
    }

    fun cerrar() {
        salida.close()
    }
}
