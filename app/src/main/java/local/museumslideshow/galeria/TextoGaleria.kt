@file:OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)

package local.museumslideshow.galeria

import android.content.Context
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.TextUnitType
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.min
import local.museumslideshow.R
import local.museumslideshow.acomodo.Constantes
import local.museumslideshow.acomodo.MedidaTexto
import local.museumslideshow.acomodo.MedidorTexto
import local.museumslideshow.acomodo.NivelInfo
import local.museumslideshow.ajustes.Modo
import local.museumslideshow.ajustes.Tema
import local.museumslideshow.catalogo.Ficha
import local.museumslideshow.temas.EstiloTexto
import local.museumslideshow.temas.Familia
import local.museumslideshow.temas.RegistroTemas
import local.museumslideshow.temas.Rol
import local.museumslideshow.temas.TemaDef

/** Los formatos de texto armados con `strings.xml`. */
fun formatosDe(ctx: Context) = Formatos(
    autorConVida = { a, v -> ctx.getString(R.string.formato_autor_vida, a, v) },
    autorYAnio = { a, y -> ctx.getString(R.string.formato_autor_anio, a, y) },
    tecnicaYMedidas = { t, m -> ctx.getString(R.string.formato_tecnica_medidas, t, m) },
)

/** EB Garamond (SERIF) e Inter (SANS): fuentes variables, un `Font` por peso y estilo. */
class FuentesTexto {
    private fun familia(normal: Int, cursiva: Int, pesos: List<Int>) = FontFamily(
        pesos.flatMap { w ->
            listOf(
                Font(resId = normal, weight = FontWeight(w), style = FontStyle.Normal, variationSettings = FontVariation.Settings(FontVariation.weight(w))),
                Font(resId = cursiva, weight = FontWeight(w), style = FontStyle.Italic, variationSettings = FontVariation.Settings(FontVariation.weight(w))),
            )
        },
    )

    private val serif by lazy { familia(R.font.eb_garamond, R.font.eb_garamond_italic, listOf(400, 500, 600)) }
    private val sans by lazy { familia(R.font.inter, R.font.inter_italic, listOf(400, 500, 600)) }

    fun de(f: Familia): FontFamily = if (f == Familia.SERIF) serif else sans
}

/** Un texto ya medido; `x`/`y` son relativos al rectángulo del texto, en px del escenario de 1920x1080. */
class BloqueTexto(val layout: TextLayoutResult, val x: Float, val y: Float)

class TextoMedido(val bloques: List<BloqueTexto>, val ancho: Float, val alto: Float, val entra: Boolean)

/** La columna de texto del Libro de arte. `reglaY` = dónde va la línea fina (null = no hay). */
class PaginaMedida(val bloques: List<BloqueTexto>, val reglaY: Float?, val alto: Float, val entra: Boolean)

/**
 * Arma y mide los textos con el MISMO TextMeasurer y los MISMOS estilos que después se dibujan, así lo que mide el
 * Acomodador es lo que se ve. Todo en px del escenario: el TextMeasurer trabaja con densidad 1 (1 sp = 1 px) y el
 * dibujo escala el lienzo.
 */
