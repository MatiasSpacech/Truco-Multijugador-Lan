# 03 — Stack tecnológico y decisiones (ADR)

Contexto del equipo: un desarrollador, entorno Windows, Node v24 disponible, sin backend remoto.
Prioridad: **que funcione en redes reales de casa**, que las reglas sean testeables, y que el
esfuerzo de UI no se duplique.

## 1. Stack recomendado

| Capa | Tecnología | Por qué |
|------|-----------|---------|
| App Android | **Kotlin 2.x**, `minSdk 26`, `targetSdk` 35/36, **Jetpack Compose** (Material 3) | Estándar actual; el lobby es UI menor: 4 pantallas |
| Servidor embebido | **Ktor 3.x** con engine **CIO**, `WebSockets` + `StaticContent` | Servidor HTTP/WS maduro sobre JVM, corre en Android, un solo artefacto, sin NDK |
| Serialización | **kotlinx.serialization** (JSON) | Sin reflexión, tipos compartidos entre server y protocolo |
| Concurrencia | **coroutines** + `Channel` (actor por partida) | Modelo natural de "una acción por vez" |
| Motor de reglas | Kotlin puro en `:engine` | Testeable en JVM sin emulador, rápido |
| Cliente web | **TypeScript + Vite + React 19** (o Svelte 5 si preferís menos ceremonia) | Ecosistema, tipado, build chico, sin CDN |
| Estilos | CSS plano con variables + `dvh`/`safe-area-inset` | Sin runtime de CSS; control total del layout de mesa |
| Estado cliente | store propio (~100 líneas) o **Zustand** | El estado llega por snapshot; no hace falta más |
| QR | `com.google.zxing:core` (genera Bitmap) | Sin dependencias de red ni Google Play Services |
| Persistencia local | **DataStore Preferences** | Ajustes + token de jugador |
| Tests motor | JUnit5 + **kotest property** | Casos de mesa + partidas aleatorias invariantes |
| Tests servidor | Ktor `testApplication` + `TestCoroutineScheduler` | Protocolo y timeouts sin flakiness |
| Tests E2E | **Playwright** contra el servidor Kotlin real, 2/4/6 contextos | Verifica el flujo completo headless en CI |
| Build | Gradle KMP-free multimódulo + npm; tarea Gradle `copyWebAssets` | Un `./gradlew assembleDebug` produce todo |

Notas:
- **Sin CDN, sin fuentes remotas, sin analytics.** Todo en `assets/web`.
- El cliente web no necesita Service Worker (no hay contexto seguro en `http://LAN`).
- `ws://` plano es suficiente: la red es de confianza y no hay datos sensibles. Documentarlo
  explícitamente para no desviarse a TLS autofirmado (dolor innecesario en móviles).

## 2. ADRs

### D-01 — Servidor en Kotlin/Ktor (RECOMENDADO) ✅
**Decisión:** el servidor vive en el proceso Android (JVM/Kotlin) con Ktor CIO.
**Alternativa:** `nodejs-mobile` + servidor TS.
**Pros de nodejs-mobile:** un solo lenguaje (TS) para server y cliente; reutilizar tipos y hasta
el motor; ecosistema npm.
**Contras:** `nodejs-mobile` tiene mantenimiento irregular, exige NDK y libs por ABI
(+40–60 MB de APK), y ata el proyecto a una dependencia con riesgo de abandono; además habría que
empaquetar Node + assets en el APK y depurar crashes nativos.
**Conclusión:** Kotlin/Ktor. El costo de "dos lenguajes" es bajo porque el cliente **no** implementa
reglas: consume `legalActions` del servidor.

### D-02 — La mesa del anfitrión es WebView con el mismo cliente web (RECOMENDADO) ✅
**Decisión:** Compose solo para lobby/ajustes/QR; la mesa se abre en un `WebView` contra
`http://127.0.0.1:8080`.
**Alternativa:** mesa nativa en Compose + mesa web para invitados → dos UIs, dos veces el trabajo de
cartas, animaciones, cantos y responsive. Descartado.
**Riesgo:** rendimiento de WebView con animaciones. Mitigación: animaciones con CSS transforms
(aceleradas por GPU), sin librerías pesadas; medir en un dispositivo de gama baja en Fase 3.

### D-03 — Servidor autoritativo con clientes "tontos" ✅
**Decisión:** el cliente nunca conoce cartas ajenas ni calcula reglas; recibe `legalActions`.
Anti-trampa por diseño y permite cambiar reglas sin tocar el cliente.

### D-04 — Snapshot completo por evento, sin deltas ✅
**Decisión:** cada cambio emite el estado completo filtrado por jugador + lista de eventos.
**Por qué:** payloads de ~1–3 KB en LAN; elimina toda una clase de bugs de sincronización.
**Límite:** si algún día se agranda el estado, revisar (no antes).

### D-05 — Modo de 6 jugadores ✅ RESUELTA
**Decisión:** 2 equipos de 3, compañeros alternados (asientos 0,2,4 vs 1,3,5). Implementado en el
motor (`equipo = asiento % 2`), con escalada del truco y el envido por equipo.

