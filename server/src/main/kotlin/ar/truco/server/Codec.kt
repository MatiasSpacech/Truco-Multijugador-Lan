package ar.truco.server

import ar.truco.protocol.Sobre
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement

val JsonTruco: Json = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
    classDiscriminator = "tipo"
    explicitNulls = false
}

/** Serializa un mensaje con el envelope del protocolo. */
fun sobre(t: String, payload: JsonElement? = null, id: String? = null): String =
    JsonTruco.encodeToString(Sobre.serializer(), Sobre(t = t, id = id, d = payload ?: JsonObject(emptyMap())))

inline fun <reified T> comoJson(valor: T): JsonElement = JsonTruco.encodeToJsonElement(valor)

inline fun <reified T> desdeJson(elemento: JsonElement): T = JsonTruco.decodeFromJsonElement(elemento)
