package local.museumslideshow.galeria

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import kotlinx.coroutines.CompletableDeferred
import android.view.SurfaceHolder
import android.view.SurfaceView
import kotlin.math.max
import local.museumslideshow.Registro
import local.museumslideshow.acomodo.Constantes
import local.museumslideshow.ajustes.Calidad
import local.museumslideshow.ajustes.Modo
import local.museumslideshow.catalogo.Forma

/** Lo que la superficie necesita de una escena y se arma una sola vez (shaders, filtros de color). */
private class RecursosSuperficie(e: EscenaLista) {
    private val oscuro = e.modo == Modo.OSCURO
    val pared: Shader?
    val canalIzq: Shader?
    val canalDer: Shader?
    val filtroFondo: ColorMatrixColorFilter?
    val colorMesa: Int

    init {
        val w1 = e.colorPared.c1.toInt()
        val w2 = e.colorPared.c2.toInt()
        val tema = e.tema
        val lomo = e.lomoX
        val gut = (tema.colorCanal[if (oscuro) Modo.OSCURO else Modo.CLARO] ?: 0L).toInt()
        val gutSinAlpha = gut and 0xFFFFFF
        canalIzq = if (tema.esLibro) LinearGradient(lomo - ANCHO_CANAL, 0f, lomo, 0f, gutSinAlpha, gut, Shader.TileMode.CLAMP) else null
        canalDer = if (tema.esLibro) LinearGradient(lomo, 0f, lomo + ANCHO_CANAL, 0f, gut, gutSinAlpha, Shader.TileMode.CLAMP) else null
        val mezcla = tema.mesaLibro[if (oscuro) Modo.OSCURO else Modo.CLARO]
        colorMesa = if (mezcla != null) mezclar(e.colorPared.pagina.toInt(), mezcla.con.toInt(), mezcla.parte) else w1

        pared = when {
            tema.esLibro -> null
            tema.focoEnPared -> {
                val rx = (if (oscuro) 0.55f else 0.60f) * Constantes.ANCHO
                val ry = (if (oscuro) 0.75f else 0.80f) * Constantes.ALTO
                val cx = 0.5f * Constantes.ANCHO
                val cy = 0.38f * Constantes.ALTO
                val centro = mezclar(w1, Color.WHITE, if (oscuro) 0.22f else 0.55f)
                val s = RadialGradient(
                    cx, cy, rx, intArrayOf(centro, w1, w2), floatArrayOf(0f, if (oscuro) 0.45f else 0.5f, 1f), Shader.TileMode.CLAMP,
                )
                val m = Matrix()
                m.setScale(1f, ry / rx, cx, cy)
                s.setLocalMatrix(m)
                s
            }
            else -> LinearGradient(0f, 0f, 0f, Constantes.ALTO, w1, w2, Shader.TileMode.CLAMP)
        }

        // Inmersivo: brillo y saturación del CSS (.bg) y, de día, el velo blanco de encima, todo en un solo filtro.
        filtroFondo = if (tema.fondoDesenfocado) {
            val brillo = if (oscuro) 0.38f else 1.2f
            val saturacion = if (oscuro) 1.15f else 0.85f
            val b = ColorMatrix(floatArrayOf(brillo, 0f, 0f, 0f, 0f, 0f, brillo, 0f, 0f, 0f, 0f, 0f, brillo, 0f, 0f, 0f, 0f, 0f, 1f, 0f))
            val s = ColorMatrix().also { it.setSaturation(saturacion) }
            val total = ColorMatrix().also { it.setConcat(s, b) }
            if (!oscuro) {
                val velo = 0.5f
                val v = ColorMatrix(
                    floatArrayOf(1 - velo, 0f, 0f, 0f, 255f * velo, 0f, 1 - velo, 0f, 0f, 255f * velo, 0f, 0f, 1 - velo, 0f, 255f * velo, 0f, 0f, 0f, 1f, 0f),
                )
                total.postConcat(v)
            }
            ColorMatrixColorFilter(total)
        } else null
    }

