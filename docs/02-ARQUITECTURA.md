# 02 — Arquitectura

## 1. Vista de componentes

```
┌─────────────────────────── Teléfono anfitrión (Android) ───────────────────────────┐
│                                                                                    │
│  ┌────────────────┐   ┌──────────────────────────────┐   ┌──────────────────────┐  │
│  │ :app (Compose) │   │ :server (Ktor CIDR)          │   │ :engine (Kotlin puro)│  │
│  │ Lobby          │──►│  GET /            → assets    │──►│ aplicar(estado,      │  │
│  │ Ajustes        │   │  GET /api/info    → versión   │   │   jugador, acción)   │  │
│  │ QR + URL       │   │  GET /qr.svg                  │   │ Reglas + azar        │  │
│  │ Permisos       │   │  WS  /ws          → partida   │   │ inyectable (seed)    │  │
│  │ WebView mesa   │   │  RoomManager + Actor/partida  │   └──────────────────────┘  │
│  └────────────────┘   └──────────────┬───────────────┘                             │
│         │                            │                                             │
│  ForegroundService (server+wakelocks)│  assets/web/ (build del cliente TS)          │
└──────────────────────────────────────┼─────────────────────────────────────────────┘
                                       │  ws://192.168.x.x:8080/ws
             ┌─────────────────────────┼──────────────────────────┐
             │                         │                          │
      ┌──────▼─────┐            ┌──────▼─────┐             ┌──────▼─────┐
      │ Navegador  │            │ Navegador  │             │ Navegador  │
      │ jugador 2  │            │ jugador 3  │             │ jugador 4-6│
      └────────────┘            └────────────┘             └────────────┘
```

## 2. Módulos

| Módulo | Responsabilidad | Depende de |
|--------|-----------------|------------|
| `:engine` | Reglas, validación de acciones, puntajes. Sin Android, sin red, sin corrutinas | stdlib + kotlinx.serialization |
| `:protocol` | DTOs del protocolo (`@Serializable`), versión, códigos de error | kotlinx.serialization |
| `:server` | Ktor: estáticos, `/ws`, `Sala` (lobby + partida), timeouts, reconexión | `:engine`, `:protocol` |
| `:app` | Compose: lobby, config, QR, permisos, `ServerService`, WebView | `:server` |
| `web/` (npm) | Cliente TypeScript/Vite: mesa, cartas, cantos, marcador | `:protocol` (tipos) |

## 3. Concurrencia y consistencia

- **Sección crítica única por sala**: `Mutex` en `Sala`; toda mutación del estado del motor pasa por
  ahí, así que el orden de las acciones es total y no hay carreras (el "actor" es lógico, sin un
  canal aparte: mismo efecto, menos código).
- **Nunca se envía dentro del lock**: cada sesión tiene su `Channel` de salida y un loop que escribe
  el WebSocket. Si un cliente es lento, solo se llena *su* cola.
- Cada acción lleva un `id` (uuid v4 del cliente) en el envelope; el servidor recuerda las últimas 200
  y descarta duplicados (reintentos por reconexión) — idempotencia.
- Toda mutación produce `(nuevoEstado, eventos)`; el servidor **emite el estado completo** filtrado
  por jugador + los eventos nuevos. Payload chico (< 3 KB) → no hay deltas.
- Timeouts con `delay()` cancelable por partida; en tests se usan timeouts de 250–300 ms reales y
  `testApplication` (sin reloj virtual, para no pelear con la red).

## 4. Red: cómo se encuentran los dispositivos

Orden de preferencia (el lobby intenta en este orden y muestra el resultado):

1. **Wi-Fi compartida existente** (router/hotspot de un tercero). La app detecta la IP del
   teléfono (`ConnectivityManager.getLinkProperties(...).linkAddresses`) y muestra
   `http://<ip>:8080` + QR. Camino principal, máximo alcance entre dispositivos.
