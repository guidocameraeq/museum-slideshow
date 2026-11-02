package local.museumslideshow.galeria

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Handler
import android.os.Looper
import android.util.Log
import java.io.File
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import local.museumslideshow.Registro
import local.museumslideshow.acomodo.Disposicion
import local.museumslideshow.catalogo.Catalogo
import local.museumslideshow.catalogo.Obra

/**
 * Una obra ya abierta, contada: la usan una o dos escenas a la vez (la que se va y la que viene comparten la
 * misma si cae al mismo tamaño). Cuando nadie la usa más se recicla DESPUÉS de una demora, porque la GPU dibuja
 * un poco más tarde de lo que se manda.
 */
class ObraCargada(val bitmap: Bitmap, val muestreo: Int, private val bytes: Long) {
    private val usos = AtomicInteger(1)

    init { vivos.addAndGet(bytes) }

    fun retener(): ObraCargada {
        usos.incrementAndGet()
        return this
    }

    fun soltar() {
        if (usos.decrementAndGet() == 0) {
            principal.postDelayed({
                vivos.addAndGet(-bytes)
                if (!bitmap.isRecycled) bitmap.recycle()
            }, ConstantesGaleria.RECICLADO_DEMORA_MS)
        }
    }

    companion object {
        /** Bytes de imágenes de obra vivas (se loguea en cada escena: es lo que vigila la prueba de 2 horas). */
        val vivos = AtomicLong(0)
        private val principal = Handler(Looper.getMainLooper())
    }
}

/** Lo que abrió el cargador para una escena. */
class ObrasAbiertas(val obras: List<ObraCargada?>, val fondo: Bitmap?) {
    fun soltar() {
        obras.forEach { it?.soltar() }
        // El fondo desenfocado es mínimo (64 px): lo recoge el recolector de basura.
    }
}

/**
 * Abre las copias guardadas de las obras. Nunca decodifica una imagen entera: primero las medidas
 * (`inJustDecodeBounds`), después `inSampleSize` potencia de 2 que no baje del tamaño del lugar de la obra,
 * y todas las obras de una escena juntas no pasan de UNA pantalla de la superficie ([Decodificacion.planificar]).
 */
class CargadorObras(private val dirBase: File) {

    private fun medidas(f: File): Pair<Int, Int>? {
        val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(f.absolutePath, o)
        return if (o.outWidth > 0 && o.outHeight > 0) o.outWidth to o.outHeight else null
    }

    private fun abrir(f: File, muestreo: Int): Bitmap? {
        var m = muestreo
        repeat(2) {
            try {
                val o = BitmapFactory.Options().apply {
                    inSampleSize = m
                    inPreferredConfig = if (ConstantesGaleria.BITMAP_EN_MEMORIA_DE_VIDEO) Bitmap.Config.HARDWARE else Bitmap.Config.ARGB_8888
                }
                val b = BitmapFactory.decodeFile(f.absolutePath, o)
                if (b != null) {
                    if (!ConstantesGaleria.BITMAP_EN_MEMORIA_DE_VIDEO) b.prepareToDraw()
                    return b
                }
                if (ConstantesGaleria.BITMAP_EN_MEMORIA_DE_VIDEO) {
                    Log.w(Registro.TAG, "Bitmap de memoria de video no disponible; se usa el común")
                    val o2 = BitmapFactory.Options().apply { inSampleSize = m }
                    return BitmapFactory.decodeFile(f.absolutePath, o2)
                }
                return null
            } catch (e: OutOfMemoryError) {
                Log.w(Registro.TAG, "Sin memoria al abrir una obra (muestreo $m); se reintenta a la mitad")
                m *= 2
            }
        }
        return null
    }

    /**
     * Abre las obras de una disposición al tamaño de su lugar × `escala` (4K = 2, 1080 = 1). `previas` = lo ya abierto
     * por la escena actual: si una obra ya está abierta con el mismo muestreo se comparte en vez de abrirla de nuevo.
     * Bloqueante (usar en un hilo de fondo).
     */
    fun abrir(disp: Disposicion, escala: Float, presupuestoPx: Long, previas: Map<String, ObraCargada>): List<ObraCargada?> {
        val archivos = disp.obras.map { Catalogo.archivoObra(dirBase, it.obra.driveId) }
        val med = archivos.map { medidas(it) }
        val idx = disp.obras.indices.filter { med[it] != null }
        val pedidos = idx.map { i ->
            val (w, h) = med[i]!!
            val r = disp.obras[i].interior
            PedidoDecodificacion(w, h, Math.ceil((r.ancho * escala).toDouble()).toInt(), Math.ceil((r.alto * escala).toDouble()).toInt())
        }
        val muestreos = Decodificacion.planificar(pedidos, presupuestoPx)
        val salida = arrayOfNulls<ObraCargada>(disp.obras.size)
        idx.forEachIndexed { k, i ->
            val id = disp.obras[i].obra.driveId
            val previa = previas[id]
            salida[i] = if (previa != null && previa.muestreo == muestreos[k] && !previa.bitmap.isRecycled) {
                previa.retener()
            } else {
                abrir(archivos[i], muestreos[k])?.let { b -> ObraCargada(b, muestreos[k], b.allocationByteCount.toLong()) }
            }
            if (salida[i] == null) Log.w(Registro.TAG, "No se pudo abrir la obra $id")
        }
        return salida.toList()
    }

    /** La copia chica de una obra, ya desenfocada, para el fondo de Inmersivo. null si no está o no se puede abrir. */
    fun abrirFondo(obra: Obra): Bitmap? {
        val f = Catalogo.archivoObraChica(dirBase, obra.driveId)
        if (!f.isFile) return null
        return try {
            val b = BitmapFactory.decodeFile(f.absolutePath) ?: return null
            val w = b.width
            val h = b.height
            val px = IntArray(w * h)
            b.getPixels(px, 0, w, 0, 0, w, h)
            b.recycle()
            val d = Desenfoque.cajas(px, w, h, ConstantesGaleria.DESENFOQUE_RADIO_PX, ConstantesGaleria.DESENFOQUE_PASADAS)
            Bitmap.createBitmap(d, w, h, Bitmap.Config.ARGB_8888)
        } catch (e: Exception) {
            Log.w(Registro.TAG, "No se pudo abrir el fondo de ${obra.driveId} (${e.javaClass.simpleName})")
            null
        }
    }
}
