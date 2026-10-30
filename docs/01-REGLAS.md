# 01 — Reglas, variantes y modelo de dominio

Fuente de verdad para implementar el motor. Todo lo que diga "configurable" va en `RulesConfig`.

## 1. Mazo

Mazo español de 40 cartas: palos `ESPADA (E)`, `BASTO (B)`, `ORO (O)`, `COPA (C)`.
Números: 1..7, 10 (sota), 11 (caballo), 12 (rey).

Representación: `Carta(palo: Palo, numero: Int)` con `numero ∈ {1,2,3,4,5,6,7,10,11,12}`.

## 2. Jerarquía de cartas (de mayor a menor)

`poder` es un entero 1..14; **empate de poder = parda**.

| poder | carta(s) |
|-------|----------|
| 14 | 1 de espada (ancho de espada) |
| 13 | 1 de basto (ancho de basto) |
| 12 | 7 de espada |
| 11 | 7 de oro |
| 10 | los tres 3 (cualquier palo) |
| 9 | los dos 2 |
| 8 | 1 de copa y 1 de oro ("anchos falsos") |
| 7 | los 12 (reyes) |
| 6 | los 11 (caballos) |
| 5 | los 10 (sotas) |
| 4 | 7 de copa y 7 de basto ("sietes falsos") |
| 3 | los 6 |
| 2 | los 5 |
| 1 | los 4 |

## 3. Valor para envido

`valorEnvido(c) = numero` si `numero ∈ 1..7`, y `0` si `numero ∈ {10,11,12}`.

- Si no hay 2 cartas del mismo palo → **envido = mayor `valorEnvido` de las 3**.
- Con 2 del mismo palo → **20 + las dos del mismo palo**.
- Con 3 del mismo palo → **20 + las dos mayores** (y además es flor si la flor está habilitada).

## 4. Flor

- **Flor** = 3 cartas del mismo palo → `20 + suma de las 3` (reales: 10,11,12 valen 0).
- `flor = SIN_FLOR | FLOR` (default: `SIN_FLOR` para el MVP; `FLOR` habilita la mano de flor y
  deshabilita el envido en esa mano).
- Con `FLOR`, el canto de flor es **obligatorio** al tenerla y se resuelve **antes** del envido.
  Si ambas partes tienen flor, gana la más alta; el que pierde entrega **3** puntos.
- **Pendiente (Fase 5)**: contraflor (6) y contraflor al resto.

## 5. Envido

Solo se puede cantar **antes de que se resuelva la primera baza** (configurable
`envidoHastaSegundaCarta = false`; en variante "hasta la segunda" se permite hasta que se juegue
la 2.ª carta). Lo canta el equipo que **no** es mano; responde el equipo rival.

Escalera de cantos (cada uno reemplaza/agranda la apuesta):

| canto | puntos si "quiero" |
|-------|--------------------|
| Envido | 2 |
| Envido + Envido | 4 |
| Real Envido (solo) | 3 |
| Envido + Real Envido | 5 |
| Envido + Envido + Real Envido | 7 |
| Falta Envido | lo que le falta al que está primero (empate → `targetScore`) |
| Envido + Falta Envido | 2 + falta |

**Regla de "no quiero"**: si se rechaza la primera declaración de la cadena → **1 punto**.
Si se rechaza una declaración posterior → vale lo que valía la cadena **hasta la declaración
anterior** (con el Envido solo contando como 1 en ese cálculo).

Verificación con la tabla anterior: `Envido+Envido no quiero` = 2 ✔, `Envido+Real Envido no quiero` = 2 ✔,
`Envido+Envido+Real Envido no quiero` = 4 ✔, `Real Envido (solo) no quiero` = 1 ✔.

Límites configurables: `maxCantosEnvido` (default 4), escalera habilitada/deshabilitada
(`envido = true`, `realEnvido = true`, `faltaEnvido = true`).

Quien dice "quiero" no revela cartas: se declara el valor y se comparan. Empate → **gana el no-mano**
(configurable `empateEnvido = NO_MANO | MANO`).

## 6. Truco

Escalera: `TRUCO` → `RETRUCO` → `VALE_CUATRO`.

| canto | valor de la mano si se acepta | puntos al que cantó si no quieren |
|-------|------------------------------|-----------------------------------|
| Truco | 2 | 1 |
| Retruco | 3 | 2 |
| Vale cuatro | 4 | 3 |

- Se puede cantar en **cualquier momento de la mano**, incluso antes del envido (configurable
  `trucoAntesDeEnvido = true`). Aceptar un truco **cierra el envido** de esa mano.
- Pueden cantar **jugadores de los dos equipos**; responde el rival que sigue en el orden de asientos.
  El que responde puede escalar (`retruco`, `vale cuatro`) en lugar de querer.
- **Irse al mazo**: el jugador en turno abandona la mano; el equipo rival se lleva el valor vigente
  (1 si no hubo canto).

## 7. Juego de la mano

- 3 cartas por jugador, repartidas de a una en orden desde la mano hasta el pie.
- El **mano** juega primero; luego juega el ganador de la baza anterior.
- **Baza**: gana la carta de mayor `poder`; empate de poder = **parda**.
- **Parda**: la gana quien viene ganando la mano; si van iguales, la gana **el mano**. En empate de
  bazas al final de las tres, gana el equipo del mano.
- Gana la mano el que se lleva **2 de 3 bazas**.
- Rotación: el mano de la próxima mano pasa al siguiente jugador (`manoIndex = (manoIndex+1) % n`).

## 8. Equipos y turnos

| jugadores | equipos | disposición | orden de juego |
|-----------|---------|-------------|----------------|
| 2 | 1 vs 1 | enfrentados | A, B |
| 4 | 2 vs 2 | compañeros **enfrentados** (A-C, B-D) | A, B, C, D (alternado) |
| 6 | 2 equipos de 3 (**D-05**) | A-D, B-E, C-F | A, B, C, D, E, F (alternado) |