### D-06 — Protocolo JSON versionado ✅
**Decisión:** `protocolVersion` entero; servidor y cliente deben coincidir (el cliente es servido por
el mismo servidor, así que la divergencia solo aparece con pestañas viejas → se fuerza recarga).

### D-07 — Sin PWA/offline install ✅
**Decisión:** no invertimos en Service Workers ni manifest instalable: `http://LAN` no es contexto
seguro. El QR + `localStorage` cubren el caso de uso.

### D-08 — Distribución ✅ RESUELTA
**Decisión:** APK directa. Sin Play Store, así que se evita la declaración de
`FOREGROUND_SERVICE_SPECIAL_USE` (R1) y las políticas de revisión. El servicio en primer plano
sigue siendo necesario para que el servidor sobreviva con la pantalla apagada, pero su tipo se elige
según lo que acepte el dispositivo (`specialUse` como fallback), sin auditoría externa.

## 3. Alternativas descartadas (y por qué)

| Opción | Veredicto |
|--------|-----------|
| **React Native + servidor ws/http embebido** | Servidor en JS con bridge, paquetes de terceros con mantenimiento dudoso, peor control del ciclo de vida del socket en background |
| **Flutter + `shelf` + Flutter Web para invitados** | Viable y con un solo lenguaje, pero el bundle de Flutter Web (varios MB) se sirve por Wi-Fi y agrega peso/tiempo de carga; WebView igual es necesaria para el host |
| **Capacitor/Cordova + plugin httpd** | Servidores HTTP de plugin no manejan bien WebSocket; abandonado en la práctica |
| **Kotlin Multiplatform con Compose para Web (Wasm)** | Comparte UI y motor, pero madurez y tamaño del bundle; buen candidato a **spike** después del MVP si molesta mantener dos UIs |
| **Servidor en un dispositivo aparte (Raspberry/PC)** | Rompe la promesa de "todo en el teléfono" |
| **WebRTC P2P entre navegadores** | Necesita signaling, sin ventaja: el servidor local ya es P2P en LAN |

## 4. Ajustes al entorno de desarrollo

**Verificado en esta máquina (Fase 1):**

| Componente | Versión |
|-----------|---------|
| JDK | Temurin 21.0.3 (en `PATH`) |
| Android SDK | `C:\AndroidSdk` — platforms `android-36`, `android-37.0`; build-tools 36/37 |
| Gradle | 9.6.0 (wrapper en el repo; lo exige AGP 9.x) |
| AGP | 9.5.0 (trae Kotlin 2.2.10 embebido) |
| Kotlin | 2.2.10 (la que usa AGP; alineada en todos los módulos) |
| compileSdk / targetSdk | 37 / 37 · minSdk 26 |
| JUnit | 5.11.4 (Jupiter) |
| Ktor | 3.6.1 (CIO + WebSockets) |
| Node / npm | 24.14 / incluido en Node |
| Cliente web | React 19.3, TypeScript 5.9, Vite 8.3 |
| E2E | Playwright 1.64 (Chromium ya instalado en la máquina) |

Falta para la Fase 4: `local.properties` con `sdk.dir=C:/AndroidSdk` (con barras normales; con
`C\:\\AndroidSdk` AGP rechaza la ruta).

**Ojo con AGP 9:** trae Kotlin embebido (KGP 2.2.10). Si se aplica `org.jetbrains.kotlin.android` —o
se deja KGP en el classpath raíz con `apply false`— AGP falla con `BaseVariant` (removido en AGP 9).
Regla del repo: los módulos JVM usan Kotlin 2.2.10 y `:app` solo aplica `com.android.application` +
`org.jetbrains.kotlin.plugin.compose` (2.2.10). Si algún día se sube Kotlin, hay que subir el AGP que
lo embeba.

- `minSdk 26` (API 26 = Android 8.0) cubre ~97% de dispositivos y habilita `startLocalOnlyHotspot`.
- `targetSdk` 36 (Android 16).
- Emulador **no** sirve para probar LAN/hotspot: hacen falta al menos 2 teléfonos reales
  (uno gama baja) y un router.
- Matriz mínima de navegadores invitados: Chrome, Samsung Internet, Firefox, y Safari (iOS) si se
  quiere invitar a alguien con iPhone — **verificar `ws://` y `localStorage` en iOS**, es el caso más
  frágil del plan. En la Fase 3 se probó en Chromium (emulado Pixel 7); el próximo paso es probarlo en
  el teléfono real con la PC como servidor.

### Lo que hay que evitar en el cliente web

En una LAN por `http://` **no hay contexto seguro**. Por eso:

- nada de Service Worker, `crypto.randomUUID` sin fallback, Clipboard ni geolocalización;
- el manifest de PWA no se usa (no habría instalación offline de todos modos);
- la app **no** depende de HTTPS: `ws://` plano es suficiente porque la red es de confianza.
