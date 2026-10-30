# 04 — Protocolo de red (WebSocket, JSON)

## 1. Generalidades

- Endpoint: `ws://<host>:<puerto>/ws`. Protocolo: `truco.v1`.
- Transporte: JSON UTF-8, un mensaje por frame de texto.
- Envelope común:
  ```json
  { "t": "tipo", "id": "uuid-opcional", "v": 1, "d": { } }
  ```
  `t` = tipo, `id` = correlación (acciones del cliente), `d` = payload.
- El servidor es el único que emite estado. El cliente nunca calcula reglas: pide y muestra.
- Todas las acciones requieren `stateSeq` (el `seq` del último estado conocido). Si no coincide,
  el servidor responde `error{code:"STALE_STATE"}` + reenvía estado (el cliente se resincroniza solo).

## 2. Cliente → Servidor

| `t` | `d` | Notas |
|-----|-----|-------|
| `hello` | `{ nombre, protocolVersion }` | Primer mensaje. Rechaza si la versión no coincide |
| `join` | `{ nombre }` | Toma una silla libre; devuelve `playerToken` |
| `rejoin` | `{ playerToken }` | Recupera silla tras desconexión |
| `ready` | `{ listo: bool }` | Listo para empezar |
| `action` | `{ stateSeq, accion }` + `id` en el envelope | Acción de juego (ver §4); el `id` da idempotencia |
| `ping` | `{ ts }` | Latido cada 10 s (keep-alive + detección de red muerta) |
| `leave` | `{}` | Abandona la partida/sala |

## 3. Servidor → Cliente

| `t` | `d` | Notas |
|-----|-----|-------|
| `welcome` | `{ protocolVersion, serverVersion, tuJugadorId, playerToken }` | Respuesta a `hello`/`join`/`rejoin` |
| `lobby` | `{ asientos: [{ id, nombre, equipo, conectado, listo, esHost }], config, codigo }` | Estado de la sala |
| `state` | `{ seq, fase, ... vista privada ... }` | Estado completo filtrado para este jugador |
| `events` | `{ seq, eventos: [{ tipo, ... }] }` | Para animaciones/sonido/log (no es la fuente de verdad) |
| `error` | `{ code, mensaje, fatal }` | Ver §6 |
| `pong` | `{ ts }` | Respuesta a `ping` |
| `ended` | `{ motivo }` | Partida o sala cerrada (`HOST_LEFT`, `FIN`, `KICKED`) |

### Vista privada de `state` (implementado)

El contrato vigente es `protocol/src/main/kotlin/ar/truco/protocol/Mensajes.kt` (`StatePayload`).
El estado es completo en cada cambio y **se arma por jugador** en el servidor.

```json
{
  "seq": 42,
  "fase": "JUEGO",
  "miJugadorId": 2,
  "asientos": [
    { "id": 0, "nombre": "Ana", "equipo": 0, "conectado": true, "listo": true,
      "esHost": true, "esMano": true, "cartasRestantes": 2, "ausente": false }
  ],
  "puntajes": [7, 5],
  "targetScore": 15,
  "etapa": "MALAS",
  "objetivoEtapa": 15,
  "malasGanadasPor": null,
  "puntajesMalas": [],
  "manoIndex": 0,
  "manoNumero": 3,
  "turno": 2,
  "valorMano": 2,
  "miMano": [ { "palo": "ESPADA", "numero": 1 } ],
  "manosReveladas": null,
  "bazas": [ { "numero": 1, "jugadas": [ { "jugador": 0, "carta": { "palo": "ORO", "numero": 7 } } ],
               "ganador": 0, "parda": false } ],
  "cantoPendiente": { "canto": "TRUCO", "deJugador": 3, "respondedor": 2, "deadlineMs": 18000 },
  "envido": { "declaraciones": [], "valorAcumulado": 0, "resuelto": true },
  "flor": null,
  "legalActions": ["JUGAR_CARTA", "CANTAR_TRUCO", "IRSE_AL_MAZO"],
  "cartasJugables": [ { "palo": "ESPADA", "numero": 1 } ],
  "envidoValores": [33, 27],
  "faltaEnvido": 8,
  "manoGanadaPor": null,
  "partidaGanadaPor": null
}
```

