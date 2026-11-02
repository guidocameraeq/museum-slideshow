package local.museumslideshow.pantallas

import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import local.museumslideshow.catalogo.Catalogo
import local.museumslideshow.galeria.Decodificacion
import local.museumslideshow.sync.Sincronizacion

/**
 * Las tapas chicas de la pantalla Galerías (`tapas/<id>.jpg`, ~480 px). Un caché con tope en bytes (las tarjetas que se
 * ven más una fila): lo que sobra se suelta solo. Nunca se decodifica la imagen entera: primero las medidas, después
 * `inSampleSize` al tamaño en que se dibuja.
 */
object TapasGalerias {
    private const val TOPE_BYTES = 12 * 1024 * 1024

    private val cache = object : LruCache<String, ImageBitmap>(TOPE_BYTES) {
        override fun sizeOf(clave: String, valor: ImageBitmap): Int = valor.asAndroidBitmap().byteCount
    }

    private fun clave(galeriaId: String, ancho: Int, alto: Int) = "$galeriaId@${ancho}x$alto"

    fun enCache(galeriaId: String, ancho: Int, alto: Int): ImageBitmap? = cache.get(clave(galeriaId, ancho, alto))

    /** Abre la tapa (en un hilo de fondo) al tamaño pedido; null si la galería no tiene tapa o no se puede abrir. */
    suspend fun cargar(dirBase: java.io.File, galeriaId: String, ancho: Int, alto: Int): ImageBitmap? {
        enCache(galeriaId, ancho, alto)?.let { return it }
        val img = withContext(Dispatchers.IO) { abrir(Catalogo.archivoTapa(dirBase, galeriaId), ancho, alto) } ?: return null
        cache.put(clave(galeriaId, ancho, alto), img)
        return img
    }

    private fun abrir(f: java.io.File, ancho: Int, alto: Int): ImageBitmap? {
        if (!f.isFile) return null
        return try {
            val medidas = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(f.absolutePath, medidas)
            if (medidas.outWidth <= 0 || medidas.outHeight <= 0) return null
            val m = Decodificacion.muestreo(medidas.outWidth, medidas.outHeight, ancho, alto)
            BitmapFactory.decodeFile(f.absolutePath, BitmapFactory.Options().apply { inSampleSize = m })?.asImageBitmap()
        } catch (e: Exception) {
            null
        } catch (e: OutOfMemoryError) {
            null
        }
    }
}

/** La tapa de una galería al tamaño `ancho` x `alto` (px de 1920x1080). null mientras carga o si no tiene. */
@Composable
fun rememberTapaGaleria(galeriaId: String?, ancho: Int, alto: Int): State<ImageBitmap?> {
    val ctx = LocalContext.current
    return produceState(galeriaId?.let { TapasGalerias.enCache(it, ancho, alto) }, galeriaId, ancho, alto) {
        value = if (galeriaId == null) null else TapasGalerias.cargar(Sincronizacion.dirBase(ctx), galeriaId, ancho, alto)
    }
}
