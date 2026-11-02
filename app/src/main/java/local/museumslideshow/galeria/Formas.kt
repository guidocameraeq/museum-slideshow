package local.museumslideshow.galeria

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.roundToInt
import local.museumslideshow.acomodo.Rect
import local.museumslideshow.ajustes.Modo
import local.museumslideshow.ajustes.Tema
import local.museumslideshow.temas.TemaDef

/** Una sombra (como `box-shadow: dx dy blur rgba(0,0,0,alpha)` del CSS). */
data class Sombra(val dx: Float, val dy: Float, val blur: Float, val alpha: Float)

/** Dibujo de formas sencillas sobre un Canvas de Android (lo usan la interfaz y la superficie). Solo hilo principal. */
object Formas {

    fun rectF(r: Rect) = RectF(r.x, r.y, r.derecha, r.abajo)

    /** `argb` con su transparencia multiplicada por `factor` (0..1). */
    fun conAlpha(argb: Int, factor: Float): Int {
        val a = ((argb ushr 24) * factor.coerceIn(0f, 1f)).roundToInt().coerceIn(0, 255)
        return (a shl 24) or (argb and 0xFFFFFF)
    }

    /**
     * Las sombras de la obra, copiadas del CSS de `03-temas-v3.html`: Galería moderna y Sala oscura según el modo,
     * Inmersivo y Libro de arte con la suya.
     */
    fun sombrasDe(tema: TemaDef, modo: Modo): List<Sombra> = when {
        tema.esLibro -> listOf(Sombra(0f, 1f, 2f, 0.4f), Sombra(0f, 6f, 14f, 0.22f))
        tema.id == Tema.INMERSIVO -> listOf(Sombra(0f, 10f, 36f, 0.4f))
        modo == Modo.OSCURO -> listOf(Sombra(0f, 24f, 54f, 0.7f))
        else -> listOf(Sombra(0f, 22f, 44f, 0.22f), Sombra(0f, 4f, 10f, 0.12f))
    }

    /**
     * Sombra suave: capas de rectángulos (u óvalos) de a poco más grandes y casi transparentes, que sumadas dan
     * `alpha` adentro y se van apagando hacia afuera. No usa desenfoque de la GPU (no anda en todos los lienzos).
     */
    fun sombra(c: Canvas, p: Paint, r: RectF, oval: Boolean, s: Sombra, factor: Float) {
        val tmp = RectF()
        val n = (s.blur / 3f).toInt().coerceIn(6, 14)
        val porCapa = 1f - (1.0 - s.alpha.coerceIn(0f, 0.99f).toDouble()).pow(1.0 / n).toFloat()
        p.style = Paint.Style.FILL
        p.shader = null
        p.color = conAlpha(Color.BLACK, 1f)
        p.alpha = (porCapa * factor * 255f).roundToInt().coerceIn(0, 255)
        if (p.alpha == 0) return
        for (k in 0 until n) {
            val g = -s.blur / 2f + s.blur * (k + 0.5f) / n
            tmp.set(r.left + s.dx - g, r.top + s.dy - g, r.right + s.dx + g, r.bottom + s.dy + g)
            if (tmp.width() <= 0f || tmp.height() <= 0f) continue
            if (oval) c.drawOval(tmp, p) else {
                val radio = max(0f, g)
                c.drawRoundRect(tmp, radio, radio, p)
            }
        }
    }

    /** Pinta la franja entre `externo` e `interno` (sin tocar lo de adentro). */
    fun anillo(c: Canvas, p: Paint, externo: RectF, interno: RectF, color: Int) {
        if ((color ushr 24) == 0) return
        if (externo.left >= interno.left && externo.top >= interno.top && externo.right <= interno.right && externo.bottom <= interno.bottom) return
        p.style = Paint.Style.FILL
        p.shader = null
        p.color = color
        c.save()
        c.clipOutRect(interno)
        c.drawRect(externo, p)
        c.restore()
    }

    fun achicar(r: RectF, d: Float) = RectF(r.left + d, r.top + d, r.right - d, r.bottom - d)
}