- El asiento 0 es la **mano** inicial; los equipos son pares/impares.
- Alternativa de 6 jugadores: 3 parejas rotando (variante, requiere cambio en la resolución de
  bazas). **Se decide en D-05 antes de la Fase 5.**

## 9. Puntaje de partido

- `targetScore ∈ {15, 30}`. A 30 se juegan **malas** (a 15) y **buenas** (a 30) con marcador corrido.
- Gana quien alcanza `targetScore`. Puntaje máximo por mano acotado por `targetScore`.
- Empate exacto en `targetScore` al mismo tiempo no existe (los puntos se otorgan a un solo equipo).

## 10. Modelo de dominio (Kotlin, módulo `:engine`)

## 10. Modelo de dominio implementado (`engine/`)

El motor real está en `engine/src/main/kotlin/ar/truco/engine/`. API pública:

```kotlin
// Cartas.kt
enum class Palo { ESPADA, BASTO, ORO, COPA }
data class Carta(val palo: Palo, val numero: Int)
object Cartas { fun poder(c: Carta): Int; fun valorEnvido(c: Carta): Int; fun mazoCompleto(); fun mezclar(...) }

// Envido.kt
object Envido { fun tieneFlor(mano); fun valor(mano); fun valorFlor(mano); fun valorPorEquipo(manos, equipos) }

// Cantos.kt  (tablas de puntos y validación de escaladas)
object Cantos { valorTruco, valorTrucoRechazado, valorEnvidoAceptado, valorEnvidoRechazado, puedeEscalarEnvido, ... }

// Rules.kt
data class RulesConfig(
    val jugadores: Int = 2,                 // 2, 4 o 6
    val targetScore: Int = 15,              // 15 o 30
    val flor: FlorMode = FlorMode.SIN_FLOR,
    val envido: Boolean = true,
    val realEnvido: Boolean = true,
    val faltaEnvido: Boolean = true,
    val maxEnvidos: Int = 2,
    val trucoAntesDeEnvido: Boolean = true,
    val envidoHastaSegundaCarta: Boolean = false,
    val empateEnvido: EmpateEnvido = EmpateEnvido.NO_MANO,
    val timeoutCantoMs: Long = 20_000,      // lo usa el servidor
    val timeoutTurnoMs: Long = 45_000,
)

// Estado.kt
enum class Fase { ESPERANDO, FLOR, JUEGO, MANO_CERRADA, FIN_PARTIDA }
data class Estado(
    val config: RulesConfig, val asientos: List<Asiento>, val seed: Long,
    val fase: Fase, val manoNumero: Int, val manoIndex: Int,
    val puntajes: List<Int>,                // por equipo
    val manos: List<List<Carta>>,           // manos actuales (van perdiendo cartas)
    val manosRepartidas: List<List<Carta>>, // las 3 originales: base del envido y la flor
    val bazas: List<Baza>, val turno: Int,
    val valorMano: Int,                     // 1..4
    val negociacion: Negociacion?,          // canto pendiente de respuesta
    val envidoCerrado: Boolean,
    val florDeclaraciones: List<Declaracion>, val envidoValores: List<Int>,
    val eventos: List<Evento>,
)
sealed interface Accion {
    data class JugarCarta(val carta: Carta) : Accion
    data class Cantar(val canto: Canto) : Accion
    data class Responder(val respuesta: Respuesta) : Accion
    data object IrseAlMazo : Accion
}

// Motor.kt
object Motor {
    fun nuevaPartida(config, nombres, seed): Resultado<Estado>
    fun repartir(estado): Resultado<Estado>
    fun aplicar(estado, jugador, accion): Resultado<Estado>   // Ok(estado, eventos) | Ilegal(motivo)
    fun accionesLegales(estado, jugador): Set<TipoAccion>     // el cliente no calcula reglas
    fun envidoDisponible(estado); fun faltaEnvido(estado); fun siguienteRival(estado, desde)
}
```

Reglas de pureza: `aplicar` no muta el estado, no lee reloj ni azar global. El azar entra por la
semilla en `repartir` (`Random(seed * 31 + manoNumero)`), el tiempo lo maneja el servidor.

## 11. Casos de test obligatorios (los "de mesa")

Implementados en `engine/src/test/kotlin/`: `CartasTest`, `EnvidoTest`, `CantosTest`, `MotorTest`,
`PartidaAleatoriaTest` (480 partidas completas al azar sobre 8 configuraciones).

1. ✅ Ancho de espada vence a todo; 7 de espada vence al 7 de oro.
2. ✅ Los tres empatan entre sí → parda.
3. ✅ Envido sin cartas del mismo palo = mayor carta (12 vale 0).
4. ✅ Envido con 3 del mismo palo = 20 + dos mayores; ej. `3,2,1 del mismo palo = 25`.
5. ✅ `4,5,6 del mismo palo = 31`; `7,6,5 = 33`; `12,11,10 = 20`.
6. ✅ Cadena de envido completa y todos los rechazos (tabla del §5).
7. ✅ Falta envido con marcador 12-9 a 15 → 3 puntos y cierre de partida.
8. ✅ Flor: se canta primero y paga 3. ⏳ Contraflor (Fase 5).
9. ✅ Truco aceptado (2) + vale cuatro rechazado (3) → 3 al que cantó.
10. ✅ Irse al mazo con truco aceptado: se cobra el valor vigente (2).
11. ✅ Parda en segunda baza: la gana quien viene ganando.
12. ✅ Rotación de mano entre manos.
13. ✅ Partida completa aleatoria: terminan y nadie supera `targetScore`.
