package local.museumslideshow.galeria

import android.graphics.Paint
import android.graphics.RectF
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.text.drawText
import local.museumslideshow.ajustes.Modo
import local.museumslideshow.ajustes.Tema
import local.museumslideshow.temas.Rol

/**
 * La capa de interfaz (Compose, transparente, encima de la superficie): sombra, marco, filete y paspartú alrededor
 * de cada obra, y todos los textos. Nunca dibuja sobre el `interior` de la obra: ahí se ve la superficie 4K.
 * Todo en unidades de 1920x1080; quien llama ya escaló el lienzo.
 */
class PintorInterfaz {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val r = RectF()
    private val sombraDeTexto = Shadow(Color(0x80000000), Offset(0f, 1f), 6f)

    /** `factor` = opacidad de toda la escena (fundido). `marca` = mostrar cuál obra de la Pared está elegida. */
    fun dibujar(ds: DrawScope, e: EscenaLista, factor: Float, elegida: Int, marca: Boolean) {
        if (factor <= 0.003f) return
        obras(ds, e, factor)
        textos(ds, e, factor, elegida, marca)
        libro(ds, e, factor)
    }

    private fun obras(ds: DrawScope, e: EscenaLista, factor: Float) {
        ds.drawIntoCanvas { cv ->
            val c = cv.nativeCanvas
            e.disp.obras.forEachIndexed { i, o ->
                val ov = e.ovalos[i]
                c.save()
                if (ov != null) c.clipOutPath(ov) else c.clipOutRect(o.interior.x, o.interior.y, o.interior.derecha, o.interior.abajo)
                r.set(o.exterior.x, o.exterior.y, o.exterior.derecha, o.exterior.abajo)
                for (s in e.sombras) Formas.sombra(c, p, r, ov != null, s, factor)
                if (e.modo == Modo.OSCURO && !e.tema.esLibro && e.tema.id != Tema.INMERSIVO && ov == null) {
                    // el contorno finito que el CSS pone de noche (0 0 0 1px rgba(255,255,255,.06))
                    val fuera = RectF(r.left - 1f, r.top - 1f, r.right + 1f, r.bottom + 1f)
                    Formas.anillo(c, p, fuera, r, Formas.conAlpha(0x0FFFFFFF, factor))
                }
                e.marcos.getOrNull(i)?.let { Anillos.dibujar(c, p, o, e.tema, e.modo, it, factor) }
                c.restore()
            }
        }
    }

    private fun textos(ds: DrawScope, e: EscenaLista, factor: Float, elegida: Int, marca: Boolean) {
        val tip = e.tema.tipografia(e.modo)
        for (d in e.dibujables) {
            val a = factor * when (d.tipo) {
                TipoDibujable.CARTEL, TipoDibujable.NOMBRE, TipoDibujable.CHIP -> e.cartelAlpha.floatValue
                TipoDibujable.SALA -> e.salaAlpha.floatValue
                TipoDibujable.CONTADOR -> 1f
            }
            if (a <= 0.003f) continue
            if (d.fondo != null) {
                ds.drawIntoCanvas { cv ->
                    p.style = Paint.Style.FILL
                    p.shader = null
                    p.color = Formas.conAlpha(d.fondo, a)
                    r.set(d.rect.x, d.rect.y, d.rect.derecha, d.rect.abajo)
                    cv.nativeCanvas.drawRoundRect(r, d.radioFondo, d.radioFondo, p)
                }
            }
            for (b in d.bloques) {
                ds.drawText(
                    b.layout, topLeft = Offset(d.rect.x + b.x, d.rect.y + b.y), alpha = a,
                    shadow = if (d.sombraTexto) sombraDeTexto else null,
                )
            }
            if (marca && d.tipo == TipoDibujable.NOMBRE && d.obraIndice == elegida) {
                val color = tip[Rol.NOMBRE_TITULO].color.toInt()
                ds.drawIntoCanvas { cv ->
                    p.style = Paint.Style.FILL
                    p.shader = null
                    p.color = Formas.conAlpha(color, a)
                    val y = d.rect.abajo + 6f
                    val x0 = d.rect.centroX - ConstantesGaleria.MARCA_ELEGIDA_ANCHO / 2
                    cv.nativeCanvas.drawRect(x0, y, x0 + ConstantesGaleria.MARCA_ELEGIDA_ANCHO, y + ConstantesGaleria.MARCA_ELEGIDA_GROSOR, p)
                }
            }
        }
    }

    private fun libro(ds: DrawScope, e: EscenaLista, factor: Float) {
        val l = e.libro ?: return
        val a = factor * e.cartelAlpha.floatValue
        if (a <= 0.003f) return
        l.pagina.reglaY?.let { y ->
            ds.drawIntoCanvas { cv ->
                p.style = Paint.Style.FILL
                p.shader = null
                p.color = Formas.conAlpha(l.reglaColor, a)
                val top = l.columna.y + y
                cv.nativeCanvas.drawRect(l.columna.x, top, l.columna.derecha, top + l.reglaGrosor, p)
            }
        }
        for (b in l.pagina.bloques) {
            ds.drawText(b.layout, topLeft = Offset(l.columna.x + b.x, l.columna.y + b.y), alpha = a)
        }
    }
}
