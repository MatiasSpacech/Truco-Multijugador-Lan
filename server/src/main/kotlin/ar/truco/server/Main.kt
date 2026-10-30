package ar.truco.server

import ar.truco.engine.FlorMode
import ar.truco.engine.RulesConfig
import io.ktor.server.cio.CIO
import io.ktor.server.engine.embeddedServer
import java.io.File

/**
 * Servidor suelto para desarrollo: permite probar el cliente web desde la PC
 * antes de empaquetar todo en el APK.
 *
 * ```
 * ./gradlew :server:run     # sirve web/dist si está compilado
 * cd web && npm run build
 * ```
 *
 * Variables de entorno opcionales: `TRUCO_PUERTO` (8080), `TRUCO_JUGADORES` (4).
 * Ruta de assets: `-Dtruco.assets=<ruta>` o se busca `web/dist` / `../web/dist`.
 */
fun main() {
    val puerto = System.getenv("TRUCO_PUERTO")?.toIntOrNull() ?: 8080
    val jugadores = System.getenv("TRUCO_JUGADORES")?.toIntOrNull() ?: 4
    val objetivo = System.getenv("TRUCO_OBJETIVO")?.toIntOrNull() ?: 15
    val conFlor = System.getenv("TRUCO_FLOR")?.toBoolean() ?: false
    val assets = buscarAssets()

    val config = ServerConfig(
        rules = RulesConfig(
            jugadores = jugadores,
            targetScore = objetivo,
            flor = if (conFlor) FlorMode.FLOR else FlorMode.SIN_FLOR,
            malasYBuenas = objetivo == 30,
        ),
        assetsDir = assets,
    )

    println(
        "Truco Argentino — servidor en http://0.0.0.0:$puerto " +
            "($jugadores jugadores, a $objetivo" + (if (conFlor) ", con flor" else "") + ")",
    )
    println(
        if (assets != null) "Cliente web: ${assets.absolutePath}"
        else "Cliente web: no encontrado (compilá con `cd web && npm run build`)",
    )

    embeddedServer(CIO, port = puerto, host = "0.0.0.0") {
        trucoModule(config)
    }.start(wait = true)
}

private fun buscarAssets(): File? {
    val candidatos = listOfNotNull(
        System.getProperty("truco.assets"),
        "web/dist",
        "../web/dist",
    )
    return candidatos
        .map { File(it) }
        .firstOrNull { it.resolve("index.html").isFile }
}
