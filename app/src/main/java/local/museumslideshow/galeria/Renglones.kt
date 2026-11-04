package local.museumslideshow.galeria

import local.museumslideshow.acomodo.NivelInfo
import local.museumslideshow.catalogo.Ficha
import local.museumslideshow.temas.Rol

/**
 * Un renglón de un cartel: el rol (de él sale la letra), el texto y, si hace falta, márgenes propios
 * (si `margenSup` es null vale el de la tipografía; `margenInf` es aire extra debajo).
 */
data class Renglon(val rol: Rol, val texto: String, val margenSup: Float? = null, val margenInf: Float = 0f)

/** Los formatos de texto que dependen del idioma; en la app salen de `strings.xml`. */
class Formatos(
    val autorConVida: (String, String) -> String,
    val autorYAnio: (String, String) -> String,
    val tecnicaYMedidas: (String, String) -> String,
)

/**
 * Qué renglones lleva cada cartel. Los datos que faltan en la ficha no dejan renglón (sin huecos).
 * Lógica pura: no mide ni dibuja.
 */
object Renglones {
    private const val AIRE_SEGUNDO_DATO = 2f
    private const val AIRE_LIBRO_TITULO_ARRIBA = 12f
    private const val AIRE_LIBRO_TITULO_ABAJO = 6f
    private const val AIRE_LIBRO_TECNICA = 6f

    private fun limpio(s: String?): String? = s?.trim()?.takeIf { it.isNotEmpty() }

    /** "Autor, año" (o solo lo que haya). */
    fun autorYAnio(f: Ficha, fm: Formatos): String? {
        val a = limpio(f.autor)
        val y = limpio(f.anio)
        return when {
            a != null && y != null -> fm.autorYAnio(a, y)
            else -> a ?: y
        }
    }

    private fun autorConVida(f: Ficha, fm: Formatos): String? {
        val a = limpio(f.autor) ?: return null
        val v = limpio(f.vidaAutor)
        return if (v != null) fm.autorConVida(a, v) else a
    }

    private fun tecnicaYMedidas(f: Ficha, fm: Formatos): String? {
        val t = limpio(f.tecnica)
        val m = limpio(f.medidas)
        return when {
            t != null && m != null -> fm.tecnicaYMedidas(t, m)
            else -> t ?: m
        }
    }

    /**
     * Cartel al costado o abajo de la obra. MINIMA = título + "autor, año". FICHA y COMPLETA suman autor (vida),
     * año, movimiento, técnica · medidas, ubicación y crédito; COMPLETA suma además la explicación.
     */
    fun cartel(
        ficha: Ficha, nivel: NivelInfo, conAvisoOk: Boolean, avisoOk: String, fm: Formatos, conCredito: Boolean,
    ): List<Renglon> {
        val r = ArrayList<Renglon>()
        limpio(ficha.titulo)?.let { r += Renglon(Rol.TITULO, it) }
        if (nivel == NivelInfo.MINIMA) {
            autorYAnio(ficha, fm)?.let { r += Renglon(Rol.AUTOR, it) }
        } else {
            autorConVida(ficha, fm)?.let { r += Renglon(Rol.AUTOR, it) }
            limpio(ficha.anio)?.let { r += Renglon(Rol.ANIO, it) }
            limpio(ficha.movimiento)?.let { r += Renglon(Rol.MOVIMIENTO, it) }
            var hayDato = false
            tecnicaYMedidas(ficha, fm)?.let { r += Renglon(Rol.DATOS, it); hayDato = true }
            limpio(ficha.ubicacion)?.let { r += Renglon(Rol.DATOS, it, if (hayDato) AIRE_SEGUNDO_DATO else null) }
            if (nivel == NivelInfo.COMPLETA) limpio(ficha.explicacion)?.let { r += Renglon(Rol.EXPLICACION, it) }
            if (conCredito) limpio(ficha.credito)?.let { r += Renglon(Rol.CREDITO, it) }
        }
        if (conAvisoOk) r += Renglon(Rol.AVISO_OK, avisoOk)
        return r
    }

    /** Nombre bajo cada obra de una Pared. */
    fun nombre(ficha: Ficha, fm: Formatos): List<Renglon> {
        val r = ArrayList<Renglon>()
        limpio(ficha.titulo)?.let { r += Renglon(Rol.NOMBRE_TITULO, it) }
        autorYAnio(ficha, fm)?.let { r += Renglon(Rol.NOMBRE_AUTOR, it) }
        return r
    }

    /** Cuadrito flotante. */
    fun chip(ficha: Ficha, fm: Formatos): List<Renglon> {
        val r = ArrayList<Renglon>()
        limpio(ficha.titulo)?.let { r += Renglon(Rol.CHIP_TITULO, it) }
        autorYAnio(ficha, fm)?.let { r += Renglon(Rol.CHIP_AUTOR, it) }
        return r
    }

    /** La parte de arriba de la página del Libro de arte (después del renglón corrido). */
    fun libroCabecera(ficha: Ficha, fm: Formatos): List<Renglon> {
        val r = ArrayList<Renglon>()
        limpio(ficha.titulo)?.let {
            r += Renglon(Rol.LIBRO_TITULO, it, AIRE_LIBRO_TITULO_ARRIBA, AIRE_LIBRO_TITULO_ABAJO)
        }
        autorConVida(ficha, fm)?.let { r += Renglon(Rol.LIBRO_AUTOR, it) }
        limpio(ficha.anio)?.let { r += Renglon(Rol.LIBRO_DATO, it, 0f) }
        limpio(ficha.movimiento)?.let { r += Renglon(Rol.LIBRO_MOVIMIENTO, it) }
        var hayDato = false
        tecnicaYMedidas(ficha, fm)?.let { r += Renglon(Rol.LIBRO_DATO, it, AIRE_LIBRO_TECNICA); hayDato = true }
        limpio(ficha.ubicacion)?.let { r += Renglon(Rol.LIBRO_DATO, it, if (hayDato) 0f else AIRE_LIBRO_TECNICA) }
        return r
    }

    fun libroCredito(ficha: Ficha, conCredito: Boolean): Renglon? =
        if (!conCredito) null else limpio(ficha.credito)?.let { Renglon(Rol.LIBRO_CREDITO, it) }
}