    companion object {
        const val ANCHO_CANAL = 70f

        fun mezclar(a: Int, b: Int, parte: Float): Int {
            fun c(x: Int, y: Int) = (x + (y - x) * parte).toInt().coerceIn(0, 255)
            return Color.argb(255, c(Color.red(a), Color.red(b)), c(Color.green(a), Color.green(b)), c(Color.blue(a), Color.blue(b)))
        }
    }
}

/** Dibuja la capa de atrás: pared, fondo desenfocado, página del libro y las obras. Todo en unidades de 1920x1080. */
private class PintorSuperficie {
    private val p = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()
    private val pantalla = RectF(0f, 0f, Constantes.ANCHO, Constantes.ALTO)
    private val matriz = Matrix()

    private fun recursos(e: EscenaLista): RecursosSuperficie {
        val r = e.recursosSuperficie
        if (r is RecursosSuperficie) return r
        return RecursosSuperficie(e).also { e.recursosSuperficie = it }
    }

    private fun reset(alpha: Float) {
        p.shader = null
        p.colorFilter = null
        p.style = Paint.Style.FILL
        p.color = Color.BLACK
        p.alpha = (alpha.coerceIn(0f, 1f) * 255f).toInt()
    }

    /** `factor` < 1 = esta escena entra con fundido sobre la anterior: su pared deja libre el hueco de las obras. */
    fun escena(c: Canvas, e: EscenaLista, factor: Float) {
        val r = recursos(e)
        val dejarHuecos = factor < 1f
        c.save()
        if (dejarHuecos) {
            e.disp.obras.forEachIndexed { i, o ->
                val ov = e.ovalos[i]
                if (ov != null) c.clipOutPath(ov) else c.clipOutRect(o.interior.x, o.interior.y, o.interior.derecha, o.interior.abajo)
            }
        }
        pared(c, e, r, factor)
        c.restore()
        obras(c, e, factor)
    }

    private fun pared(c: Canvas, e: EscenaLista, r: RecursosSuperficie, factor: Float) {
        val tema = e.tema
        when {
            tema.esLibro -> libro(c, e, r, factor)
            tema.fondoDesenfocado && e.abiertas.fondo != null -> {
                reset(factor)
                c.drawRect(pantalla, p)       // piso negro (el CSS tiene .wall #000)
                fondoDesenfocado(c, e.abiertas.fondo, r, factor)
            }
            else -> {
                reset(factor)
                p.shader = r.pared ?: LinearGradient(0f, 0f, 0f, Constantes.ALTO, e.colorPared.c1.toInt(), e.colorPared.c2.toInt(), Shader.TileMode.CLAMP)
                c.drawRect(pantalla, p)
                p.shader = null
            }
        }
    }

    /** El fondo de Inmersivo: la copia chica estirada a -8%..108% de la pantalla (como `.bg` del CSS), con filtro. */
    private fun fondoDesenfocado(c: Canvas, fondo: Bitmap, r: RecursosSuperficie, factor: Float) {
        val bx = -0.08f * Constantes.ANCHO
        val by = -0.08f * Constantes.ALTO
        val bw = 1.16f * Constantes.ANCHO
        val bh = 1.16f * Constantes.ALTO
        reset(factor)
        p.colorFilter = r.filtroFondo
        cubrir(c, fondo, bx, by, bw, bh)
        p.colorFilter = null
    }

    /** Dibuja `b` cubriendo la caja (object-fit: cover), recortado a la caja. */
    private fun cubrir(c: Canvas, b: Bitmap, x: Float, y: Float, w: Float, h: Float) {
        if (b.isRecycled) return
        val s = max(w / b.width, h / b.height)
        c.save()
        c.clipRect(x, y, x + w, y + h)
        c.translate(x + (w - b.width * s) / 2, y + (h - b.height * s) / 2)
        c.scale(s, s)
        c.drawBitmap(b, 0f, 0f, p)
        c.restore()
    }

