# 05 — Roadmap, testing y estructura del repo

Estimaciones en **sesiones de trabajo de medio día**. No son deadlines.

## Fase 0 — Planificación ✅
Entregables: docs 00–05, README, `.gitignore`. Decisiones D-01, D-02, D-05 y D-08 resueltas.

## Fase 1 — Motor de reglas ✅ (hecho)
`./gradlew :engine:test` → **43 tests verdes**.

- Jerarquía de cartas, valores de envido, mazo de 40 y reparto determinista por semilla.
- 2/4/6 jugadores con equipos alternados (D-05) y turnos rotativos.
- Bazas, pardas ("la gana quien viene ganando; si no, el mano"), cierre de mano, rotación de mano.
- Truco / retruco / vale cuatro, aceptados y rechazados, escalada dentro de la negociación.
- Envido completo: cadena, escaladas, tabla exacta de "no quiero", falta envido, empate por configuración.
- Flor (canto obligatorio, 3 puntos) con el envido deshabilitado en esa mano.
- Irse al mazo con el valor vigente.
- `accionesLegales` por jugador: el cliente nunca calcula reglas.
- `PartidaAleatoriaTest`: 480 partidas completas al azar sobre 8 configuraciones, con invariantes
  (cartas conservadas, puntajes dentro del objetivo, toda partida termina).

**Pendiente anotado:** contraflor / contraflor al resto (Fase 5), malas y buenas a 30 (Fase 5),
`envidoHastaSegundaCarta` (la ventana existe pero el valor se calcula con las 3 cartas originales,
revisar cuando se use la variante).

## Fase 2 — Protocolo y servidor ✅ (hecho)
`./gradlew :server:test` → **15 tests verdes** (integración real por WebSocket).

- `:protocol`: envelope `{t, id, v, d}`, DTOs de entrada/salida, `PROTOCOL_VERSION = 1`, códigos de error.
- `:server`: `Application.trucoModule(config)` con `/api/info`, `/ws` y estáticos (`assets/web` o `web/dist`).
- `Sala`: lobby con tokens, equipos alternados, arranque cuando todos están listos, partida autoritativa,
  broadcast de estado filtrado por jugador, eventos con texto en español, timeouts, reconexión.
- Timeouts: canto → "no quiero"; turno → carta más baja; fin de mano → reparto automático.
- Idempotencia por `id` de acción y validación de `stateSeq` (`STALE_STATE`).
- `Main.kt` para correr el servidor en la PC (`./gradlew :server:run`, puerto 8080) y probar el cliente
  web antes de empaquetar en Android.
- Tests: lobby, privacidad de cartas (el JSON de A no contiene cartas de B), mano completa, envido,
  4 jugadores en dos equipos, `STALE_STATE`, fuera de turno, timeout de canto, reconexión con token,
  sala llena, versión de protocolo, token inválido, reparto automático de la mano siguiente.

**Verificado a mano:** `installDist` + `curl /api/info` → `{"protocolVersion":1,...,"maxJugadores":4}`.

## Fase 3 — Cliente web + mesa del host (5–6 sesiones) ← *la más pesada*
`web/`

- Vite + TS + React: store, cliente WS con reconexión exponencial, `localStorage` de token.
- Pantallas: unirse (nombre), lobby, mesa, fin de partida.
- Mesa mobile-first: barra superior (puntajes, valor de mano, mano), cartas del jugador con
  abanico y drag/tap, cartas jugadas por baza, indicador de turno, panel de cantos con solo las
  acciones legales, log de eventos.
- Animaciones CSS (reparto, carta jugada, canto) y sonidos opcionales.
- Empaquetado en `assets/web` vía tarea Gradle; WebView del host apuntando a `127.0.0.1` (D-02).

**Aceptado cuando:** 4 personas en 4 teléfonos reales juegan una mano completa sin instrucciones y
sin bugs bloqueantes; el layout no rompe en 360×640 ni en pantallas con notch.

## Fase 3 — Cliente web jugable ✅ (hecho)
`cd web && npm run build` (typecheck + Vite, ~74 KB gzip) y `e2e → 1 test verde en Chromium real`.

- React 19 + TypeScript estricto, sin librerías de UI ni CSS: 235 KB de JS, 10 KB de CSS, todo local
  (nada de CDN: la red del juego no tiene internet).
