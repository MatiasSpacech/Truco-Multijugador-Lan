package ar.truco.app.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

object Colores {
    val Fondo = Color(0xFF0F2A20)
    val FondoSecundario = Color(0xFF173A2C)
    val Fieltro = Color(0xFF1D4A36)
    val Crema = Color(0xFFF4ECD8)
    val Dorado = Color(0xFFD9B36A)
    val Rojo = Color(0xFFC8553D)
    val Azul = Color(0xFF5B8FB9)
}

private val Esquema = darkColorScheme(
    primary = Colores.Dorado,
    onPrimary = Color(0xFF20180A),
    primaryContainer = Color(0xFF4A3A1C),
    onPrimaryContainer = Colores.Crema,
    secondary = Colores.Azul,
    background = Colores.Fondo,
    onBackground = Colores.Crema,
    surface = Colores.FondoSecundario,
    onSurface = Colores.Crema,
    surfaceVariant = Color(0xFF1F4A38),
    onSurfaceVariant = Color(0xFFCFE3D6),
    error = Colores.Rojo,
    onError = Color.White,
)

@Composable
fun TemaTruco(contenido: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Esquema) {
        Surface(color = Colores.Fondo, content = contenido)
    }
}
