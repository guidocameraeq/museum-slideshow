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

    /** Marco para obras redondas u ovaladas: la foto entera en el rectángulo exterior. Los demás no dibujan nada. */
    fun dibujarEntero(c: Canvas, p: Paint, exterior: RectF, factor: Float) {}
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
 * Grosor de cada lado de una foto de marco (px de la foto) y su escalado a pantalla: el lado más grueso mide
 * `t` y los otros salen en proporción (un solo factor para los cuatro, así las esquinas no se deforman).
 */
class Bordes(val izq: Int, val arr: Int, val der: Int, val aba: Int) {
    val maximo: Int = maxOf(izq, arr, der, aba)

    /** Escribe en `out` (izq, arr, der, aba) el grosor en pantalla para un grosor total `t`. Sin allocations. */
    fun escalar(t: Float, out: FloatArray) {
        val k = if (maximo > 0) t / maximo else 0f
        out[0] = izq * k; out[1] = arr * k; out[2] = der * k; out[3] = aba * k
    }
}

/**
 * Marco hecho con la foto de un marco real, estirada por partes: las 4 esquinas quedan intactas y los 4 lados
 * se estiran. `bordes` = grosor de cada lado dentro de la foto. El centro de la foto no se dibuja.
 * Si `entero` (marco redondo), la foto se dibuja completa, escalada al rectángulo exterior.
 */
class MarcoImagen(private val foto: Bitmap, private val bordes: Bordes, private val entero: Boolean = false) : DibujanteMarco {
    private val src = android.graphics.Rect()
    private val dst = RectF()
    private val g = FloatArray(4)

    private fun parte(c: Canvas, p: Paint, sl: Int, st: Int, sr: Int, sb: Int, dl: Float, dt: Float, dr: Float, db: Float) {
        if (dr <= dl || db <= dt || sr <= sl || sb <= st) return
        src.set(sl, st, sr, sb)
        dst.set(dl, dt, dr, db)
        c.drawBitmap(foto, src, dst, p)
    }

    private fun preparar(p: Paint, factor: Float) {
        p.style = Paint.Style.FILL
        p.shader = null
        p.color = android.graphics.Color.WHITE
        p.alpha = (factor.coerceIn(0f, 1f) * 255).toInt()
        p.isFilterBitmap = true
    }

    override fun dibujarEntero(c: Canvas, p: Paint, exterior: RectF, factor: Float) {
        if (!entero || foto.isRecycled || exterior.width() <= 0f || exterior.height() <= 0f) return
        preparar(p, factor)
        src.set(0, 0, foto.width, foto.height)
        c.drawBitmap(foto, src, exterior, p)
    }

    override fun dibujar(c: Canvas, p: Paint, exterior: RectF, grosor: Float, filete: Float, modo: Modo, factor: Float) {
        val t = grosor + filete
        if (t <= 0f || foto.isRecycled) return
        if (entero) { dibujarEntero(c, p, exterior, factor); return }
        val w = foto.width
        val h = foto.height
        // los bordes nunca pueden pasar de la mitad de la foto
        val bl = min(bordes.izq, w / 2); val bt = min(bordes.arr, h / 2)
        val br = min(bordes.der, w / 2); val bb = min(bordes.aba, h / 2)
        bordes.escalar(t, g)
        preparar(p, factor)
        val l = exterior.left; val tp = exterior.top; val r = exterior.right; val bm = exterior.bottom
        val gl = g[0]; val gt = g[1]; val gr = g[2]; val gb = g[3]
        // esquinas
        parte(c, p, 0, 0, bl, bt, l, tp, l + gl, tp + gt)
        parte(c, p, w - br, 0, w, bt, r - gr, tp, r, tp + gt)
        parte(c, p, 0, h - bb, bl, h, l, bm - gb, l + gl, bm)
        parte(c, p, w - br, h - bb, w, h, r - gr, bm - gb, r, bm)
        // lados
        parte(c, p, bl, 0, w - br, bt, l + gl, tp, r - gr, tp + gt)
        parte(c, p, bl, h - bb, w - br, h, l + gl, bm - gb, r - gr, bm)
        parte(c, p, 0, bt, bl, h - bb, l, tp + gt, l + gl, bm - gb)
        parte(c, p, w - br, bt, w, h - bb, r - gr, tp + gt, r, bm - gb)
    }
}

/** Qué dibujante le toca a cada marco del catálogo. */
object Marcos {
    /**
     * Grosor de cada lado del marco dentro de su foto (izq, arriba, der, abajo; px de la foto de 800 px de lado largo),
     * medido por `herramientas/marcos/PrepararMarcos.java`. El redondo se dibuja entero: no necesita bordes.
     */
    val BORDES_DE_FOTO: Map<String, Bordes> = mapOf(
        "madera" to Bordes(110, 107, 111, 116),
        "dorado" to Bordes(152, 160, 161, 163),
        "filete" to Bordes(174, 170, 172, 171),
    )

    private val usados = HashMap<String, DibujanteMarco>()

    /** `res/drawable-nodpi/marco_<id>.png` si el marco tiene foto y está; si no, el negro fino provisorio. */
    fun para(def: MarcoDef, ctx: Context): DibujanteMarco {
        usados[def.id]?.let { return it }
        var d: DibujanteMarco = MarcoProvisorio
        val bordes = BORDES_DE_FOTO[def.id]
        if (def.imagenDisponible && def.grosor.values.any { it > 0 } && (bordes != null || def.redondo)) {
            val res = ctx.resources.getIdentifier("marco_" + def.id.replace('-', '_'), "drawable", ctx.packageName)
            if (res != 0) {
                try {
                    val bmp = BitmapFactory.decodeResource(ctx.resources, res)
                    if (bmp != null) d = MarcoImagen(bmp, bordes ?: Bordes(0, 0, 0, 0), entero = def.redondo)
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
        if (o.forma != Forma.RECTANGULAR) {              // obra redonda u ovalada: foto del marco redondo entera, sin paspartú
            if (o.grosorMarco > 0f) marco.dibujarEntero(c, p, Formas.rectF(o.exterior), factor)
            return
        }
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
