package ar.truco.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import ar.truco.app.servidor.EstadoServidor
import ar.truco.app.servidor.HotspotLocal
import ar.truco.app.servidor.ServicioServidor
import ar.truco.app.ui.ConfigMesa
import ar.truco.app.ui.PantallaLobby
import ar.truco.app.ui.PantallaMesa
import ar.truco.app.ui.PantallaServidor

private enum class Pantalla { LOBBY, SERVIDOR, MESA }

@Composable
fun AppTruco() {
    val contexto = LocalContext.current
    val estado by EstadoServidor.estado.collectAsState()

    var jugadores by rememberSaveable { mutableStateOf(4) }
    var objetivo by rememberSaveable { mutableStateOf(15) }
    var flor by rememberSaveable { mutableStateOf(false) }
    var pantalla by rememberSaveable {
        mutableStateOf(
            if (EstadoServidor.estado.value.fase == EstadoServidor.Fase.ACTIVO) Pantalla.SERVIDOR
            else Pantalla.LOBBY,
        )
    }

    val hotspot = remember { HotspotLocal(contexto) }
    val config = ConfigMesa(jugadores, objetivo, flor)

    val pedirNotificaciones = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { /* si lo niega, la mesa igual funciona: solo no se ve la notificación */ }

    val pedirHotspot = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { concedido ->
        if (concedido) crearHotspot(hotspot) else {
            EstadoServidor.actualizar {
                it.copy(hotspotError = "Sin permiso de Wi-Fi cercano no se puede crear la zona")
            }
        }
    }

    // Si el servicio se detiene (o falla), volvemos al lobby.
    LaunchedEffect(estado.fase) {
        if (estado.fase == EstadoServidor.Fase.DETENIDO && pantalla == Pantalla.SERVIDOR) {
            pantalla = Pantalla.LOBBY
        }
    }

    when (pantalla) {
        Pantalla.LOBBY -> PantallaLobby(
            config = config,
            alCambiar = { nueva ->
                jugadores = nueva.jugadores
                objetivo = nueva.objetivo
                flor = nueva.flor
            },
            alCrear = {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                    ContextCompat.checkSelfPermission(contexto, Manifest.permission.POST_NOTIFICATIONS) !=
                    PackageManager.PERMISSION_GRANTED
                ) {
                    pedirNotificaciones.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
                ServicioServidor.iniciar(contexto, jugadores, objetivo, flor)
                pantalla = Pantalla.SERVIDOR
            },
            error = estado.error,
        )

        Pantalla.SERVIDOR -> {
            val url = estado.url
            PantallaServidor(
                estado = estado,
                alAbrirMesa = { if (url != null) pantalla = Pantalla.MESA },
                alTerminar = {
                    ServicioServidor.detener(contexto)
                    hotelCerrar(hotspot)
                    pantalla = Pantalla.LOBBY
                },
                alCrearHotspot = {
                    val permiso = permisoHotspot()
                    val concedido = permiso == null ||
                        ContextCompat.checkSelfPermission(contexto, permiso) == PackageManager.PERMISSION_GRANTED
                    if (permiso != null && !concedido) pedirHotspot.launch(permiso) else crearHotspot(hotspot)
                },
                alDetenerHotspot = {
                    hotelCerrar(hotspot)
                    EstadoServidor.actualizar { it.copy(hotspot = null, hotspotError = null) }
                },
            )
        }

        Pantalla.MESA -> {
            val url = estado.url
            if (url == null) {
                pantalla = Pantalla.SERVIDOR
            } else {
                PantallaMesa(url = url, alVolver = { pantalla = Pantalla.SERVIDOR })
            }
        }
    }
}

private fun permisoHotspot(): String? = when {
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> Manifest.permission.NEARBY_WIFI_DEVICES
    else -> Manifest.permission.ACCESS_FINE_LOCATION
}

private fun crearHotspot(hotspot: HotspotLocal) {
    EstadoServidor.actualizar { it.copy(hotspotError = null) }
    hotspot.crear { resultado ->
        resultado.fold(
            onSuccess = { datos ->
                EstadoServidor.actualizar { it.copy(hotspot = datos, hotspotError = null) }
            },
            onFailure = { error ->
                EstadoServidor.actualizar {
                    it.copy(hotspotError = error.message ?: "No se pudo crear la zona Wi-Fi")
                }
            },
        )
    }
}

private fun hotelCerrar(hotspot: HotspotLocal) {
    hotspot.detener()
    EstadoServidor.actualizar { it.copy(hotspot = null) }
}