- Pantallas: ingreso, lobby (sillas, equipos, listos), mesa y overlay de fin de partida.
- Mesa mobile-first: tanteador, “vale N”, carta de mano, bazas con parda, cartas rivales boca abajo
  (o reveladas al cerrarse la mano), panel de cantos con countdown real del servidor, historia de la
  mano y botón de mazo.
- **Baraja española de verdad**: palos de espada, basto, oro y copa dibujados como SVG inline (nada de
  ♠♥♦♣), con doble filete, número en serif y el nombre de la figura (sota/caballo/rey) en el 10/11/12.
  Sin fuentes ni imágenes remotas: los iconos heredan el color del palo (negro o rojo).
- Solo se ofrecen las acciones que manda `legalActions`: el cliente no conoce las reglas.
- Jugar una carta requiere **dos toques** (elegir + confirmar) para evitar errores en el teléfono.
- Reconexión automática con backoff, `ping` cada 10 s, token en `localStorage` (`rejoin`), y al volver
  a la pestaña se reintenta la conexión.
- Si la sala vuelve al lobby tras el final, la mesa se cierra sola (revancha sin rearmar nada).
- E2E (`e2e/`): dos contextos de navegador entran, se marcan listos, verifican que no se ven las
  cartas del otro, recargan la página (recupera la silla y la mano), juegan la mano completa con
  doble toque, comprueban el punto y el reparto automático de la mano siguiente.
- Bug real encontrado por el E2E: con `explicitNulls = false` el `welcome` omitía `tuJugadorId` y el
  cliente lo leía como `undefined` (pantalla en el lobby sin estar sentado). Se serializan los nulls
  explícitos y el cliente usa `?? null`.

## Fase 4 — App Android anfitriona ✅ (hecho)
`./gradlew :app:assembleDebug` → APK de debug, verificado en emulador Android 17 (API 37).

- `:app` en Compose: lobby (1 vs 1 / 2 vs 2 / 3 vs 3, a 15/30, con flor), pantalla de servidor
  (dirección, QR, contador de jugadores, zona Wi-Fi opcional) y la mesa en un WebView.
- `ServicioServidor` en primer plano con `foregroundServiceType="specialUse"`, notificación
  persistente con el contador y acción “Terminar”, `WifiLock` + `PARTIAL_WAKE_LOCK`.
  **Probado con la pantalla apagada 45 s: el servidor siguió respondiendo y la partida intacta.**
- El cliente web se empaqueta como recursos Java del APK bajo `web/` (tarea `copiarClienteWeb` que
  sincroniza `web/dist` en `preBuild`), así el servidor lo sirve con el mismo `staticResources`
  que en la PC.
- QR con ZXing (sin red ni Play Services), IP local con `ConnectivityManager`, puerto libre
  automático (8080, 8081…), y URL que se refresca sola cada 1,5 s.
- Zona Wi-Fi con `startLocalOnlyHotspot` + permiso `NEARBY_WIFI_DEVICES`: verificado en el emulador
  (red `AndroidShare_xxxx` + clave) y la dirección de la mesa se actualiza al nuevo IP.
- Verificación real: la app abrió la mesa, 3 navegadores de la PC entraron por `adb forward`,
  jugaron una mano; desde el WebView se jugó una carta, se volvió con el botón atrás y se terminó
  la mesa (el servidor cerró).

### Bugs que aparecieron en el camino (y quedaron arreglados)

1. **Recursos en la raíz del APK**: copiar `web/dist` a `generated/web` dejaba `index.html` en la
   raíz y `assets/*` mezclado con los assets de Android; `staticResources("/", "web")` daba 404.
   Ahora se copia a `generated/cliente-web/web` (entradas `web/index.html`, `web/assets/...`).
2. **El WebView del emulador resuelve `100vh`, `100dvh` y hasta el `height: 100%` del `html` a 0**, así
   que las pantallas quedaban colapsadas. Las pantallas ahora usan `position: fixed; inset: 0`
   (y coinciden con lo que ya hacía la mesa), que sí funciona en todos lados.
3. **AGP 9 trae Kotlin embebido**: aplicar `org.jetbrains.kotlin.android` (o dejar KGP en el classpath
   raíz con `apply false`) rompe con `BaseVariant`; hay que usar el Kotlin del AGP (2.2.10) y solo
   aplicar el plugin de Compose de esa misma versión.
