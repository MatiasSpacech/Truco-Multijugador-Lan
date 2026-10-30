package ar.truco.app.servidor

import android.content.Context
import android.net.ConnectivityManager
import android.net.wifi.WifiManager
import java.net.Inet4Address
import java.net.ServerSocket

/** Cosas de red que la app necesita antes de levantar el servidor. */
object RedLocal {

    /** IPv4 de la red Wi-Fi actual (la que tienen que usar los invitados). */
    fun ipLocal(context: Context): String? {
        val administrador = context.getSystemService(ConnectivityManager::class.java) ?: return null
        val red = administrador.activeNetwork ?: return null
        val propiedades = administrador.getLinkProperties(red) ?: return null
        return propiedades.linkAddresses
            .map { it.address }
            .filterIsInstance<Inet4Address>()
            .firstOrNull { !it.isLoopbackAddress && !it.isLinkLocalAddress }
            ?.hostAddress
    }

    fun wifiEncendido(context: Context): Boolean {
        val wifi = context.applicationContext.getSystemService(WifiManager::class.java) ?: return false
        return wifi.isWifiEnabled
    }

    /** Puerto libre a partir de [preferido] (8080 suele estar libre en el teléfono). */
    fun puertoLibre(preferido: Int, intentos: Int = 20): Int {
        for (puerto in preferido until preferido + intentos) {
            try {
                ServerSocket(puerto).use { return puerto }
            } catch (_: Exception) {
                // ocupado: probamos el siguiente
            }
        }
        return preferido
    }
}
