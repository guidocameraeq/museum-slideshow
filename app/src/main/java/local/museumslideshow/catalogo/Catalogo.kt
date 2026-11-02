package local.museumslideshow.catalogo

import android.util.Log
import java.io.File
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import local.museumslideshow.Registro

/** Índice que deja el sincronizador en `dirBase/indice.json`. */
@Serializable
data class ArchivoIdx(
    val id: String,
    val nombre: String,
    val md5: String? = null,
    val modificado: String? = null,
    val ancho: Int = 0,
    val alto: Int = 0,
)

@Serializable
data class GaleriaIdx(
    val id: String,
    val nombre: String,
    val json: ArchivoIdx? = null,
    val imagenes: List<ArchivoIdx> = emptyList(),
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
)

object Catalogo {
    val JSON = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    fun archivoIndice(dir: File) = File(dir, "indice.json")
    fun archivoObra(dir: File, id: String) = File(File(dir, "obras"), "$id.jpg")
    fun archivoObraChica(dir: File, id: String) = File(File(dir, "obras"), "$id.chica.jpg")
    fun archivoJson(dir: File, id: String) = File(File(dir, "json"), "$id.json")

    fun leerIndice(dir: File): IndiceLocal = try {
        val f = archivoIndice(dir)
        if (f.isFile) JSON.decodeFromString(IndiceLocal.serializer(), f.readText()) else IndiceLocal()
    } catch (e: Exception) {
        Log.w(Registro.TAG, "Índice local ilegible (${e.javaClass.simpleName})")
        IndiceLocal()
    }

    fun cargar(dir: File): CatalogoCargado {
        val indice = leerIndice(dir)
        val problemas = ArrayList<Problema>(indice.problemas)
        val galerias = ArrayList<Galeria>()
        for (g in indice.galerias) {
            val jsonTexto = g.json?.let { j ->
                try {
                    archivoJson(dir, j.id).takeIf { it.isFile }?.readText(Charsets.UTF_8)
                } catch (e: Exception) {
                    null
                }
            }
            val imagenes = g.imagenes
                .filter { archivoObra(dir, it.id).isFile }
                .map { ImagenLocal(it.id, it.nombre, it.ancho, it.alto) }
            val r = LectorGaleria.leer(g.id, g.nombre, jsonTexto, imagenes)
            galerias += r.galeria
            problemas += r.problemas
        }
        galerias.sortWith { a, b -> String.CASE_INSENSITIVE_ORDER.compare(a.titulo, b.titulo) }
        return CatalogoCargado(
            galerias, problemas, indice.ultimaSincronizacionBuena,
            indice.ultimoIntento, indice.ultimoIntentoOk, indice.problemaUltimoIntento,
        )
    }
}
