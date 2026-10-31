# Changelog

Formato basado en [Keep a Changelog](https://keepachangelog.com/es-ES/1.1.0/). El proyecto usa
versionado semántico a partir de la 1.0.0; antes de eso cada fase fue un hito interno.

## [1.0.0] — 2026-10-31

Primera versión pública. APK firmada, R8 activado y verificada de punta a punta en emulador
(Android 17 / API 37) con 4 jugadores: 3 navegadores + el WebView del anfitrión.

### Juego

- Truco argentino para **2, 4 o 6 jugadores** con equipos alternados (0-2-4 vs 1-3-5 en 6).
- Envido completo: envido, real envido, falta envido, cadena de cantos y tabla exacta de “no quiero”.
- Truco, retruco y vale cuatro, con escalada y rechazo.
- **Flor y contraflor** (contraflor al resto), con el envido deshabilitado en esa mano.
- **Malas y buenas** a 30: al llegar a 15 se cierra la primera etapa y el marcador vuelve a cero.
- Pardas, cierre de mano, rotación de mano y partida a 15 o 30.
- Servidor autoritativo: el cliente nunca calcula reglas ni ve cartas ajenas.

### Red y sala

- Servidor Ktor (CIO) embebido en el teléfono anfitrión, HTTP + WebSocket en la LAN, sin internet.
- QR con la dirección, detección de IP, puerto libre automático y **zona Wi-Fi propia** (hotspot).
- Reconexión con token: recargar la página devuelve la silla y la mano.
- Abandono fino: se juega por el ausente a los 1,5 s y a la segunda desconexión queda marcado.
- Revancha sin rearmar la sala (la mesa vuelve al lobby).
- Servicio en primer plano con notificación persistente, `WifiLock` y `WakeLock`: la partida sigue
  con la pantalla apagada.

### Cliente web

- Mobile-first, sin dependencias de UI ni CDN: baraja española dibujada en SVG (espada, basto, oro,
  copa), abanico de cartas, bazas, tanteador y etapa.
- Dos toques para jugar una carta; solo se ofrecen las acciones legales que manda el servidor.
- **i18n español/inglés** (detecta el idioma del sistema, se cambia en Ajustes).
- **Tutorial de 3 pasos** la primera vez y **ayuda de reglas** desde la mesa.
- **Sonido y vibración** sintetizados (sin assets) y apagables por separado.
- **Tema claro/oscuro/automático** y `prefers-reduced-motion`.
- Accesibilidad: blancos de 44 px, foco visible y atrapado, Escape, `aria-live`, contraste.
- **Log de diagnóstico exportable** (250 mensajes + contexto de sesión) desde Ajustes.

### Release

- APK firmada (v2) de ~2,2 MB con R8 + `shrinkResources`.
- Reglas de ProGuard para kotlinx.serialization y Ktor (`app/proguard-rules.pro`).
- Guía de instalación y uso: [docs/06-INSTALACION.md](docs/06-INSTALACION.md).

### Tests

- Motor: 53 tests (incluye flor/contraflor, malas y buenas, y 480 partidas aleatorias).
- Servidor: 18 tests de integración por WebSocket (privacidad de cartas, timeouts, reconexión).
- Cliente: 15 tests unitarios con Vitest (i18n, preferencias, diagnóstico).
- E2E: 3 tests en Chromium real, cada uno con su propio servidor.

### Limitaciones conocidas

- Verificado en emulador; **falta la prueba en teléfonos reales** (batería, aislamiento de clientes,
  hotspot con jugadores ya conectados).
- El E2E cubre 2 jugadores; 4 y 6 están cubiertos por tests de motor/servidor, no por navegador.
- La app anfitriona está en español y sin interruptores de tema; el es/en y los temas son del cliente.
- En iPhone el sonido requiere el primer toque y no hay vibración (limitación de Safari).
