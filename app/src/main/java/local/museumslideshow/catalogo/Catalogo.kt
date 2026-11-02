package local.museumslideshow.catalogo

import android.util.Log
import java.io.File
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import local.museumslideshow.Registro

/** Índice que deja el sincronizador en `dirBase/indice.json`. `ancho`/`alto` son los que dio Drive (0 = no los dio). */
@Serializable
data class ArchivoIdx(
    val id: String,
    val nombre: String,
    val md5: String? = null,
    val modificado: String? = null,
    val ancho: Int = 0,
    val alto: Int = 0,
)

/** De qué obra (y de qué versión de ella) es la tapa guardada de una galería. */
@Serializable
data class TapaIdx(
    val obraId: String,
    val md5: String? = null,
    val modificado: String? = null,
)

/** Una carpeta con subcarpetas. `padreId` null = cuelga de la carpeta raíz. */
@Serializable
data class GrupoIdx(
    val id: String,
    val nombre: String,
    val padreId: String? = null,
)

@Serializable
data class GaleriaIdx(
    val id: String,
    val nombre: String,
    val json: ArchivoIdx? = null,
    val imagenes: List<ArchivoIdx> = emptyList(),
    /** El grupo que la contiene; null = cuelga de la carpeta raíz (también lo que dejó el índice viejo). */
    val padreId: String? = null,
    val tapa: TapaIdx? = null,
)

@Serializable
data class IndiceLocal(
    val galerias: List<GaleriaIdx> = emptyList(),
    val problemas: List<Problema> = emptyList(),
    val ultimaSincronizacionBuena: Long? = null,
    /** Hora (ms) del último intento de sincronizar, bueno o malo. */
    val ultimoIntento: Long? = null,
    /** Si el último intento salió bien (sin errores de red). */
    val ultimoIntentoOk: Boolean? = null,
    /** Si el último intento falló antes de empezar (no se pudo listar la carpeta raíz, falta la clave): no toca galerías ni problemas. */
    val problemaUltimoIntento: Problema? = null,
    /** El árbol: las carpetas con subcarpetas. El índice viejo no las trae (todo cuelga de la raíz). */
    val grupos: List<GrupoIdx> = emptyList(),
)

object Catalogo {
    val JSON = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    fun archivoIndice(dir: File) = File(dir, "indice.json")
    fun archivoObra(dir: File, id: String) = File(File(dir, "obras"), "$id.jpg")
    fun archivoObraChica(dir: File, id: String) = File(File(dir, "obras"), "$id.chica.jpg")
    fun archivoJson(dir: File, id: String) = File(File(dir, "json"), "$id.json")

    /** La tapa de una galería: una copia chica (~480 px) de su primera obra. */
    fun archivoTapa(dir: File, galeriaId: String) = File(File(dir, "tapas"), "$galeriaId.jpg")

    /** Un índice ilegible (o de un formato que no se entiende) se trata como vacío: la primera sincronización lo rehace. */
    fun leerIndice(dir: File): IndiceLocal = try {
        val f = archivoIndice(dir)
        if (f.isFile) JSON.decodeFromString(IndiceLocal.serializer(), f.readText()) else IndiceLocal()
    } catch (e: Exception) {
        Log.w(Registro.TAG, "Índice local ilegible (${e.javaClass.simpleName})")
        IndiceLocal()
    }

    /**
     * Arma las galerías desde el índice AUNQUE la obra no esté bajada: la obra lleva las medidas de Drive (o, si Drive no las
     * dio, las de la copia, que se miden con `medirCopia`) y si su copia está en la tele. Sin ninguna medida queda "sin medidas".
     */
    fun cargar(dir: File, medirCopia: (File) -> Pair<Int, Int>? = { null }): CatalogoCargado {
        val indice = leerIndice(dir)
        val problemas = ArrayList<Problema>(indice.problemas)
        val porId = indice.grupos.associateBy { it.id }
        val galerias = ArrayList<Galeria>()
        for (g in indice.galerias) {
            val jsonTexto = g.json?.let { j ->
                try {
                    archivoJson(dir, j.id).takeIf { it.isFile }?.readText(Charsets.UTF_8)
                } catch (e: Exception) {
                    null
                }
            }
            val bajadas = HashSet<String>()
            val imagenes = g.imagenes.map { i ->
                val copia = archivoObra(dir, i.id)
                val estaBajada = copia.isFile
                if (estaBajada) bajadas += i.id
                var ancho = i.ancho
                var alto = i.alto
                if ((ancho <= 0 || alto <= 0) && estaBajada) {
                    val m = try { medirCopia(copia) } catch (e: Exception) { null }
                    if (m != null) { ancho = m.first; alto = m.second }
                }
                ImagenLocal(i.id, i.nombre, maxOf(ancho, 0), maxOf(alto, 0))
            }
            val r = LectorGaleria.leer(g.id, g.nombre, jsonTexto, imagenes)
            val ruta = rutaDe(g.padreId, porId)
            galerias += r.galeria.copy(
                obras = r.galeria.obras.map { it.conBajada(it.driveId in bajadas) },
                padreId = g.padreId,
                ruta = ruta,
            )
            problemas += r.problemas
        }
        galerias.sortWith { a, b -> String.CASE_INSENSITIVE_ORDER.compare(a.claveOrden(), b.claveOrden()) }
        val grupos = indice.grupos.map { Grupo(it.id, it.nombre, it.padreId, rutaDe(it.padreId, porId)) }
            .sortedWith { a, b -> String.CASE_INSENSITIVE_ORDER.compare(a.ruta.joinToString("/") + "/" + a.titulo, b.ruta.joinToString("/") + "/" + b.titulo) }
        return CatalogoCargado(
            galerias, problemas, indice.ultimaSincronizacionBuena,
            indice.ultimoIntento, indice.ultimoIntentoOk, indice.problemaUltimoIntento, grupos,
        )
    }

    private fun Galeria.claveOrden() = if (ruta.isEmpty()) titulo else ruta.joinToString("/") + "/" + titulo

    /** Los nombres de los grupos desde arriba hasta el padre directo (con tope por si el índice trae un ciclo). */
    private fun rutaDe(padreId: String?, porId: Map<String, GrupoIdx>): List<String> {
        val ruta = ArrayList<String>()
        var id = padreId
        var vueltas = 0
        while (id != null && vueltas < 10) {
            val g = porId[id] ?: break
            ruta.add(0, g.nombre)
            id = g.padreId
            vueltas++
        }
        return ruta
    }
}