    private fun libro(c: Canvas, e: EscenaLista, r: RecursosSuperficie, factor: Float) {
        val libro = Constantes.LIBRO
        reset(factor)
        p.color = r.colorMesa
        p.alpha = (factor.coerceIn(0f, 1f) * 255f).toInt()
        c.drawRect(pantalla, p)
        rect.set(libro.x, libro.y, libro.derecha, libro.abajo)
        Formas.sombra(c, p, rect, false, Sombra(0f, 30f, 70f, 0.5f), factor)
        reset(factor)
        p.color = e.colorPared.pagina.toInt()
        p.alpha = (factor.coerceIn(0f, 1f) * 255f).toInt()
        c.drawRect(rect, p)
        for ((shader, x0) in listOf(r.canalIzq to e.lomoX - RecursosSuperficie.ANCHO_CANAL, r.canalDer to e.lomoX)) {
            if (shader == null) continue
            reset(factor)
            p.shader = shader
            c.drawRect(x0, libro.y, x0 + RecursosSuperficie.ANCHO_CANAL, libro.abajo, p)
            p.shader = null
        }
    }

    private fun obras(c: Canvas, e: EscenaLista, factor: Float) {
        e.disp.obras.forEachIndexed { i, o ->
            val b = e.abiertas.obras.getOrNull(i)?.bitmap ?: return@forEachIndexed
            if (b.isRecycled) return@forEachIndexed
            val d = o.interior
            reset(factor)
            if (o.forma == Forma.RECTANGULAR) {
                cubrir(c, b, d.x, d.y, d.ancho, d.alto)
            } else {
                // Redonda u ovalada: la obra cubre el óvalo (recorte centrado) con bordes suaves.
                val s = max(d.ancho / b.width, d.alto / b.height)
                val sh = BitmapShader(b, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
                matriz.reset()
                matriz.postScale(s, s)
                matriz.postTranslate(d.x + (d.ancho - b.width * s) / 2, d.y + (d.alto - b.height * s) / 2)
                sh.setLocalMatrix(matriz)
                p.shader = sh
                rect.set(d.x, d.y, d.derecha, d.abajo)
                c.drawOval(rect, p)
                p.shader = null
            }
        }
    }
}

/**
 * Las dos superficies 4K de detrás de la ventana (A abajo, B arriba con `setZOrderMediaOverlay`), de 3840x2160
 * (o 1920x1080 con Calidad 1080). Cada escena se dibuja UNA sola vez en la libre; el fundido lo hace el compositor
 * moviendo el alpha de B (sube = entra la escena de B; baja = entra la de A). Alpha de B: 1 si la actual es B, 0 si es A.
 * El dibujo (estático) va en un hilo propio; el alpha se cambia desde el hilo principal.
 */
class SuperficieObra {
    private val pintor = PintorSuperficie()
    private val hilo = HandlerThread("superficie-obra").also { it.start() }
    private val h = Handler(hilo.looper)

    private val vistas = arrayOfNulls<SurfaceView>(2)
    @Volatile private var validas = 0
    @Volatile private var calidad: Calidad? = null
    @Volatile private var cerrada = false
    private var avisoError = false
    private var avisoAlpha = false
    private var alphaB = 0f
    private val transaccion = android.view.SurfaceControl.Transaction()

    // Solo en el hilo de la superficie: la escena que muestra cada una (para redibujar si la superficie se recrea).
    private val escenas = arrayOfNulls<EscenaLista>(2)

    /**
     * Engancha una vista nueva (las dos se recrean cada vez que cambia la Calidad: cambiar el tamaño de una superficie
     * viva dejaba búferes de 1920x1080 al volver a 4K). Nace con el tamaño correcto desde el arranque.
     */
    fun enlazar(slot: Int, v: SurfaceView, c: Calidad) {
        calidad = c
        validas = validas and (1 shl slot).inv()
        vistas[slot] = v
        if (c == Calidad.HD1080) v.holder.setFixedSize(ConstantesGaleria.SUPERFICIE_1080_ANCHO, ConstantesGaleria.SUPERFICIE_1080_ALTO)
        else v.holder.setFixedSize(ConstantesGaleria.SUPERFICIE_4K_ANCHO, ConstantesGaleria.SUPERFICIE_4K_ALTO)
        v.holder.addCallback(Escucha(slot, v))
        if (v.holder.surface?.isValid == true) validas = validas or (1 shl slot)
    }