Reglas de privacidad (verificadas en `server/src/test/kotlin/ar/truco/server/SalaTest.kt`):
- `miMano` solo contiene las cartas del jugador autenticado; `cartasRestantes` es un número.
- `manosReveladas` es `null` durante la mano y solo aparece en `MANO_CERRADA` / `FIN_PARTIDA`.
- `legalActions`/`cartasJugables` los calcula el motor → el cliente no duplica reglas.
- Hay un test que compara el JSON emitido a A contra las cartas de B y falla si aparece una sola.

Otros payloads del servidor: `welcome`, `lobby`, `state`, `events`, `error`, `pong`, `ended`
(en `protocol/.../Mensajes.kt`). Los eventos traen `texto` ya redactado en español para el log de la mesa.

## 4. `Accion`

```json
{ "tipo": "JUGAR_CARTA", "carta": { "palo": "ESPADA", "numero": 1 } }
{ "tipo": "CANTAR", "canto": "ENVIDO" }        // ENVIDO | ENVIDO_ENVIDO | REAL_ENVIDO | FALTA_ENVIDO | FLOR | CONTRAFLOR | CONTRAFLOR_AL_RESTO | TRUCO | RETRUCO | VALE_CUATRO
{ "tipo": "RESPONDER", "respuesta": "QUIERO" } // QUIERO | NO_QUIERO
{ "tipo": "IRSE_AL_MAZO" }
```

Idempotencia: mismo `id` + misma acción → se ignora sin error. `id` distinto con `stateSeq` viejo →
`STALE_STATE`.

## 5. Secuencias

### Unirse
```
C: hello{nombre, protocolVersion:1}
S: welcome{tuJugadorId:null, playerToken:null}
C: join{nombre:"Ana"}
S: welcome{tuJugadorId:0, playerToken:"…"}
S: lobby{…}  (broadcast a todos)
C: ready{listo:true}
S: lobby{…}
S: state{seq:1, fase:"REPARTO", …}   (cuando el host inicia)
```

### Jugar una carta
```
C: action{stateSeq:41, id:"…", accion:{tipo:"JUGAR_CARTA", carta:{palo:"ORO",numero:7}}}
S: state{seq:42, …}     ← broadcast filtrado
S: events{seq:42, eventos:[{tipo:"CARTA_JUGADA", jugador:2, carta:{…}}]}
```

### Canto con timeout
```
C: action{…, accion:{tipo:"CANTAR", canto:"TRUCO"}}
S: state{…, cantoPendiente:{tipo:"TRUCO", respondedor:1, deadlineMs:20000}}
   …20 s sin respuesta…
S: state{…, cantoPendiente:null, puntajes:[8,5]}  (auto NO_QUIERO)
S: events{eventos:[{tipo:"CANTO_RECHAZADO_AUTO", por:"timeout"}]}
```

### Reconexión
```
(corte de red)
C: rejoin{playerToken:"…"}
S: welcome{tuJugadorId:2, …}
S: state{seq:57, …}      ← estado actual completo, como si nada
```

## 6. Errores

| code | Significado | `fatal` |
|------|-------------|---------|
| `PROTOCOL_VERSION` | Versión de protocolo distinta | sí (fuerza recarga) |
| `SALA_LLENA` | No hay sillas libres | no |
| `TOKEN_INVALIDO` | `playerToken` desconocido/expirado | no (permite re-join) |
| `ACCION_ILEGAL` | El motor la rechazó (`motivo` explica) | no |
| `NO_ES_TU_TURNO` | Acción fuera de turno | no |
| `STALE_STATE` | `stateSeq` desactualizado | no (reenvía estado) |
| `PARTIDA_EN_CURSO` | Se intentó unir con la partida empezada y sin token | no |
| `HOST_LEFT` | Sala cerrada | sí |

Los `error` no fatales **no** rompen la conexión: el cliente muestra un toast y sigue con el estado
que ya tiene.

## 7. Compatibilidad y despliegue

