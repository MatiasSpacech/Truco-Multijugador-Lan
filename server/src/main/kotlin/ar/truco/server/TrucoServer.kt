package ar.truco.server

import ar.truco.engine.RulesConfig
import ar.truco.protocol.InfoPayload
import io.ktor.http.ContentType
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationStopped
import io.ktor.server.application.install
import io.ktor.server.http.content.staticFiles
import io.ktor.server.http.content.staticResources
import io.ktor.server.response.respondText
import io.ktor.server.routing.Routing
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.server.websocket.webSocket
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.io.File

/** Configuración del proceso servidor (la arma la app anfitriona o `Main.kt` en la PC). */
data class ServerConfig(
    val rules: RulesConfig = RulesConfig(),
    val codigo: String = "TRUCO",
    val serverVersion: String = "0.4.0",
    val assetsDir: File? = null,
    val pausaEntreManosMs: Long = 4_000,
    val pausaFinalMs: Long = 20_000,
)

/**
 * Crea la sala. En Android el scope lo maneja el servicio en primer plano
 * (así sobrevive a la Activity); en la PC lo maneja [trucoModule].
 */
fun crearSala(config: ServerConfig, scope: CoroutineScope): Sala = Sala(
    config = config.rules,
    codigo = config.codigo,
    serverVersion = config.serverVersion,
    scope = scope,
    pausaEntreManosMs = config.pausaEntreManosMs,
    pausaFinalMs = config.pausaFinalMs,
)

/**
 * Módulo Ktor con todo lo que expone el anfitrión:
 * `/api/info` (diagnóstico), `/ws` (juego) y el cliente web estático.
 *
 * Uso en la PC (crea su propio scope y lo cancela al detenerse):
 * ```
 * embeddedServer(CIO, port = 8080) { trucoModule(ServerConfig()) }
 * ```
 */
fun Application.trucoModule(config: ServerConfig = ServerConfig()): Sala {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    monitor.subscribe(ApplicationStopped) { scope.cancel("servidor detenido") }
    val sala = crearSala(config, scope)
    trucoModule(sala, config)
    return sala
}

/** Instala las rutas sobre una sala ya creada (Android: la crea el servicio). */
fun Application.trucoModule(sala: Sala, config: ServerConfig) {
    install(io.ktor.server.websocket.WebSockets)
    routing {
        rutasApi(sala)
        rutasWebSocket(sala)
        montarWeb(config)
    }
}

private fun Routing.rutasApi(sala: Sala) {
    get("/api/info") {
        val info: InfoPayload = sala.info()
        call.respondText(JsonTruco.encodeToString(InfoPayload.serializer(), info), ContentType.Application.Json)
    }
}

private fun Routing.rutasWebSocket(sala: Sala) {
    webSocket("/ws") {
        val sesion = Sesion()
        sala.conectar(sesion)

        val escritor = launch {
            for (texto in sesion.salida) send(Frame.Text(texto))
        }

        try {
            for (frame in incoming) {
                if (frame is Frame.Text) sala.procesar(sesion, frame.readText())
            }
        } finally {
            escritor.cancel()
            sesion.cerrar()
            sala.desconectar(sesion)
        }
    }
}

private fun Routing.montarWeb(config: ServerConfig) {
    val dir = config.assetsDir
    val hayRecursos = Sala::class.java.classLoader.getResource("web/index.html") != null

    if (dir != null && dir.resolve("index.html").isFile) {
        staticFiles("/", dir)
    } else if (hayRecursos) {
        staticResources("/", "web")
    } else {
        get("/") { call.respondText(SIN_CLIENTE_HTML, ContentType.Text.Html) }
    }
}

private val SIN_CLIENTE_HTML = """
<!doctype html>
<html lang="es">
<head>
  <meta charset="utf-8">
  <meta name="viewport" content="width=device-width, initial-scale=1">
  <title>Truco — servidor arriba</title>
  <style>
    body { font-family: system-ui, sans-serif; background: #1b3a2f; color: #f4ecd8;
           display: grid; place-items: center; min-height: 100vh; margin: 0; text-align: center; }
    code { background: rgba(0,0,0,.3); padding: .2rem .4rem; border-radius: .3rem; }
  </style>
</head>
<body>
  <div>
    <h1>Servidor de Truco funcionando</h1>
    <p>El cliente web todavía no está compilado en <code>assets/web</code>.</p>
    <p>Diagnóstico: <a href="/api/info" style="color:#9fd3b8">/api/info</a></p>
  </div>
</body>
</html>
""".trimIndent()
