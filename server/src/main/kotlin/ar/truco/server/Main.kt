package ar.truco.server

import ar.truco.engine.RulesConfig
import io.ktor.server.cio.CIO
import io.ktor.server.engine.embeddedServer
import java.io.File

/**
 * Servidor suelto para desarrollo: permite probar el cliente web desde la PC
 * antes de empaquetar todo en el APK.
 *
 * ```
 * ./gradlew :server:run
 * ```
 *
 * Variables de entorno opcionales: `TRUCO_PUERTO` (8080), `TRUCO_JUGADORES` (4).
 * El cliente web se sirve desde `web/dist` o desde `-Dtruco.assets=<ruta>`.
 */
fun main() {
    val puerto = System.getenv("TRUCO_PUERTO")?.toIntOrNull() ?: 8080
    val jugadores = System.getenv("TRUCO_JUGADORES")?.toIntOrNull() ?: 4
    val assets = File(System.getProperty("truco.assets") ?: "web/dist")

    val config = ServerConfig(
        rules = RulesConfig(jugadores = jugadores, targetScore = 15),
        assetsDir = assets,
    )

    println("Truco Argentino — servidor en http://0.0.0.0:$puerto (${jugadores} jugadores)")
    println("Assets: ${assets.absolutePath} (existe: ${assets.isDirectory})")

    embeddedServer(CIO, port = puerto, host = "0.0.0.0") {
        trucoModule(config)
    }.start(wait = true)
}