4. **`local.properties`** con `C\:\\AndroidSdk` fallaba la validación del SDK; se escribe
   `sdk.dir=C:/AndroidSdk`.

**Pendiente de esta fase**: probar en teléfonos reales (el emulador no simula aislamiento de clientes
ni consumo real de batería) y el caso de crear la zona Wi-Fi con jugadores ya conectados (cambia la IP:
hay que reconectar).
- Lobby Compose: nombre, cantidad de jugadores, `RulesConfig`, botón "Crear partida".
- `ServerService` en primer plano + `WifiLock`/`WakeLock` + notificación con contador.
- QR (zxing) con la URL y, si aplica, SSID/password del hotspot.
- Detección de IP local; aviso si no hay Wi-Fi; manejo de puerto ocupado.
- Spike R2: `startLocalOnlyHotspot` en 2–3 dispositivos; documentar resultado y fallbacks.
- Pantalla de error si Play/oem bloquea el servicio; guía "cómo desactivar aislamiento".

**Aceptado cuando:** con la pantalla apagada 10 minutos, el servidor sigue vivo y un invitado puede
unirse y jugar.

## Fase 5 — Multijugador completo (4–5 sesiones)
- 4 y 6 jugadores (D-05), equipos, envido/flor con equipos.
- Reconexión, abandono, "ausente", regla de compañero solo.
- Partido a 15 y 30 (malas/buenas), revancha sin rearmar la sala.
- Ajustes de partida visibles en el lobby y en `/api/info`.

**Aceptado cuando:** 6 teléfonos juegan una partida a 30 con alguien que se desconecta y vuelve,
sin corromper el estado.

## Fase 6 — Pulido (3–4 sesiones)
- Accesibilidad (tamaños táctiles ≥ 44 px, contraste, `prefers-reduced-motion`), i18n es/en.
- Tutorial de 3 pasos y ayuda de reglas desde la mesa.
- Sonidos, vibración, tema claro/oscuro.
- Log de diagnóstico exportable (texto) para depurar redes raras.

## Fase 7 — Release (2 sesiones)
- Firma, `assembleRelease`, R8 con reglas para Ktor/kotlinx.serialization.
- APK/AAB, README de instalación, changelog.
- Si D-08 = Play: ficha, política de privacidad ("no se recolectan datos"), declaración de
  `specialUse`, beta cerrada con 12 testers.

## Estrategia de testing

| Nivel | Herramienta | Qué cubre |
|-------|-------------|-----------|
| Motor | JUnit5 + kotest property | Reglas, casos de mesa, invariantes en 5.000 partidas |
| Servidor | Ktor `testApplication` + `TestCoroutineScheduler` | Protocolo, filtrado de información, timeouts |
| Cliente | Vitest | Reducer/store, parseo de estados, reconexión |
| E2E | Playwright (2/4/6 contextos headless) | Flujo real: unirse, jugar mano, cantar, perder conexión |
| Manual | 2–3 teléfonos + router + hotspot | Lo que ningún test automático ve |

Regla: el **filtrado de información** (nadie ve cartas ajenas) es un test, no una revisión de código.

## Estructura del repo (objetivo)

```
TrucoArgentino/
├─ README.md
├─ docs/                     00–05
├─ build.gradle.kts          (raíz)
├─ settings.gradle.kts
├─ gradle/libs.versions.toml (version catalog)
├─ engine/                   Kotlin puro (JVM) — reglas ✅
│  └─ src/{main,test}/kotlin/ar/truco/engine/
├─ protocol/                 DTOs @Serializable ✅ (Fase 2)
├─ server/                   Ktor: /api/info, /ws, estáticos ✅ (Fase 2)
├─ app/                      Android (Compose + Service + WebView) ✅ (Fase 4)
│  └─ build/generated/cliente-web/web/   build del cliente (generado)
├─ web/                      React + TS + Vite ✅ (Fase 3)
│  ├─ src/{net,store,ui,rules-types}
│  └─ package.json
└─ e2e/                      Playwright ✅ (Fase 3)
```

## Orden sugerido si querés ver algo en pantalla rápido

Si la parte de Android (Fase 4) intimida, se puede invertir el orden: Fase 1 → 2 → **3 con el
servidor corriendo en la PC** (`./gradlew :server:run`) → 4. Así se prueba el juego completo con
teléfonos conectados al Wi-Fi de la PC antes de tocar Kotlin/Android.
