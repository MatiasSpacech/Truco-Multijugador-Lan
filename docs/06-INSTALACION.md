# 06 — Instalación y uso (para quien juega)

Truco Argentino es una app Android que **hace de servidor** y de mesa. Los demás jugadores entran
desde el navegador del teléfono: no instalan nada, no hace falta internet ni cuentas.

```
┌──────────────┐   Wi-Fi / hotspot   ┌───────────────┐
│  App Android │◄──── ws://ip:8080 ─►│  Navegador    │
│  (anfitrión) │                     │  jugador 2    │
└──────────────┘                     └───────────────┘
```

## 1. Requisitos

- **Teléfono anfitrión**: Android 8.0 o superior (API 26+). La app pesa ~2,2 MB.
- **Jugadores**: cualquier teléfono con navegador moderno (Chrome, Safari, Firefox, Samsung
  Internet). Todos en la **misma red Wi-Fi** que el anfitrión (o en la zona Wi-Fi que crea la app).
- **Sin internet**: el juego no sale de la red local. No hay cuentas, ni publicidad, ni telemetría.

## 2. Instalar el APK

1. Pasá el archivo `app-release.apk` al teléfono (cable, Bluetooth, Telegram, lo que sea) o
   descargalo desde donde lo compartas.
2. Abrilo desde el explorador de archivos. Android va a pedir permiso para **instalar apps
   desconocidas** para esa app (Archivos / Chrome): aceptá una vez.
3. Si Play Protect muestra un aviso por ser una app fuera de la tienda, elegí “Instalar de todos
   modos”. El APK está firmado, pero no pasó por la revisión de Google.
4. Abrí **Truco Argentino**.

> **Actualizar**: las versiones nuevas se instalan encima de la anterior **solo si están firmadas con
> la misma clave** (es lo normal si las compilás vos). Si instalaste un APK de otra persona/firma,
> Android va a pedir desinstalar primero (se pierden las preferencias del cliente web).

## 3. Crear la mesa (anfitrión)

1. Elegí **jugadores** (1 vs 1, 2 vs 2 o 3 vs 3), **partido a** 15 o 30 (30 incluye malas y buenas) y
   si juegan **con flor**.
2. Tocá **Crear mesa**. Android pide permiso de notificaciones: aceptá (es la notificación fija del
   servidor, con el contador de jugadores y el botón “Terminar”).
3. La pantalla muestra la **dirección para entrar** (`http://192.168.x.x:8080`) y un **QR**.
   Podés dejar el teléfono bloqueado: el servidor sigue vivo (servicio en primer plano + Wi-Fi lock).

## 4. Entrar desde el navegador (invitados)

1. Escaneá el QR o escribí la dirección en el navegador.
2. Poné tu nombre → **Entrar a la mesa**.
3. Cuando estén todos, cada uno toca **Estoy listo**. La partida arranca sola.
4. La primera vez aparece un **tutorial de 3 pasos**; se puede saltear y repetir desde Ajustes.

**Jugar una carta son dos toques**: el primero la levanta, el segundo la juega. Los cantos que
aparecen abajo son solo los que podés hacer en ese momento.

## 5. Jugar en el teléfono anfitrión

En la pantalla del servidor, **Jugar en este teléfono** abre la mesa en la app (un WebView contra
`127.0.0.1`). Es la misma interfaz que ven los invitados. El botón atrás vuelve a la pantalla del
servidor sin cerrar la mesa.

## 6. Sin router: zona Wi-Fi propia

En la pantalla del servidor, **Zona Wi-Fi propia → Crear** levanta un hotspot del teléfono y muestra
SSID y clave. Los invitados se conectan a esa red y entran por la dirección nueva.

> Ojo: crear la zona **cambia la IP** del servidor. Si ya había jugadores conectados, tienen que
> volver a entrar por la dirección nueva (el QR se actualiza solo en la pantalla del anfitrión).

## 7. Problemas comunes

| Síntoma | Qué hacer |
|---------|-----------|
| El navegador no abre la dirección | Verificá que estén en la misma red. En redes de invitados/empresa el **aislamiento de clientes (AP isolation)** bloquea la conexión: usá la zona Wi-Fi de la app o un router propio. |
| “Sala llena” | La mesa ya tiene todos los asientos ocupados (los que se desconectan conservan su silla unos minutos). |
| “Partida en curso” | Ya se está jugando una mano: esperá a que termine o pedile al anfitrión que termine la mesa. |
| Se cortó y no vuelve | Recargá la página: el cliente guarda el token de la silla y te devuelve tu mano. |
| La pantalla del anfitrión se apaga y “no pasa nada” | Es lo esperado: el servidor sigue funcionando con la pantalla apagada (probalo, la partida no se corta). |
| El puerto 8080 está ocupado | La app elige el siguiente libre (8081, 8082…) y lo muestra en pantalla. |
| Un invitado quedó “ausente” | Se desconectó dos veces: la partida sigue sin él. Al recargar, recupera su silla. |
| “Actualización no instalada” | El APK nuevo tiene otra firma. Desinstalá y volvé a instalar. |

## 8. Para quien compila el APK

Requisitos: JDK 17+ (probado con 21) y Android SDK con `platforms;android-37`.

```bash
# 1) cliente web (una vez, o cuando cambie el front)
cd web && npm install && npm run build && cd ..

# 2) firma: keystore local (no se commitea)
keytool -genkeypair -keystore truco-release.jks -alias truco \
  -keyalg RSA -keysize 4096 -validity 10950 \
  -dname "CN=Truco Argentino, O=Truco Argentino, C=AR"
cp keystore.properties.example keystore.properties   # y poné las claves reales

# 3) APK firmada con R8
./gradlew :app:assembleRelease
# → app/build/outputs/apk/release/app-release.apk
```

- Sin `keystore.properties` el release sale **sin firmar** (solo para probar R8). El build avisa.
- `keystore.properties` y `*.jks` están en `.gitignore`. **Guardá el keystore y la clave**: sin ellos
  no se pueden publicar actualizaciones (Android exige la misma firma).
- R8 + `shrinkResources` están activados. Las reglas que importan (kotlinx.serialization y Ktor) están
  en `app/proguard-rules.pro`; si agregás DTOs nuevos, el release se prueba de punta a punta antes de
  distribuirlo (ver abajo).
- `versionCode` se sube en `app/build.gradle.kts` con cada APK que se distribuya; `versionName` y
  `serverVersion` (en `TrucoServer.kt` y `ServicioServidor.kt`) van juntos.
- AAB (`bundleRelease`) no se usa: la distribución es APK directa (D-08).

### Verificación del release (lo que se hizo para 1.0.0)

En un emulador/dispositivo con el APK release instalado:

1. La app abre, se crea la mesa y responde `curl http://127.0.0.1:8080/api/info` (por `adb forward`).
2. La notificación del servicio en primer plano aparece (“Mesa abierta”, acción “Terminar”).
3. 3 navegadores entran, el anfitrión juega desde el WebView y se juega una mano completa: reparto,
   privacidad de cartas, bazas, cierre de mano y reparto siguiente. Si R8 se llevara los
   `$serializer`, este paso falla aunque el HTTP responda.

## 9. Privacidad

No se recolecta nada: ni cuentas, ni analytics, ni publicidad, ni conexiones a internet. Todo el
estado de la partida vive en memoria en el teléfono anfitrión y desaparece al terminar la mesa. El
“log de diagnóstico” que se puede descargar desde Ajustes queda en el teléfono del jugador y solo se
comparte si él lo decide.