    /** Dibuja la escena en la superficie `slot` (0 = A, 1 = B) y espera a que esté mandada. Devuelve cuánto tardó (ms). */
    suspend fun dibujar(slot: Int, e: EscenaLista): Float {
        if (cerrada) return 0f
        val listo = CompletableDeferred<Float>()
        h.post {
            escenas[slot] = e
            val t0 = System.nanoTime()
            dibujarAhora(slot)
            listo.complete((System.nanoTime() - t0) / 1e6f)
        }
        return listo.await()
    }

    /** B está arriba de A: su alpha es lo único que se mueve en un fundido. Hilo principal. */
    fun alphaArriba(a: Float) {
        alphaB = a.coerceIn(0f, 1f)
        aplicarAlphaB()
    }

    /** El alpha va directo a la capa del compositor (`View.setAlpha` no le llega en esta tele). */
    private fun aplicarAlphaB() {
        val v = vistas[1] ?: return
        try {
            val sc = v.surfaceControl
            if (!sc.isValid) return
            transaccion.setAlpha(sc, alphaB).apply()
        } catch (ex: Exception) {
            if (!avisoAlpha) { avisoAlpha = true; Log.w(Registro.TAG, "No se pudo fijar el alpha de la superficie (${ex.javaClass.simpleName})") }
        }
    }

    /** Esa superficie ya no muestra ninguna escena (se soltó): no se vuelve a dibujar con imágenes recicladas. */
    fun soltar(slot: Int) {
        h.post { escenas[slot] = null }
    }

    fun cerrar() {
        cerrada = true
        hilo.quitSafely()
    }

    private fun dibujarAhora(slot: Int) {
        val holder = vistas[slot]?.holder ?: return
        if ((validas and (1 shl slot)) == 0) return
        val e = escenas[slot]
        var canvas: Canvas? = null
        try {
            canvas = holder.surface.lockHardwareCanvas()
            if (e == null) canvas.drawColor(Color.TRANSPARENT, android.graphics.PorterDuff.Mode.CLEAR)
            else {
                val k = canvas.width / Constantes.ANCHO
                canvas.save()
                canvas.scale(k, k)
                pintor.escena(canvas, e, 1f)
                canvas.restore()
            }
        } catch (ex: Exception) {
            if (!avisoError) {
                avisoError = true
                Log.w(Registro.TAG, "No se pudo dibujar en la superficie (${ex.javaClass.simpleName}: ${ex.message})")
            }
        } finally {
            if (canvas != null) {
                try { holder.surface.unlockCanvasAndPost(canvas) } catch (ex: Exception) { /* la superficie ya no está */ }
            }
        }
    }

    /** Escucha de UNA vista: si ya la reemplazó otra (cambio de calidad), sus avisos no cuentan. */
    private inner class Escucha(private val slot: Int, private val vista: SurfaceView) : SurfaceHolder.Callback {
        private fun vigente() = vistas[slot] === vista

        override fun surfaceCreated(holder: SurfaceHolder) {
            if (!vigente()) return
            validas = validas or (1 shl slot)
            if (slot == 1) aplicarAlphaB()
        }

        override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
            if (!vigente()) return
            validas = validas or (1 shl slot)
            if (slot == 1) aplicarAlphaB()
            Log.i(Registro.TAG, "Superficie de la obra ${if (slot == 0) "A" else "B"}: ${width}x$height")
            if (!cerrada) h.post { if (escenas[slot] != null) dibujarAhora(slot) }
        }

        override fun surfaceDestroyed(holder: SurfaceHolder) {
            if (!vigente()) return
            validas = validas and (1 shl slot).inv()
        }
    }
}
