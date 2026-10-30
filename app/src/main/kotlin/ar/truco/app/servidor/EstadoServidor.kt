package ar.truco.app.servidor

import android.content.Context
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Handler
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** Estado que la UI observa y el servicio actualiza. */
object EstadoServidor {

    enum class Fase { DETENIDO, INICIANDO, ACTIVO, ERROR }

    data class DatosHotspot(val ssid: String, val clave: String)

    data class Snapshot(
        val fase: Fase = Fase.DETENIDO,
        val puerto: Int = 0,
        val ip: String? = null,
        val jugadores: Int = 0,
        val maxJugadores: Int = 4,
        val partidaEnCurso: Boolean = false,
        val error: String? = null,
        val hotspot: DatosHotspot? = null,
        val hotspotError: String? = null,
    ) {
        val url: String? get() = ip?.let { "http://$it:$puerto" }
    }

    private val _estado = MutableStateFlow(Snapshot())
    val estado: StateFlow<Snapshot> = _estado.asStateFlow()

    fun actualizar(transform: (Snapshot) -> Snapshot) {
        _estado.update(transform)
    }

    fun reiniciar() {
        _estado.value = Snapshot()
    }
}

/**
 * Punto de acceso Wi-Fi propio del teléfono, como alternativa al router de la casa.
 *
 * Es *best effort*: depende del fabricante y del permiso `NEARBY_WIFI_DEVICES`
 * (Android 13+). Si falla, la app sigue funcionando con la red Wi-Fi normal.
 */
class HotspotLocal(private val context: Context) {

    private var reserva: WifiManager.LocalOnlyHotspotReservation? = null

    fun crear(alTerminar: (Result<EstadoServidor.DatosHotspot>) -> Unit) {
        val wifi = context.applicationContext.getSystemService(WifiManager::class.java)
        if (wifi == null) {
            alTerminar(Result.failure(IllegalStateException("Este teléfono no expone Wi-Fi")))
            return
        }

        try {
            wifi.startLocalOnlyHotspot(
                object : WifiManager.LocalOnlyHotspotCallback() {
                    override fun onStarted(nueva: WifiManager.LocalOnlyHotspotReservation) {
                        reserva = nueva
                        alTerminar(Result.success(datos(nueva)))
                    }

                    override fun onStopped() {
                        reserva = null
                    }

                    override fun onFailed(reason: Int) {
                        alTerminar(Result.failure(IllegalStateException(motivo(reason))))
                    }
                },
                Handler(context.mainLooper),
            )
        } catch (e: SecurityException) {
            alTerminar(Result.failure(IllegalStateException("Falta el permiso de Wi-Fi cercano")))
        } catch (e: Throwable) {
            alTerminar(Result.failure(IllegalStateException(e.message ?: "No se pudo crear el hotspot")))
        }
    }

    fun detener() {
        runCatching { reserva?.close() }
        reserva = null
    }

    @Suppress("DEPRECATION")
    private fun datos(reserva: WifiManager.LocalOnlyHotspotReservation): EstadoServidor.DatosHotspot {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val config = reserva.softApConfiguration
            return EstadoServidor.DatosHotspot(
                ssid = config.ssid ?: "Truco",
                clave = config.passphrase ?: "",
            )
        }
        @Suppress("DEPRECATION")
        val config = reserva.wifiConfiguration
        @Suppress("DEPRECATION")
        return EstadoServidor.DatosHotspot(
            ssid = config?.SSID ?: "Truco",
            clave = config?.preSharedKey ?: "",
        )
    }

    private fun motivo(reason: Int): String = when (reason) {
        WifiManager.LocalOnlyHotspotCallback.ERROR_NO_CHANNEL -> "No hay canal Wi-Fi disponible"
        WifiManager.LocalOnlyHotspotCallback.ERROR_INCOMPATIBLE_MODE -> "El Wi-Fi está en un modo incompatible"
        WifiManager.LocalOnlyHotspotCallback.ERROR_TETHERING_DISALLOWED -> "El operador no permite compartir red"
        else -> "No se pudo crear el hotspot (código $reason)"
    }
}
