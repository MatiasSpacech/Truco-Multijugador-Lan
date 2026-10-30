# 00 — Plan del proyecto

## 1. Objetivo

Una app Android que permita jugar al Truco Argentino entre 2, 4 o 6 personas en la misma red local,
sin internet, sin cuentas y sin que los invitados instalen nada: el anfitrión inicia el servidor y
comparte un enlace/QR; los demás abren el navegador de su teléfono y juegan.

## 2. Principios de diseño

1. **Servidor autoritativo.** Toda la verdad del juego vive en el servidor del anfitrión. Los
   clientes son "terminales tontas": dibujan estado y envían intenciones. Reglas anti-trampa gratis,
   porque las cartas ajenas nunca se envían al dispositivo equivocado.
2. **Sin dependencias de internet.** Todos los assets (JS, CSS, fuentes, sonidos) van empaquetados
   en el APK y se sirven desde el servidor local. Prohibido CDN.
3. **Una sola UI.** La mesa del anfitrión usa el mismo cliente web (en WebView) que los invitados;
   el código nativo se limita a lobby, configuración, permisos y ciclo de vida del servidor.
4. **Motor puro y testeable.** Las reglas no conocen Android, ni red, ni UI: función determinista
   sobre estado + acción, con RNG inyectable por semilla (tests reproducibles y, a futuro, replays).
5. **Tolerante a desconexiones.** El teléfono del invitado se bloquea, el Wi-Fi parpadea, alguien
   cierra el navegador: la silla se reserva y se puede volver a entrar con un token.

## 3. Alcance

**Decisiones cerradas:** Kotlin + Ktor embebido (D-01), mesa del host en WebView (D-02), 6 jugadores =
2 equipos de 3 (D-05), distribución por APK directa (D-08). Ver docs/03-STACK.md.

### MVP (jugable de punta a punta)
- 2 y 4 jugadores (individual y 2 vs 2), partido a 15.
- Truco / Retruco / Vale Cuatro, Envido / Real Envido / Falta Envido, Irse al mazo.
- Salón de espera, reparto, 3 bazas, conteo de puntos, rotación de mano.
- QR + URL para unirse; servidor en primer plano; pantalla de reconexión.
- Cliente web mobile-first: mesa, cartas, cantos, marcador, log de la mano.

### v1 (completo)
- 6 jugadores (2 equipos de 3), partido a 30 (malas y buenas).
- Flor / contraflor / contraflor al resto (configurable, también "sin flor").
- Reincorporación de jugadores y manejo de abandono.
- Timeouts de canto configurables, auto-resolución ("no quiero" automático).
- Variantes de reglas en la configuración de partida.

### Fuera de alcance (explícito)
- Internet / servidor remoto / matchmaking / cuentas / ranking online.
- Truco a 3 o 5 jugadores, "truco podrido", "truco mendocino", "truco uruguayo" (se evalúan como
  plugins de reglas recién después del MVP).
- Chat de voz, espectadores, torneos, monetización.

## 4. Riesgos y mitigaciones

| # | Riesgo | Impacto | Mitigación |
|---|--------|---------|------------|
| R1 | Android 14+ exige `foregroundServiceType` declarado | Alto | Distribución por APK (D-08) evita la revisión de Play; el tipo se elige según el dispositivo (`specialUse` como fallback). Prototipo en Fase 4 |
| R2 | `LocalOnlyHotspot` con comportamiento variable por OEM/versión y permisos cambiantes (`NEARBY_WIFI_DEVICES`) | Alto | Hotspot = opcional *best effort*; camino principal: router Wi-Fi existente + QR. Spike de 1 día en 2-3 dispositivos |
| R3 | Aislamiento de clientes (AP isolation) en routers de ISP | Medio | Detección: si un cliente no llega, mostrar guía ("desactivar aislamiento / usar hotspot") |
| R4 | Doze / ahorro de batería mata el servidor | Alto | Foreground service + `WifiLock` + `WakeLock` parcial + opción "mantener pantalla encendida" |
| R5 | WebView vs navegador real: diferencias de render/teclado | Medio | Probar el cliente en Chrome/Samsung Internet/Firefox y en WebView desde Fase 3 |
| R6 | Divergencia entre reglas reales y las implementadas (la trampa clásica del truco) | Alto | Doc 01 con tablas exactas + suite de tests con casos "de mesa" + config de variantes |
| R7 | Complejidad del envido/flor en 4 y 6 jugadores (quién responde) | Medio | Empezar 1v1, generalizar por equipos, marcar variantes ambiguas como configurables |
| R8 | El anfitrión se va / cierra la app → partida perdida | Medio | Aviso claro + bloqueo de salida durante partida + (v2) migración de host |

## 5. Definición de terminado (por feature)

- Reglas cubiertas con tests automáticos (incluida la variante configurada).
- Sin dependencias de red externa.
- Probado en al menos 2 dispositivos reales + 1 WebView.
- Reconexión probada (bloquear pantalla, matar pestaña, cambiar de Wi-Fi).
- Textos en español rioplatense, sin jerga de programador a la vista.

## 6. Métricas de éxito (informales)

- Un grupo de 4 personas completa una partida a 15 sin instrucciones previas.
- Unirse toma menos de 15 segundos desde que el anfitrión muestra el QR.
- Cero "trampas" posibles: ningún cliente recibe cartas ajenas (verificable en el protocolo).