- El cliente se sirve desde el mismo servidor → ambos se actualizan juntos. Igual se valida
  `protocolVersion` (en `hello` y en el `v` del envelope) y se fuerza `location.reload()` en caso de
  mismatch (pestañas abiertas viejas).
- `GET /api/info` devuelve `{ appVersion, protocolVersion, codigo, jugadores, maxJugadores, fase,
  partidaEnCurso }` para diagnóstico rápido (y para que el lobby muestre datos sin abrir WS).

## 8. Automatismos del servidor (Fase 2, ya implementados)

Estos comportamientos no necesitan mensajes nuevos y evitan que la mesa se trabe:

| situación | qué hace el servidor |
|-----------|----------------------|
| Canto sin respuesta (`rules.timeoutCantoMs`) | responde **no quiero** por el jugador |
| Turno sin jugar (`rules.timeoutTurnoMs`) | juega la **carta más baja** por el jugador (o canta flor si está en la mano de flor) |
| Mano cerrada (`pausaEntreManosMs`) | reparte la mano siguiente automáticamente; el mano rota |
| Cierre de socket en partida | reserva la silla y marca `conectado:false`; si era su turno se juega
  por él a los `esperaAusenteMs` |
| Dos turnos jugados por un desconectado | queda `ausente:true` (la mesa sigue sola con el compañero) |
| `rejoin` de un ausente | vuelve a jugar normalmente y se limpia la marca |
| Cierre de socket en el lobby | libera la silla y reasigna ids |

## 9. Estado de implementación

- `:protocol` — todos los DTOs de este documento, `PROTOCOL_VERSION = 1`.
- `:server` — módulo Ktor (`/api/info`, `/ws`, estáticos desde `assets/web` o `web/dist`),
  `Sala` con sección crítica única, timeouts, reconexión, idempotencia por `id` de acción
  (últimas 200), filtrado de cartas. 15 tests de integración con `testApplication` cubriendo
  lobby, equipos de 4, envido, mano completa, privacidad, `STALE_STATE`, timeout, reconexión, errores.
- Modo desarrollo: `./gradlew :server:run` (ver `Main.kt`, puerto 8080, `TRUCO_JUGADORES`).

## 10. Cliente web (`web/`, Fase 3)

- Un solo store (`Cliente`) con `useSyncExternalStore`; sin Redux ni librerías de estado.
- Cada acción lleva `id` (`crypto.randomUUID` con fallback: en `http://192.168.x.x` **no** hay contexto
  seguro y `crypto.randomUUID` puede no existir).
- `localStorage` guarda nombre y `playerToken`; al abrir/reconectar manda `hello` y después `rejoin`.
  Si el token ya no existe (`TOKEN_INVALIDO`), lo descarta y vuelve a entrar con el nombre.
- Reconexión exponencial (0.8 s → 6 s), `ping` cada 10 s y reintento inmediato al volver a la pestaña
  (`visibilitychange`) o al recuperar red (`online`).
- El countdown de los cantos usa `cantoPendiente.deadlineMs` del servidor (por eso el servidor
  reprograma el timeout **antes** de emitir el estado: si no, el cliente mostraba el deadline viejo).
- No se usa nada que requiera contexto seguro: nada de Service Worker, Clipboard ni `randomUUID` sin
  fallback. Es la razón de no pelear por HTTPS en la LAN.
- **Nada de `vh`/`dvh` para la altura de las pantallas**: algunos WebViews (visto en el emulador de
  Android 17) resuelven esas unidades a 0 y la pantalla queda vacía. Cada pantalla se posiciona con
  `position: fixed; inset: 0`.

## 11. Tests end-to-end (`e2e/`)

Playwright con Chromium, viewport de Pixel 7, contra el servidor Kotlin real (`:server:installDist`)
que sirve `web/dist`. Un test cubre el flujo completo: dos contextos de navegador, lobby, partida,
privacidad de cartas, recarga con rejoin, mano completa y reparto automático.

```bash
cd e2e && npx playwright test
```

`e2e/scripts/captura.mjs` genera las capturas de `docs/capturas/` (útil para revisar el diseño sin
abrir el teléfono).