class ComposicionTexto(
    private val medidor: TextMeasurer,
    private val fuentes: FuentesTexto,
    private val formatos: Formatos,
    private val avisoOk: String,
) {
    private val es = Locale("es", "AR")
    private val alineacionLibro = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None)

    private fun estilo(e: EstiloTexto, escala: Float, centrado: Boolean) = TextStyle(
        fontFamily = fuentes.de(e.familia),
        fontSize = TextUnit(e.tamanoPx * escala, TextUnitType.Sp),
        fontWeight = FontWeight(e.peso),
        fontStyle = if (e.italica) FontStyle.Italic else FontStyle.Normal,
        lineHeight = TextUnit(e.interlineadoPx * escala, TextUnitType.Sp),
        letterSpacing = if (e.espaciado != 0f) TextUnit(e.espaciado, TextUnitType.Em) else TextUnit.Unspecified,
        color = Color(e.color.toInt()),
        textAlign = if (centrado) TextAlign.Center else TextAlign.Start,
        lineHeightStyle = alineacionLibro,
    )

    private fun medir(texto: String, e: EstiloTexto, escala: Float, centrado: Boolean, ancho: Float?): TextLayoutResult {
        val t = if (e.mayusculas) texto.uppercase(es) else texto
        val st = estilo(e, escala, centrado)
        return if (ancho != null) {
            medidor.measure(t, st, constraints = Constraints.fixedWidth(floor(ancho).toInt()))
        } else {
            medidor.measure(t, st, softWrap = false)
        }
    }

    private fun entraEnAncho(l: TextLayoutResult, ancho: Float) = l.multiParagraph.minIntrinsicWidth <= ancho + 0.5f

    /** Apila renglones de arriba a abajo con sus márgenes. */
    private fun apilar(
        renglones: List<Renglon>, tip: local.museumslideshow.temas.Tipografia, ancho: Float,
        escala: Float, centrado: Boolean, y0: Float = 0f, margenPrimero: Boolean = false,
        estiloDe: (Renglon) -> EstiloTexto = { tip[it.rol] },
    ): Triple<List<BloqueTexto>, Float, Boolean> {
        var y = y0
        var entra = true
        val bloques = ArrayList<BloqueTexto>(renglones.size)
        renglones.forEachIndexed { i, r ->
            val e = estiloDe(r)
            if (i > 0 || margenPrimero) y += r.margenSup ?: e.margenSuperiorPx
            val l = medir(r.texto, e, escala, centrado, ancho)
            if (!entraEnAncho(l, ancho)) entra = false
            bloques += BloqueTexto(l, 0f, y)
            y += l.size.height + r.margenInf
        }
        return Triple(bloques, y, entra)
    }

    fun cartel(
        ficha: Ficha, nivel: NivelInfo, conAvisoOk: Boolean, ancho: Float, tema: TemaDef, modo: Modo, conCredito: Boolean,
    ): TextoMedido {
        val escala = if (ancho < Constantes.ANCHO_CARTEL_CHICO) Constantes.ESCALA_CARTEL_CHICO else 1f
        val filas = Renglones.cartel(ficha, nivel, conAvisoOk, avisoOk, formatos, conCredito)
        val (bloques, alto, entra) = apilar(filas, tema.tipografia(modo), ancho, escala, centrado = false)
        return TextoMedido(bloques, ancho, alto, entra)
    }

    fun nombre(ficha: Ficha, ancho: Float, tema: TemaDef, modo: Modo): TextoMedido {
        val filas = Renglones.nombre(ficha, formatos)
        val (bloques, alto, entra) = apilar(filas, tema.tipografia(modo), ancho, 1f, centrado = true)
        return TextoMedido(bloques, ancho, alto, entra)
    }

    /** El cuadrito flotante: ancho justo al texto (máx. 440 con el relleno). Los bloques ya vienen corridos por el relleno. */
    fun chip(ficha: Ficha, tema: TemaDef, modo: Modo): TextoMedido {
        val tip = tema.tipografia(modo)
        val rh = tip.rellenoChipH
        val rv = tip.rellenoChipV
        val filas = Renglones.chip(ficha, formatos)
        val maxTexto = 440f - 2 * rh
        var ancho = 0f
        for (r in filas) {
            val l = medir(r.texto, tip[r.rol], 1f, false, null)
            ancho = maxOf(ancho, ceil(l.size.width.toFloat()))
        }
        ancho = min(ancho, maxTexto)
        val (bloques, alto, entra) = apilar(filas, tip, ancho, 1f, centrado = false)
        val corridos = bloques.map { BloqueTexto(it.layout, it.x + rh, it.y + rv) }
        return TextoMedido(corridos, ancho + 2 * rh, alto + 2 * rv, entra)
    }

    /** Una línea suelta (nombre de la sala o contador) con su cajita si el tema la tiene. */
    private fun lineaSuelta(texto: String, rol: Rol, tema: TemaDef, modo: Modo): TextoMedido {
        val tip = tema.tipografia(modo)
        val e = if (tip.tiene(rol)) tip[rol] else tip[Rol.SALA]
        val l = medir(texto, e, 1f, false, null)
        val w = l.size.width.toFloat() + 2 * e.rellenoH
        val h = l.size.height.toFloat() + 2 * e.rellenoV
        return TextoMedido(listOf(BloqueTexto(l, e.rellenoH, e.rellenoV)), w, h, true)
    }

    fun sala(texto: String, tema: TemaDef, modo: Modo) = lineaSuelta(texto, Rol.SALA, tema, modo)
    fun contador(texto: String, tema: TemaDef, modo: Modo) = lineaSuelta(texto, Rol.CONTADOR, tema, modo)

    /**
     * La columna derecha del Libro de arte: renglón corrido (alto fijo de 26), título, autor, año, movimiento,
     * técnica · medidas, ubicación, [línea + explicación a `tamanoExplicacionPx`], crédito y, si falta la explicación,
     * el aviso de OK.
     */
    fun paginaLibro(
        ficha: Ficha, corrido: String?, tamanoExplicacionPx: Float?, conAvisoOk: Boolean, ancho: Float, modo: Modo,
        conCredito: Boolean,
    ): PaginaMedida {
        val tip = RegistroTemas.de(Tema.LIBRO_DE_ARTE).tipografia(modo)
        val bloques = ArrayList<BloqueTexto>()
        var entra = true
        if (!corrido.isNullOrBlank()) {
            val l = medir(corrido, tip[Rol.LIBRO_CORRIDO], 1f, false, null)
            bloques += BloqueTexto(l, 0f, 0f)
        }
        var y = ALTO_CORRIDO
        val cab = apilar(Renglones.libroCabecera(ficha, formatos), tip, ancho, 1f, false, y, margenPrimero = true)
        bloques += cab.first; y = cab.second; if (!cab.third) entra = false

        var reglaY: Float? = null
        val explicacion = ficha.explicacion?.trim()?.takeIf { it.isNotEmpty() }
        if (tamanoExplicacionPx != null && explicacion != null) {
            val regla = tip[Rol.LIBRO_REGLA]
            y += regla.margenSuperiorPx
            reglaY = y
            y += regla.tamanoPx + regla.margenSuperiorPx
            val base = tip[Rol.LIBRO_EXPLICACION]
            val e = base.copy(tamanoPx = tamanoExplicacionPx, interlineadoPx = tamanoExplicacionPx * 1.42f)
            val ex = apilar(listOf(Renglon(Rol.LIBRO_EXPLICACION, explicacion, 0f)), tip, ancho, 1f, false, y) { e }
            bloques += ex.first; y = ex.second; if (!ex.third) entra = false
        }
        Renglones.libroCredito(ficha, conCredito)?.let {
            val c = apilar(listOf(it), tip, ancho, 1f, false, y, margenPrimero = true)
            bloques += c.first; y = c.second; if (!c.third) entra = false
        }
        if (conAvisoOk && tamanoExplicacionPx == null) {
            val o = apilar(listOf(Renglon(Rol.LIBRO_AVISO_OK, avisoOk)), tip, ancho, 1f, false, y, margenPrimero = true)
            bloques += o.first; y = o.second; if (!o.third) entra = false
        }
        return PaginaMedida(bloques, reglaY, y, entra)
    }

    companion object {
        /** Alto fijo del renglón corrido de la página (17 px x 1,53). */
        const val ALTO_CORRIDO = 26f
    }
}

