package local.museumslideshow.galeria

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.Log
import kotlin.math.min
import local.museumslideshow.Registro
import local.museumslideshow.acomodo.ObraColocada
import local.museumslideshow.ajustes.Modo
import local.museumslideshow.catalogo.Forma
import local.museumslideshow.temas.MarcoDef
import local.museumslideshow.temas.TemaDef

/**
 * Dibuja el marco (y el filete) en el anillo entre `exterior` y lo de adentro. Nunca dibuja sobre la obra.
 * `factor` = transparencia general (fundido).
 */
interface DibujanteMarco {
    fun dibujar(c: Canvas, p: Paint, exterior: RectF, grosor: Float, filete: Float, modo: Modo, factor: Float)
}

/** Negro fino provisorio (y filete dorado si el marco lo trae) mientras no hay foto del marco. */
object MarcoProvisorio : DibujanteMarco {
    private const val NEGRO_CLARO = 0xFF1C1A18.toInt()
    private const val NEGRO_OSCURO = 0xFF060606.toInt()
    private const val DORADO = 0xFFC9A54E.toInt()

    override fun dibujar(c: Canvas, p: Paint, exterior: RectF, grosor: Float, filete: Float, modo: Modo, factor: Float) {
        val aroMarco = Formas.achicar(exterior, grosor)
        val aroFilete = Formas.achicar(aroMarco, filete)
        Formas.anillo(c, p, exterior, aroMarco, Formas.conAlpha(if (modo == Modo.OSCURO) NEGRO_OSCURO else NEGRO_CLARO, factor))
        Formas.anillo(c, p, aroMarco, aroFilete, Formas.conAlpha(DORADO, factor))
    }
}

/**
 * Marco hecho con la foto de un marco real, estirada por partes: las 4 esquinas quedan intactas y los 4 lados
 * se estiran. `borde` = grosor del marco dentro de la foto, en px de la foto. El centro de la foto no se dibuja.
 */
class MarcoImagen(private val foto: Bitmap, private val borde: Int) : DibujanteMarco {
    private val src = android.graphics.Rect()
    private val dst = RectF()

    private fun parte(c: Canvas, p: Paint, sl: Int, st: Int, sr: Int, sb: Int, dl: Float, dt: Float, dr: Float, db: Float) {
        if (dr <= dl || db <= dt || sr <= sl || sb <= st) return
        src.set(sl, st, sr, sb)
        dst.set(dl, dt, dr, db)
        c.drawBitmap(foto, src, dst, p)
    }

    override fun dibujar(c: Canvas, p: Paint, exterior: RectF, grosor: Float, filete: Float, modo: Modo, factor: Float) {
        val t = grosor + filete
        if (t <= 0f || foto.isRecycled) return
        val b = min(borde, min(foto.width, foto.height) / 2)
        p.style = Paint.Style.FILL
        p.shader = null
        p.color = android.graphics.Color.WHITE
        p.alpha = (factor.coerceIn(0f, 1f) * 255).toInt()
        p.isFilterBitmap = true
        val w = foto.width
        val h = foto.height
        val l = exterior.left; val tp = exterior.top; val r = exterior.right; val bt = exterior.bottom
        // esquinas
        parte(c, p, 0, 0, b, b, l, tp, l + t, tp + t)
        parte(c, p, w - b, 0, w, b, r - t, tp, r, tp + t)
        parte(c, p, 0, h - b, b, h, l, bt - t, l + t, bt)
        parte(c, p, w - b, h - b, w, h, r - t, bt - t, r, bt)
        // lados
        parte(c, p, b, 0, w - b, b, l + t, tp, r - t, tp + t)
        parte(c, p, b, h - b, w - b, h, l + t, bt - t, r - t, bt)
        parte(c, p, 0, b, b, h - b, l, tp + t, l + t, bt - t)
        parte(c, p, w - b, b, w, h - b, r - t, tp + t, r, bt - t)
    }
}

/** Qué dibujante le toca a cada marco del catálogo. */
object Marcos {
    /** Grosor del marco dentro de su foto (px de la foto), por id. Se completa cuando lleguen las fotos. */
    private val BORDES_DE_FOTO: Map<String, Int> = emptyMap()

    private val usados = HashMap<String, DibujanteMarco>()

    /** `res/drawable-nodpi/marco_<id>.png` si el marco tiene foto y está; si no, el negro fino provisorio. */
    fun para(def: MarcoDef, ctx: Context): DibujanteMarco {
        usados[def.id]?.let { return it }
        var d: DibujanteMarco = MarcoProvisorio
        if (def.imagenDisponible && def.grosor.values.any { it > 0 }) {
            val res = ctx.resources.getIdentifier("marco_" + def.id.replace('-', '_'), "drawable", ctx.packageName)
            if (res != 0) {
                try {
                    val bmp = BitmapFactory.decodeResource(ctx.resources, res)
                    if (bmp != null) d = MarcoImagen(bmp, BORDES_DE_FOTO[def.id] ?: (min(bmp.width, bmp.height) / 4))
                } catch (e: Exception) {
                    Log.w(Registro.TAG, "No se pudo abrir la foto del marco ${def.id} (${e.javaClass.simpleName})")
                }
            }
        }
        usados[def.id] = d
        return d
    }
}

/** El anillo completo alrededor de la obra: marco, filete y paspartú con su bisel. Nada se dibuja sobre el `interior`. */
object Anillos {
    private const val BISEL_CLARO = 0xFFF3F1EA.toInt()
    private const val BISEL_OSCURO = 0xFF202023.toInt()
    private const val LINEA_CLARO = 0x1F000000
    private const val LINEA_OSCURO = 0x1AFFFFFF

    fun dibujar(c: Canvas, p: Paint, o: ObraColocada, tema: TemaDef, modo: Modo, marco: DibujanteMarco, factor: Float) {
        if (o.forma != Forma.RECTANGULAR) return          // los marcos redondos llegan con su foto
        if (o.grosorMarco <= 0f && o.filete <= 0f && o.paspartu <= 0f) return
        val ext = Formas.rectF(o.exterior)
        val interior = Formas.rectF(o.interior)
        marco.dibujar(c, p, ext, o.grosorMarco, o.filete, modo, factor)
        if (o.paspartu > 0f) {
            val oscuro = modo == Modo.OSCURO
            val hastaPaspartu = Formas.achicar(ext, o.grosorMarco + o.filete)
            val bisel = min(7f, o.paspartu * 0.4f)
            val r3 = RectF(interior.left - bisel, interior.top - bisel, interior.right + bisel, interior.bottom + bisel)
            val r4 = RectF(interior.left - 1f, interior.top - 1f, interior.right + 1f, interior.bottom + 1f)
            val color = (tema.colorPaspartu[if (oscuro) Modo.OSCURO else Modo.CLARO] ?: 0xFFFBFAF6L).toInt()
            Formas.anillo(c, p, hastaPaspartu, r3, Formas.conAlpha(color, factor))
            Formas.anillo(c, p, r3, r4, Formas.conAlpha(if (oscuro) BISEL_OSCURO else BISEL_CLARO, factor))
            Formas.anillo(c, p, r4, interior, Formas.conAlpha(if (oscuro) LINEA_OSCURO else LINEA_CLARO, factor))
        }
    }
}
