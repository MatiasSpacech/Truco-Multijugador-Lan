package ar.truco.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Lo que se elige antes de abrir la mesa. */
data class ConfigMesa(
    val jugadores: Int = 4,
    val objetivo: Int = 15,
    val flor: Boolean = false,
)

@Composable
fun PantallaLobby(
    config: ConfigMesa,
    alCambiar: (ConfigMesa) -> Unit,
    alCrear: () -> Unit,
    error: String?,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "Truco",
            fontSize = 40.sp,
            fontWeight = FontWeight.Bold,
            color = Colores.Dorado,
        )
        Text(
            text = "Argentino · multijugador en tu red",
            fontSize = 14.sp,
            color = Colores.Crema.copy(alpha = 0.75f),
            textAlign = TextAlign.Center,
        )

        Spacer(Modifier.height(28.dp))

        Tarjeta {
            Text("Jugadores", fontWeight = FontWeight.SemiBold)
            Text(
                "Los compañeros juegan enfrentados",
                fontSize = 12.sp,
                color = Colores.Crema.copy(alpha = 0.6f),
            )
            Fila {
                listOf(2, 4, 6).forEach { cantidad ->
                    Opcion(
                        texto = "${cantidad / 2} vs ${cantidad / 2}",
                        elegida = config.jugadores == cantidad,
                        alTocar = { alCambiar(config.copy(jugadores = cantidad)) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            Spacer(Modifier.height(18.dp))

            Text("Partido a", fontWeight = FontWeight.SemiBold)
            Fila {
                listOf(15, 30).forEach { puntos ->
                    Opcion(
                        texto = "$puntos puntos",
                        elegida = config.objetivo == puntos,
                        alTocar = { alCambiar(config.copy(objetivo = puntos)) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            Spacer(Modifier.height(18.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column {
                    Text("Con flor", fontWeight = FontWeight.SemiBold)
                    Text(
                        "Flor, contraflor y sin envido en esa mano",
                        fontSize = 12.sp,
                        color = Colores.Crema.copy(alpha = 0.6f),
                    )
                }
                Switch(checked = config.flor, onCheckedChange = { alCambiar(config.copy(flor = it)) })
            }
        }

        Spacer(Modifier.height(16.dp))

        Tarjeta(tono = Colores.Fieltro) {
            Text("Cómo se juega", fontWeight = FontWeight.SemiBold)
            Text(
                "Este teléfono es el servidor. Los demás se conectan desde el navegador " +
                    "escaneando un QR. No hace falta internet ni instalar nada.",
                fontSize = 13.sp,
                color = Colores.Crema.copy(alpha = 0.8f),
            )
            Text(
                "Después de crear la mesa, dejá la app abierta (podés bloquear el teléfono): " +
                    "el servidor sigue funcionando.",
                fontSize = 13.sp,
                color = Colores.Crema.copy(alpha = 0.8f),
            )
        }

        if (error != null) {
            Spacer(Modifier.height(14.dp))
            Text(error, color = MaterialTheme.colorScheme.error, fontSize = 13.sp, textAlign = TextAlign.Center)
        }

        Spacer(Modifier.height(24.dp))

        Button(
            onClick = alCrear,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Colores.Dorado, contentColor = Colores.Fondo),
            shape = RoundedCornerShape(14.dp),
        ) {
            Text("Crear mesa", fontSize = 17.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun Tarjeta(
    tono: androidx.compose.ui.graphics.Color = Colores.FondoSecundario,
    contenido: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(tono, RoundedCornerShape(18.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        content = contenido,
    )
}

@Composable
private fun Fila(contenido: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        content = contenido,
    )
}

@Composable
private fun Opcion(
    texto: String,
    elegida: Boolean,
    alTocar: () -> Unit,
    modifier: Modifier = Modifier,
) {
    FilterChip(
        selected = elegida,
        onClick = alTocar,
        label = { Text(texto, fontSize = 13.sp) },
        modifier = modifier,
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = Colores.Dorado,
            selectedLabelColor = Colores.Fondo,
        ),
    )
}

@Composable
fun Cargando() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text("Abriendo la mesa…", color = Colores.Crema.copy(alpha = 0.8f))
    }
}

@Composable
fun Punto(tono: androidx.compose.ui.graphics.Color) {
    Box(
        Modifier
            .size(8.dp)
            .background(tono, RoundedCornerShape(50)),
    )
}
