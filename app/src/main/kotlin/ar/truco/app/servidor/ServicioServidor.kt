package ar.truco.app.servidor

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.net.wifi.WifiManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import ar.truco.app.MainActivity
import ar.truco.app.R
import ar.truco.engine.FlorMode
import ar.truco.engine.RulesConfig
import ar.truco.server.ServerConfig
import ar.truco.server.Sala
import ar.truco.server.crearSala
import ar.truco.server.trucoModule
import io.ktor.server.cio.CIO
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
/**
 * Servicio en primer plano que mantiene viva la mesa: servidor Ktor + Wi-Fi lock + wake lock.
 * Sin esto, Android suspende el proceso al apagar la pantalla y la partida se corta.
 */
class ServicioServidor : Service() {

    companion object {
        const val ACCION_INICIAR = "ar.truco.app.servidor.INICIAR"
        const val ACCION_DETENER = "ar.truco.app.servidor.DETENER"
        const val EXTRA_JUGADORES = "jugadores"
        const val EXTRA_OBJETIVO = "objetivo"
        const val EXTRA_FLOR = "flor"

        private const val CANAL = "truco_servidor"
        private const val NOTIFICACION = 42
        private const val TREINTA_MINUTOS = 30L * 60 * 1000

        fun iniciar(context: Context, jugadores: Int, objetivo: Int, flor: Boolean) {
            val intent = Intent(context, ServicioServidor::class.java).apply {
                action = ACCION_INICIAR
                putExtra(EXTRA_JUGADORES, jugadores)
                putExtra(EXTRA_OBJETIVO, objetivo)
                putExtra(EXTRA_FLOR, flor)
            }
            ContextCompat.startForegroundService(context, intent)
        }

        fun detener(context: Context) {
            context.startService(Intent(context, ServicioServidor::class.java).apply { action = ACCION_DETENER })
        }
    }

    private var servidor: EmbeddedServer<*, *>? = null
    private var sala: Sala? = null
    private var alcance: CoroutineScope? = null
    private var tareaEstado: Job? = null
    private var wifiLock: WifiManager.WifiLock? = null
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACCION_DETENER -> {
                detenerTodo()
                stopSelf()
                return START_NOT_STICKY
            }

