package local.museumslideshow.actualizacion

import local.museumslideshow.ajustes.BuscarActualizaciones
import org.json.JSONException
import org.json.JSONObject

/** Una versión publicada en GitHub Releases: su número, el código interno y de dónde se baja el APK. */
data class VersionPublicada(
    val version: String,
    val codigo: Int,
    val apkUrl: String,
    val apkBytes: Long,
    /** Qué trae, en texto plano (puede estar vacío). */
    val notas: String,
)

/** Lógica pura de las actualizaciones: leer la respuesta de GitHub, comparar versiones y cuándo toca buscar. */
object Versiones {
    /** El repo donde se publican las versiones (un solo lugar). */
    const val REPO = "guidocameraeq/museum-slideshow"
    const val URL_ULTIMA = "https://api.github.com/repos/$REPO/releases/latest"

    /** Cada cuánto busca sola como mucho ("una vez por día" con un poco de margen). */
    const val INTERVALO_MS = 20L * 60 * 60 * 1000

    /** El código interno de una versión, igual que `app/build.gradle.kts`: "0.3" -> 300, "v1.2.4" -> 10204. Null si no es un número. */
    fun codigo(texto: String): Int? {
        val partes = texto.trim().removePrefix("v").removePrefix("V").split('.')
        if (partes.isEmpty() || partes.size > 3) return null
        val n = partes.map { it.toIntOrNull()?.takeIf { x -> x in 0..99 } ?: return null }
        val p = n + List(3 - n.size) { 0 }
        return p[0] * 10000 + p[1] * 100 + p[2]
    }

    /** La respuesta de `releases/latest`. Null si no se entiende o la versión no trae un APK. */
    fun leerUltima(json: String): VersionPublicada? = try {
        val o = JSONObject(json)
        val tag = o.getString("tag_name")
        val codigo = codigo(tag)
        val assets = o.optJSONArray("assets")
        var url: String? = null
        var bytes = 0L
        if (assets != null) {
            for (i in 0 until assets.length()) {
                val a = assets.getJSONObject(i)
                if (a.optString("name").endsWith(".apk") && !a.isNull("browser_download_url")) {
                    url = a.getString("browser_download_url")
                    bytes = a.optLong("size", 0L)
                    break
                }
            }
        }
        if (codigo == null || url.isNullOrBlank()) null
        else VersionPublicada(tag.trim().removePrefix("v").removePrefix("V"), codigo, url, bytes, limpiarNotas(if (o.isNull("body")) "" else o.optString("body")))
    } catch (e: JSONException) {
        null
    }

    /** Las notas sin el renglón de "Full Changelog" ni títulos automáticos de GitHub; cortas para que entren en pantalla. */
    fun limpiarNotas(texto: String): String =
        texto.lines()
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("**Full Changelog") && !it.startsWith("## What") }
            .joinToString("\n")
            .take(600)

    fun hayNueva(instalada: Int, ultima: VersionPublicada?): Boolean = ultima != null && ultima.codigo > instalada

    /** ¿Toca buscar sola? Solo con "Una vez por día" y si pasaron ~20 h (o el reloj de la tele fue para atrás). */
    fun tocaBuscar(cada: BuscarActualizaciones, ultimaMs: Long?, ahoraMs: Long): Boolean =
        cada == BuscarActualizaciones.UNA_VEZ_POR_DIA && (ultimaMs == null || ahoraMs < ultimaMs || ahoraMs - ultimaMs >= INTERVALO_MS)
}
