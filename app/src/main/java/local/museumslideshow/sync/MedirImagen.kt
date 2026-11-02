package local.museumslideshow.sync

import android.graphics.BitmapFactory
import java.io.File

/** Las medidas de una imagen guardada, sin decodificarla (solo la cabecera). null si no se puede leer. */
fun medirImagen(f: File): Pair<Int, Int>? {
    val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(f.absolutePath, o)
    return if (o.outWidth > 0 && o.outHeight > 0) o.outWidth to o.outHeight else null
}