            else -> {
                val jugadores = intent?.getIntExtra(EXTRA_JUGADORES, 4) ?: 4
                val objetivo = intent?.getIntExtra(EXTRA_OBJETIVO, 15) ?: 15
                val flor = intent?.getBooleanExtra(EXTRA_FLOR, false) ?: false
                iniciarServidor(jugadores, objetivo, flor)
            }
        }
        return START_NOT_STICKY
    }

    private fun iniciarServidor(jugadores: Int, objetivo: Int, flor: Boolean) {
        detenerServidor()

        crearCanal()
        startForeground(
            NOTIFICACION,
            notificacion(titulo = "Abriendo la mesa…", detalle = "$jugadores jugadores"),
        )

        EstadoServidor.actualizar {
            it.copy(
                fase = EstadoServidor.Fase.INICIANDO,
                error = null,
                jugadores = 0,
                maxJugadores = jugadores,
                partidaEnCurso = false,
                hotspot = null,
                hotspotError = null,
            )
        }

        val puerto = RedLocal.puertoLibre(8080)
        val config = ServerConfig(
            rules = RulesConfig(
                jugadores = jugadores,
                targetScore = objetivo,
                malasYBuenas = objetivo == 30,
                flor = if (flor) FlorMode.FLOR else FlorMode.SIN_FLOR,
            ),
            codigo = "TRUCO",
            serverVersion = "1.0.0",
            assetsDir = null,
        )

        val alcanceNuevo = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val salaNueva = crearSala(config, alcanceNuevo)

        try {
            val motor = embeddedServer(CIO, port = puerto, host = "0.0.0.0") {
                trucoModule(salaNueva, config)
            }
            motor.start(wait = false)
            servidor = motor
            sala = salaNueva
            alcance = alcanceNuevo
        } catch (e: Throwable) {
            alcanceNuevo.cancel()
            EstadoServidor.actualizar {
                it.copy(
                    fase = EstadoServidor.Fase.ERROR,
                    error = e.message ?: "No se pudo iniciar el servidor",
                )
            }
            notificacion(titulo = "No se pudo abrir la mesa", detalle = e.message ?: "Error")
            stopSelf()
            return
        }

        adquirirBloqueos()

        EstadoServidor.actualizar {
            it.copy(
                fase = EstadoServidor.Fase.ACTIVO,
                puerto = puerto,
                ip = RedLocal.ipLocal(this@ServicioServidor),
            )
        }

        tareaEstado = alcanceNuevo.launch {
            while (isActive) {
                val info = runCatching { salaNueva.info() }.getOrNull()
                if (info != null) {
                    renovarWakeLock()
                    EstadoServidor.actualizar {
                        it.copy(
                            fase = EstadoServidor.Fase.ACTIVO,
                            ip = RedLocal.ipLocal(this@ServicioServidor),
                            jugadores = info.jugadores,
                            maxJugadores = info.maxJugadores,
                            partidaEnCurso = info.partidaEnCurso,
                        )
                    }
                    actualizarNotificacion(info.jugadores, info.maxJugadores)
                }
                delay(1_500)
            }
        }
    }

    private fun detenerServidor() {
        tareaEstado?.cancel()
        tareaEstado = null

        val salaActual = sala
        sala = null
        if (salaActual != null) {
            CoroutineScope(Dispatchers.Default).launch { runCatching { salaActual.cerrar("HOST_LEFT") } }
        }

        runCatching { servidor?.stop(500, 1_500) }
        servidor = null

        alcance?.cancel()
        alcance = null
        liberarBloqueos()
    }

    private fun detenerTodo() {
        detenerServidor()
        EstadoServidor.reiniciar()
        stopForeground(STOP_FOREGROUND_REMOVE)
    }

    override fun onDestroy() {
        detenerServidor()
        if (EstadoServidor.estado.value.fase != EstadoServidor.Fase.ERROR) EstadoServidor.reiniciar()
        super.onDestroy()
    }

    // ------------------------------------------------------------ locks

    @Suppress("DEPRECATION")
    private fun adquirirBloqueos() {
        val wifi = applicationContext.getSystemService(WifiManager::class.java)
        wifiLock = wifi?.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "truco:wifi").also {
            runCatching { it?.setReferenceCounted(false); it?.acquire() }
        }

        renovarWakeLock()
    }

    /**
     * El wake lock se pide con vencimiento (30 min) y el loop de estado lo renueva: si algo se
     * cuelga, el sistema lo libera solo en vez de dejarlo para siempre.
     */
    private fun renovarWakeLock() {
        val energia = getSystemService(PowerManager::class.java) ?: return
        if (wakeLock?.isHeld == true) return
        wakeLock = energia.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "truco:servidor").also {
            runCatching { it.setReferenceCounted(false); it.acquire(TREINTA_MINUTOS) }
        }
    }

    private fun liberarBloqueos() {
        runCatching { if (wifiLock?.isHeld == true) wifiLock?.release() }
        runCatching { if (wakeLock?.isHeld == true) wakeLock?.release() }
        wifiLock = null
        wakeLock = null
    }

    // ------------------------------------------------------------ notificación

    private fun crearCanal() {
        val gestor = getSystemService(NotificationManager::class.java) ?: return
        if (gestor.getNotificationChannel(CANAL) != null) return
        gestor.createNotificationChannel(
            NotificationChannel(CANAL, "Mesa de truco", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Servidor de la partida en curso"
                setShowBadge(false)
            },
        )
    }

    private fun actualizarNotificacion(jugadores: Int, maximo: Int) {
        val ip = EstadoServidor.estado.value.ip
        val puerto = EstadoServidor.estado.value.puerto
        val detalle = if (ip != null) "http://$ip:$puerto · $jugadores/$maximo" else "$jugadores/$maximo jugadores"
        val gestor = getSystemService(NotificationManager::class.java) ?: return
        gestor.notify(NOTIFICACION, notificacion(titulo = "Mesa abierta", detalle = detalle))
    }

    private fun notificacion(titulo: String, detalle: String) =
        NotificationCompat.Builder(this, CANAL)
            .setSmallIcon(R.drawable.ic_notificacion)
            .setContentTitle(titulo)
            .setContentText(detalle)
            .setOngoing(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(
                PendingIntent.getActivity(
                    this,
                    0,
                    Intent(this, MainActivity::class.java),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                ),
            )
            .addAction(
                0,
                "Terminar",
                PendingIntent.getService(
                    this,
                    1,
                    Intent(this, ServicioServidor::class.java).apply { action = ACCION_DETENER },
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                ),
            )
            .build()
}