/**
 * El medidor que usa el Acomodador: pregunta a [ComposicionTexto] (la misma que arma lo que se dibuja).
 * Guarda los últimos resultados porque el Acomodador mide el mismo cartel varias veces.
 */
class MedidorTextoCompose(private val composicion: ComposicionTexto) : MedidorTexto {
    private data class Clave(val tipo: Int, val a: Any?, val b: Any?, val c: Any?, val d: Any?, val e: Any?)

    private val cache = object : LinkedHashMap<Clave, Any>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Clave, Any>?) = size > 96
    }

    @Suppress("UNCHECKED_CAST")
    private fun <T : Any> recordar(k: Clave, f: () -> T): T = (cache[k] as T?) ?: f().also { cache[k] = it }

    fun cartel(ficha: Ficha, nivel: NivelInfo, aviso: Boolean, ancho: Float, tema: TemaDef, modo: Modo, conCredito: Boolean): TextoMedido =
        recordar(Clave(1, ficha, Triple(nivel, aviso, conCredito), ancho, tema.id, modo)) {
            composicion.cartel(ficha, nivel, aviso, ancho, tema, modo, conCredito)
        }

    fun nombre(ficha: Ficha, ancho: Float, tema: TemaDef, modo: Modo): TextoMedido =
        recordar(Clave(2, ficha, ancho, tema.id, modo, null)) { composicion.nombre(ficha, ancho, tema, modo) }

    fun chip(ficha: Ficha, tema: TemaDef, modo: Modo): TextoMedido =
        recordar(Clave(3, ficha, tema.id, modo, null, null)) { composicion.chip(ficha, tema, modo) }

    fun sala(texto: String, tema: TemaDef, modo: Modo): TextoMedido =
        recordar(Clave(4, texto, tema.id, modo, null, null)) { composicion.sala(texto, tema, modo) }

    fun contador(texto: String, tema: TemaDef, modo: Modo): TextoMedido =
        recordar(Clave(5, texto, tema.id, modo, null, null)) { composicion.contador(texto, tema, modo) }

    fun pagina(ficha: Ficha, corrido: String?, tam: Float?, aviso: Boolean, ancho: Float, modo: Modo, conCredito: Boolean): PaginaMedida =
        recordar(Clave(6, ficha, corrido to tam, aviso to conCredito, ancho, modo)) {
            composicion.paginaLibro(ficha, corrido, tam, aviso, ancho, modo, conCredito)
        }

    override fun medirCartel(
        ficha: Ficha, nivel: NivelInfo, conAvisoOk: Boolean, ancho: Float, tema: TemaDef, modo: Modo, conCredito: Boolean,
    ): MedidaTexto {
        val m = cartel(ficha, nivel, conAvisoOk, ancho, tema, modo, conCredito)
        return MedidaTexto(ancho, m.alto, m.entra)
    }

    override fun medirNombre(ficha: Ficha, ancho: Float, tema: TemaDef, modo: Modo): MedidaTexto {
        val m = nombre(ficha, ancho, tema, modo)
        return MedidaTexto(ancho, m.alto, m.entra)
    }

    override fun medirChip(ficha: Ficha, tema: TemaDef, modo: Modo): MedidaTexto {
        val m = chip(ficha, tema, modo)
        return MedidaTexto(m.ancho, m.alto, m.entra)
    }

    override fun medirSala(texto: String, tema: TemaDef, modo: Modo): MedidaTexto {
        val m = sala(texto, tema, modo)
        return MedidaTexto(m.ancho, m.alto, m.entra)
    }

    override fun medirContador(texto: String, tema: TemaDef, modo: Modo): MedidaTexto {
        val m = contador(texto, tema, modo)
        return MedidaTexto(m.ancho, m.alto, m.entra)
    }

    override fun medirPaginaLibro(
        ficha: Ficha, nombreSala: String?, conExplicacion: Boolean, tamanoExplicacionPx: Float,
        conAvisoOk: Boolean, ancho: Float, modo: Modo, conCredito: Boolean,
    ): MedidaTexto {
        val p = pagina(ficha, nombreSala, if (conExplicacion) tamanoExplicacionPx else null, conAvisoOk, ancho, modo, conCredito)
        return MedidaTexto(ancho, p.alto, p.entra)
    }
}
