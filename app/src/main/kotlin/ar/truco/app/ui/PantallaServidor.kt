package ar.truco.app.ui

import android.content.ClipData
import android.content.ClipboardManager
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ar.truco.app.servidor.EstadoServidor

@Composable
fun PantallaServidor(
    estado: EstadoServidor.Snapshot,
    alAbrirMesa: () -> Unit,
    alTerminar: () -> Unit,
    alCrearHotspot: () -> Unit,
    alDetenerHotspot: () -> Unit,
) {
    val contexto = LocalContext.current
    val url = estado.url
    val qr = remember(url) { url?.let { qrDe(it) } }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Punto(if (estado.fase == EstadoServidor.Fase.ACTIVO) Colores.Dorado else Colores.Rojo)
            Text("Mesa abierta", fontSize = 24.sp, fontWeight = FontWeight.Bold)
        }
        Text(
            "Los jugadores entran desde el navegador de su teléfono",
            fontSize = 13.sp,
            color = Colores.Crema.copy(alpha = 0.7f),
            textAlign = TextAlign.Center,
        )

        Spacer(Modifier.height(18.dp))

        when (estado.fase) {
            EstadoServidor.Fase.INICIANDO -> {
                CircularProgressIndicator(color = Colores.Dorado)
                Spacer(Modifier.height(12.dp))
                Text("Abriendo el servidor…", color = Colores.Crema.copy(alpha = 0.8f))
            }

            EstadoServidor.Fase.ERROR -> {
                Tarjeta(tono = Color(0xFF3A1D18)) {
                    Text("No se pudo abrir la mesa", fontWeight = FontWeight.SemiBold)
                    Text(estado.error ?: "Error desconocido", fontSize = 13.sp)
                }
            }

            else -> {
                if (url != null) {
                    Tarjeta {
                        Text("Dirección para entrar", fontSize = 12.sp, color = Colores.Crema.copy(alpha = 0.6f))
                        SelectionContainer {
                            Text(
                                url,
                                fontSize = 19.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                color = Colores.Dorado,
                            )
                        }
                        TextButton(
                            onClick = {
                                val gestor = contexto.getSystemService(ClipboardManager::class.java)
                                gestor?.setPrimaryClip(ClipData.newPlainText("Truco", url))
                            },
                            modifier = Modifier.align(Alignment.Start),
                        ) {
                            Text("Copiar dirección")
                        }
                    }

                    Spacer(Modifier.height(14.dp))

                    if (qr != null) {
                        Column(
                            modifier = Modifier
                                .background(Color.White, RoundedCornerShape(16.dp))
                                .padding(14.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Image(
                                bitmap = qr,
                                contentDescription = "Código QR para entrar a la mesa",
                                modifier = Modifier.size(240.dp),
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Escaneá el QR con la cámara",
                            fontSize = 12.sp,
                            color = Colores.Crema.copy(alpha = 0.7f),
                        )
                    }
                } else {
                    Tarjeta(tono = Color(0xFF3A2D18)) {
                        Text("Sin Wi-Fi", fontWeight = FontWeight.SemiBold)
                        Text(
                            "Conectá este teléfono a una red Wi-Fi (o creá una zona) para que los demás puedan entrar.",
                            fontSize = 13.sp,
                        )
                    }
                }

                Spacer(Modifier.height(14.dp))

                Tarjeta(tono = Colores.Fieltro) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text("En la mesa", fontWeight = FontWeight.SemiBold)
                        Text(
                            "${estado.jugadores}/${estado.maxJugadores}",
                            fontWeight = FontWeight.Bold,
                            color = Colores.Dorado,
                        )
                    }
                    Text(
                        if (estado.partidaEnCurso) "Partida en curso" else "Esperando jugadores",
                        fontSize = 13.sp,
                        color = Colores.Crema.copy(alpha = 0.75f),
                    )
                }

                Spacer(Modifier.height(14.dp))

                Tarjeta {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("Zona Wi-Fi propia", fontWeight = FontWeight.SemiBold)
                            Text(
                                "Opcional, si no hay un router a mano",
                                fontSize = 12.sp,
                                color = Colores.Crema.copy(alpha = 0.6f),
                            )
                        }
                        TextButton(onClick = if (estado.hotspot == null) alCrearHotspot else alDetenerHotspot) {
                            Text(if (estado.hotspot == null) "Crear" else "Apagar")
                        }
                    }

                    estado.hotspot?.let { hotspot ->
                        Text(
                            "Red: ${hotspot.ssid}",
                            fontSize = 14.sp,
                            fontFamily = FontFamily.Monospace,
                            color = Colores.Dorado,
                        )
                        if (hotspot.clave.isNotBlank()) {
                            Text(
                                "Clave: ${hotspot.clave}",
                                fontSize = 14.sp,
                                fontFamily = FontFamily.Monospace,
                                color = Colores.Dorado,
                            )
                        }
                        Text(
                            "Los invitados se conectan a esa red y abren la misma dirección.",
                            fontSize = 12.sp,
                            color = Colores.Crema.copy(alpha = 0.7f),
                        )
                    }

                    estado.hotspotError?.let { mensaje ->
                        Text(mensaje, fontSize = 12.sp, color = Color(0xFFE3A08F))
                    }
                }
            }
        }

        Spacer(Modifier.height(20.dp))

        Button(
            onClick = alAbrirMesa,
            enabled = estado.fase == EstadoServidor.Fase.ACTIVO && url != null,
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Colores.Dorado, contentColor = Colores.Fondo),
            shape = RoundedCornerShape(14.dp),
        ) {
            Text("Jugar en este teléfono", fontSize = 16.sp, fontWeight = FontWeight.Bold)
        }

        Spacer(Modifier.height(10.dp))

        OutlinedButton(
            onClick = alTerminar,
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp),
            shape = RoundedCornerShape(14.dp),
        ) {
            Text("Terminar la mesa")
        }

        Spacer(Modifier.height(16.dp))

        Text(
            "Podés bloquear la pantalla: la mesa sigue abierta. Mientras tanto, los invitados " +
                "pueden entrar y salir sin perder su lugar.",
            fontSize = 12.sp,
            color = Colores.Crema.copy(alpha = 0.55f),
            textAlign = TextAlign.Center,
        )
    }
}
