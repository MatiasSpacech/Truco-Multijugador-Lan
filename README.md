# Truco Argentino — Multijugador LAN

Juego de **Truco Argentino** para Android. La app anfitriona levanta un servidor HTTP + WebSocket
dentro del teléfono; los demás jugadores se conectan **desde el navegador** de sus propios móviles
(no instalan nada). 2, 4 o 6 jugadores, sin internet, sin cuentas.

```
┌──────────────┐   Wi-Fi / hotspot   ┌───────────────┐
│  App Android │◄──── ws://ip:8080 ─►│  Navegador    │
│  (host)      │◄───────────────────►│  jugador 2    │
│  servidor    │◄───────────────────►│  jugador 3    │
│  + mesa      │◄───────────────────►│  jugador 4-6  │
└──────────────┘                     └───────────────┘
```

## Documentación

| Doc | Contenido |
|-----|-----------|
| [docs/00-PLAN.md](docs/00-PLAN.md) | Objetivo, alcance, MVP, riesgos, definición de terminado |
| [docs/01-REGLAS.md](docs/01-REGLAS.md) | Reglas del juego, variantes y modelo de dominio |
| [docs/02-ARQUITECTURA.md](docs/02-ARQUITECTURA.md) | Componentes, red, ciclo de vida Android, reconexión |
| [docs/03-STACK.md](docs/03-STACK.md) | Tecnologías recomendadas, alternativas y ADRs |
| [docs/04-PROTOCOLO.md](docs/04-PROTOCOLO.md) | Mensajes WebSocket, secuencias, errores |
| [docs/05-ROADMAP.md](docs/05-ROADMAP.md) | Fases, entregables y criterios de aceptación |

## Decisiones tomadas

| # | Decisión | Estado |
|---|----------|--------|
| D-01 | **Kotlin + Ktor** embebido (CIO) para el host | ✅ confirmado |
| D-02 | Mesa del anfitrión en **WebView** contra `127.0.0.1` (una sola UI) | ✅ confirmado |
| D-05 | 6 jugadores = **2 equipos de 3** (compañeros alternados 0-2-4 vs 1-3-5) | ✅ confirmado |
| D-08 | Distribución por **APK directa** (sin Play Store) | ✅ confirmado |

## Estado

- [x] Fase 0 — Planificación y decisiones documentadas
- [x] Fase 1 — Motor de reglas (Kotlin puro, 43 tests, 480 partidas aleatorias)
- [ ] Fase 2 — Servidor y protocolo
- [ ] Fase 3 — Cliente web + app anfitriona
- [ ] Fase 4 — Servicio en primer plano, QR, hotspot
- [ ] Fase 5 — 4/6 jugadores, reconexión, timeouts, contraflor
- [ ] Fase 6 — Pulido (animaciones, sonido, accesibilidad)
- [ ] Fase 7 — Release (APK firmada)

## Cómo correr lo que hay hoy

```bash
./gradlew :engine:test        # 43 tests del motor de reglas
```
