package local.museumslideshow.sync

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapRegionDecoder
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import local.museumslideshow.Registro
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Copia para la pantalla: entra en 3840×2160 sin agrandar ni deformar, JPEG calidad 95, hecha por franjas
 * (nunca hay en memoria más que el destino, ~33 MB, y una franja). Más una copia chica de 64 px para el fondo.
 */
class ProcesadorImagenAndroid : ProcesadorImagen {

    override fun procesar(origen: File, destino: File, destinoChica: File): Medidas? {
        val opt = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(origen.path, opt)
        val w = opt.outWidth
        val h = opt.outHeight
        if (w <= 0 || h <= 0) return null

        val escala = min(1.0, min(MAX_ANCHO.toDouble() / w, MAX_ALTO.toDouble() / h))
        val dw = max(1, (w * escala).roundToInt())
        val dh = max(1, (h * escala).roundToInt())

        var resultado: Bitmap? = null
        try {
            val salida = Bitmap.createBitmap(dw, dh, Bitmap.Config.ARGB_8888)
            resultado = salida
            salida.eraseColor(Color.BLACK)
            val ok = porFranjas(origen, w, h, salida) || planB(origen, w, h, salida)
            if (!ok) return null

            FileOutputStream(destino).use { if (!salida.compress(Bitmap.CompressFormat.JPEG, CALIDAD, it)) return null }
            guardarChica(salida, destinoChica)
            return Medidas(dw, dh)
        } catch (e: OutOfMemoryError) {
            Log.w(Registro.TAG, "Sin memoria al procesar una imagen")
            return null
        } catch (e: Exception) {
            Log.w(Registro.TAG, "No se pudo procesar una imagen (${e.javaClass.simpleName})")
            return null
        } finally {
            resultado?.recycle()
        }
    }

    /** Franjas horizontales decodificadas con el inSampleSize más grande que no baje del tamaño final. */
    private fun porFranjas(origen: File, w: Int, h: Int, destino: Bitmap): Boolean {
        var decoder: BitmapRegionDecoder? = null
        try {
            @Suppress("DEPRECATION")
            decoder = BitmapRegionDecoder.newInstance(origen.path, false) ?: return false
            val dw = destino.width
            val dh = destino.height
            var muestra = 1
            while (w / (muestra * 2) >= dw && h / (muestra * 2) >= dh) muestra *= 2

            val alturaFranja = FRANJA_FILAS * muestra
            val canvas = Canvas(destino)
            val pincel = Paint(Paint.FILTER_BITMAP_FLAG)
            var y0 = 0
            while (y0 < h) {
                val y1 = min(h, y0 + alturaFranja)
                val opt = BitmapFactory.Options().apply { inSampleSize = muestra }
                val franja = decoder.decodeRegion(Rect(0, y0, w, y1), opt) ?: return false
                try {
                    val arriba = (y0.toLong() * dh / h).toInt()
                    val abajo = if (y1 >= h) dh else (y1.toLong() * dh / h).toInt()
                    canvas.drawBitmap(franja, null, Rect(0, arriba, dw, max(abajo, arriba + 1)), pincel)
                } finally {
                    franja.recycle()
                }
                y0 = y1
            }
            return true
        } catch (e: Exception) {
            Log.w(Registro.TAG, "Falló el decodificador por franjas (${e.javaClass.simpleName}); pruebo el plan B")
            destino.eraseColor(Color.BLACK)
            return false
        } catch (e: OutOfMemoryError) {
            Log.w(Registro.TAG, "Sin memoria en el decodificador por franjas; pruebo el plan B")
            destino.eraseColor(Color.BLACK)
            return false
        } finally {
            decoder?.recycle()
        }
    }

    /** decodeFile con un inSampleSize tal que el resultado no pase el tamaño final. */
    private fun planB(origen: File, w: Int, h: Int, destino: Bitmap): Boolean {
        var muestra = 1
        while (w / muestra > destino.width || h / muestra > destino.height) muestra *= 2
        val opt = BitmapFactory.Options().apply { inSampleSize = muestra }
        val bmp = try {
            BitmapFactory.decodeFile(origen.path, opt)
        } catch (e: OutOfMemoryError) {
            null
        } ?: return false
        try {
            Canvas(destino).drawBitmap(bmp, null, Rect(0, 0, destino.width, destino.height), Paint(Paint.FILTER_BITMAP_FLAG))
            return true
        } finally {
            bmp.recycle()
        }
    }

    /** Lado largo de 64 px, bajando de a mitades para que el reescalado no deje dientes. */
    private fun guardarChica(origen: Bitmap, destino: File) {
        val lado = max(origen.width, origen.height)
        val factor = min(1.0, LADO_CHICA.toDouble() / lado)
        val cw = max(1, (origen.width * factor).roundToInt())
        val ch = max(1, (origen.height * factor).roundToInt())
        var actual = origen
        try {
            while (actual.width / 2 >= cw * 2 && actual.height / 2 >= ch * 2) {
                val mitad = Bitmap.createScaledBitmap(actual, actual.width / 2, actual.height / 2, true)
                if (actual !== origen) actual.recycle()
                actual = mitad
            }
            val chica = Bitmap.createScaledBitmap(actual, cw, ch, true)
            try {
                FileOutputStream(destino).use { chica.compress(Bitmap.CompressFormat.JPEG, 80, it) }
            } finally {
                if (chica !== actual && chica !== origen) chica.recycle()
            }
        } finally {
            if (actual !== origen) actual.recycle()
        }
    }

    companion object {
        const val MAX_ANCHO = 3840
        const val MAX_ALTO = 2160
        private const val CALIDAD = 95
        private const val LADO_CHICA = 64
        private const val FRANJA_FILAS = 1024
    }
}
