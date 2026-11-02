package local.museumslideshow.pantallas

import android.content.res.Resources
import androidx.annotation.StringRes
import local.museumslideshow.R
import local.museumslideshow.catalogo.CatalogoCargado
import local.museumslideshow.catalogo.Problema
import local.museumslideshow.catalogo.TipoProblema

/**
 * Traduce cada [Problema] a una frase en criollo de `strings.xml` (Guido no es programador: nunca se muestra
 * "JSON_INVALIDO"). Las frases reciben siempre los mismos datos: 1 = galería, 2 = archivo, 3 = detalle.
 */
object TextosProblemas {
    /** La frase que corresponde a este problema (el error de red tiene tres versiones según qué falló). */
    @StringRes
    fun clave(p: Problema): Int = when (p.tipo) {
        TipoProblema.JSON_INVALIDO -> R.string.problema_json_invalido
        TipoProblema.JSON_SIN_FORMATO -> R.string.problema_json_sin_formato
        TipoProblema.FORMATO_NUEVO -> R.string.problema_formato_nuevo
        TipoProblema.ARCHIVO_FALTANTE -> R.string.problema_archivo_faltante
        TipoProblema.ARCHIVO_REPETIDO -> R.string.problema_archivo_repetido
        TipoProblema.ID_REPETIDO -> R.string.problema_id_repetido
        TipoProblema.OBRA_SIN_ARCHIVO -> R.string.problema_obra_sin_archivo
        TipoProblema.IMAGEN_MUY_GRANDE -> R.string.problema_imagen_muy_grande
        TipoProblema.NO_ES_IMAGEN -> R.string.problema_no_es_imagen
        TipoProblema.JSON_MUY_GRANDE -> R.string.problema_json_muy_grande
        TipoProblema.IMAGEN_NO_DECODIFICA -> R.string.problema_imagen_no_decodifica
        TipoProblema.ESPACIO_LLENO -> R.string.problema_espacio_lleno
        TipoProblema.ERROR_RED -> when {
            p.galeria.isBlank() -> R.string.problema_red_drive
            p.archivo == null -> R.string.problema_red_carpeta
            else -> R.string.problema_red_archivo
        }
        TipoProblema.SIN_CLAVE -> if (p.detalle == "carpeta") R.string.problema_sin_carpeta else R.string.problema_sin_clave
    }

    fun frase(res: Resources, p: Problema): String =
        res.getString(clave(p), p.galeria, p.archivo ?: "", detalleLegible(res, p))

    /** El código HTTP como "error 503", "red" como "sin conexión"; cualquier otro detalle tal cual. */
    private fun detalleLegible(res: Resources, p: Problema): String {
        val d = p.detalle ?: return ""
        return when {
            p.tipo == TipoProblema.ERROR_RED && d == "red" -> res.getString(R.string.detalle_sin_conexion)
            p.tipo == TipoProblema.ERROR_RED && d.all { it.isDigit() } -> res.getString(R.string.detalle_error_http, d)
            else -> d
        }
    }

    /** Los problemas agrupados por galería (la general, sin nombre, va primera), cada grupo en el orden en que aparecen. */
    fun agrupar(problemas: List<Problema>): List<Pair<String, List<Problema>>> =
        problemas.groupBy { it.galeria }.toList().sortedBy { (g, _) -> if (g.isBlank()) 0 else 1 }
}

/** Por qué no hay obras para mostrar (para elegir el mensaje de la pantalla vacía). */
enum class MotivoSinObras { SINCRONIZANDO, SIN_CLAVE, SIN_CONEXION, CARPETA_VACIA }

object SinObras {
    /** Sin obras: si se está sincronizando, la espera; si falló la red o falta la clave, eso; si no, la carpeta está vacía. */
    fun motivo(sincronizando: Boolean, cat: CatalogoCargado?): MotivoSinObras = when {
        sincronizando -> MotivoSinObras.SINCRONIZANDO
        cat == null -> MotivoSinObras.SINCRONIZANDO
        cat.problemaUltimoIntento?.tipo == TipoProblema.SIN_CLAVE -> MotivoSinObras.SIN_CLAVE
        cat.problemaUltimoIntento?.tipo == TipoProblema.ERROR_RED || cat.problemas.any { it.tipo == TipoProblema.ERROR_RED } ->
            MotivoSinObras.SIN_CONEXION
        cat.ultimoIntento == null -> MotivoSinObras.SINCRONIZANDO
        else -> MotivoSinObras.CARPETA_VACIA
    }
}
