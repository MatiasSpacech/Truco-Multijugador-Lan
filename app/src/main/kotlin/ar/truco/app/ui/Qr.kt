package ar.truco.app.ui

import android.graphics.Bitmap
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter

/**
 * QR de la URL de la mesa, generado en el teléfono con ZXing (sin servicios externos).
 * Devuelve null si el texto no se puede codificar (no debería pasar con una URL).
 */
fun qrDe(texto: String, lado: Int = 720): ImageBitmap? = runCatching {
    val matriz = QRCodeWriter().encode(
        texto,
        BarcodeFormat.QR_CODE,
        lado,
        lado,
        mapOf(EncodeHintType.MARGIN to 1),
    )
    val pixeles = IntArray(lado * lado)
    for (y in 0 until lado) {
        val fila = y * lado
        for (x in 0 until lado) {
            pixeles[fila + x] = if (matriz[x, y]) 0xFF000000.toInt() else 0xFFFFFFFF.toInt()
        }
    }
    Bitmap.createBitmap(pixeles, lado, lado, Bitmap.Config.ARGB_8888).asImageBitmap()
}.getOrNull()