2. **Hotspot local del anfitrión** (`WifiManager.startLocalOnlyHotspot`, API 26+). La app obtiene
   SSID + password del hotspot y los muestra junto al QR (`WIFI:T:WPA;S:<ssid>;P:<pass>;;` — Android
   y iOS interpretan ese formato al escanear con la cámara). Requiere permiso
   `NEARBY_WIFI_DEVICES` (Android 13+) y verificación por OEM (**spike R2**).
3. **mDNS/NSD** (`_truco._tcp`): publicar el servicio para futuros clientes nativos y para
   diagnóstico. No aporta a los navegadores; opcional, no bloquea el MVP.

Restricciones a respetar:
- `http://` + `ws://` (no TLS): sin problema de mixed content, y no necesitamos APIs que exijan
  contexto seguro. Por eso **no** nos apoyamos en Service Workers/PWA instalable.
- No se expone nada fuera de la LAN. Sin datos personales: los nombres son lo único que se pide.

## 5. Ciclo de vida del servidor en Android

```
Usuario toca "Crear partida"
  → verifica permiso de notificaciones (13+) y Wi-Fi
  → arranca ServerService (foregroundServiceType=specialUse | dataSync)   ← R1
  → adquiere WifiLock(WIFI_MODE_FULL_HIGH_PERF) + PARTIAL_WAKE_LOCK
  → crea RoomManager y el servidor Ktor en 0.0.0.0:8080 (puerto alternativo si está ocupado)
  → publica notificación persistente: "Partida abierta — 3/6 jugadores — Detener"
  → muestra lobby con QR

Usuario toca "Detener" / última persona sale / partida termina sin revancha
  → cierra el WebSocket server, libera locks, detiene el servicio
```

Detalles obligatorios (implementados en `app/src/main/kotlin/ar/truco/app/servidor/`):
- Servicio con `START_NOT_STICKY`: si el proceso muere no se reinicia a ciegas (no sabríamos con qué
  configuración); el usuario ve que la notificación desapareció.
- `foregroundServiceType="specialUse"` + `<property android:name="android.app.PROPERTY_SPECIAL_USE_FGS_SUBTYPE">`.
- `WifiLock(WIFI_MODE_FULL_HIGH_PERF)` + `PARTIAL_WAKE_LOCK` (se liberan en `onDestroy`).
- Notificación persistente con `http://ip:puerto · n/max` y acción “Terminar”.
- La mesa del anfitrión es un WebView a `http://127.0.0.1:8080` con el mismo cliente web (D-02), con
  `FLAG_KEEP_SCREEN_ON` mientras se juega; el botón atrás vuelve a la pantalla del servidor sin
  cortar la partida.
- El cliente web viaja como recursos Java del APK bajo `web/`; el servidor lo sirve con el mismo
  `staticResources` que en la PC (en Android no hay `File` de assets).

Verificado en emulador (API 37): pantalla apagada 45 s con el servidor respondiendo, hotspot
`startLocalOnlyHotspot` funcionando y `Terminar` cerrando el servidor.

## 6. Reconexión y abandono

- Al entrar, el cliente recibe un `playerToken` (uuid) y lo guarda en `localStorage`.
- Al reconectar, envía `rejoin{playerToken}` → recupera su silla y su mano actuales.
- Silla desconectada: `conectado=false`; la partida **pausa** el turno de ese jugador hasta
  `timeoutTurnoMs`, luego juega automáticamente (carta más baja / irse al mazo) para no trabar.
- Abandono explícito o 3 timeouts consecutivos → el jugador pasa a "ausente"; si es equipo,
  el compañero sigue jugando solo (regla "mano a mano"). Configurable, y documentado al usuario.
- Si se va el anfitrión → se cierra la sala. Aviso en pantalla a todos; v2 podría migrar el host
  (fuera de alcance).

## 7. Estrategia de datos

- Estado de partida **en memoria** (es efímero, dura una partida).
- `RulesConfig` y preferencias (nombre, sonido, tema) en **DataStore**.
- No hay base de datos, no hay telemetría, no hay red externa. Cero permisos de contactos/ubicación
  (ojo: el SSID puede requerir ubicación en versiones viejas → solo si se usa hotspot en API < 33).
