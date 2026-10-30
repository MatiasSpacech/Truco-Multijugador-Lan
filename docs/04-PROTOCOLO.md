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
| `action` | `{ stateSeq, accion: Accion, id }` | Acción de juego (ver §4) |
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

### Vista privada de `state`

```json
{
  "seq": 42,
  "fase": "JUEGO",
  "miJugadorId": 2,
  "asientos": [
    { "id": 0, "nombre": "Ana", "equipo": 0, "conectado": true, "esMano": true, "cartasRestantes": 2 },
    { "id": 1, "nombre": "Beto", "equipo": 1, "conectado": true, "esMano": false, "cartasRestantes": 3 }
  ],
  "puntajes": [7, 5],
  "targetScore": 15,
  "manoIndex": 0,
  "turno": 2,
  "valorMano": 2,
  "miMano": [ { "palo": "ESPADA", "numero": 1 }, { "palo": "ORO", "numero": 7 }, { "palo": "COPA", "numero": 4 } ],
  "bazas": [ { "jugadas": [ { "jugador": 0, "carta": {...} } ], "ganador": 0 } ],
  "cantoPendiente": { "tipo": "TRUCO", "deJugador": 3, "respondedor": 2, "deadlineMs": 18000 },
  "envido": { "cantado": ["ENVIDO"], "valorAcumulado": 4, "resuelto": false },
  "flor": null,
  "legalActions": ["JUGAR_CARTA", "CANTAR_TRUCO", "IRSE_AL_MAZO"],
  "cartasJugables": [ { "palo": "ORO", "numero": 7 } ]
}
```

Reglas de privacidad (verificables en tests):
- `miMano` solo contiene las cartas del jugador autenticado.
- `cartasRestantes` es un número, nunca las cartas.
- Al resolverse la mano (`MANO_CERRADA`) se revelan todas las cartas de esa mano (es público en la mesa).
- `legalActions`/`cartasJugables` los calcula el motor → el cliente no duplica reglas.

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
  `protocolVersion` y se fuerza `location.reload()` en caso de mismatch (pestañas abiertas viejas).
- `GET /api/info` devuelve `{ appVersion, protocolVersion, codigoSala, jugadores, fase }` para
  diagnóstico rápido (y para que el lobby muestre datos sin abrir WS).
