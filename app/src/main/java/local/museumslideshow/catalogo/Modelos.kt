package local.museumslideshow.catalogo

import kotlinx.serialization.Serializable

enum class Forma { RECTANGULAR, REDONDA, OVALADA }

data class Ficha(
    val titulo: String?,
    val autor: String?,
    val vidaAutor: String?,
    val anio: String?,
    val movimiento: String?,
    val tecnica: String?,
    val medidas: String?,
    val ubicacion: String?,
    val forma: Forma,
    val explicacion: String?,
    val marco: String?,
    val credito: String?,
    val licencia: String?,
    val fuente: String?,
    val id: String?,
)

/**
 * `ancho`/`alto` son los que dio Drive (o, si no los dio, los de la copia guardada); 0 = sin medidas.
 * `bajada` = la copia ya estaba en la tele cuando se leyó el catálogo. Es una foto de ese momento y NO entra en la
 * igualdad (dos lecturas de la misma obra son la misma obra aunque una se haya bajado entre medio: la escena no se rehace).
 */
data class Obra(
    val driveId: String,
    val archivo: String,
    val ancho: Int,
    val alto: Int,
    val ficha: Ficha?,
) {
    var bajada: Boolean = true
        private set

    /** Sin medidas la obra no puede acomodarse en el pase: espera a bajarse. */
    val conMedidas: Boolean get() = ancho > 0 && alto > 0

    fun conBajada(b: Boolean): Obra = copy().also { it.bajada = b }
}

/** `padreId` = el grupo (carpeta) que la contiene, null si cuelga de la carpeta raíz; `ruta` = los nombres de los grupos de arriba. */
data class Galeria(
    val driveId: String,
    val titulo: String,
    val descripcion: String?,
    val obras: List<Obra>,
    val padreId: String? = null,
    val ruta: List<String> = emptyList(),
)

/** Una carpeta con subcarpetas (puede ser también galería). */
data class Grupo(
    val driveId: String,
    val titulo: String,
    val padreId: String? = null,
    val ruta: List<String> = emptyList(),
)

data class ImagenLocal(val driveId: String, val nombre: String, val ancho: Int, val alto: Int)

@Serializable
enum class TipoProblema {
    // Del lector
    JSON_INVALIDO, JSON_SIN_FORMATO, FORMATO_NUEVO, ARCHIVO_FALTANTE, ARCHIVO_REPETIDO, ID_REPETIDO, OBRA_SIN_ARCHIVO,

    // Del sincronizador
    IMAGEN_MUY_GRANDE, NO_ES_IMAGEN, JSON_MUY_GRANDE, IMAGEN_NO_DECODIFICA, ESPACIO_LLENO, ERROR_RED, SIN_CLAVE,
}

/** Un problema es un tipo + datos; la pantalla lo traduce con strings.xml (nunca se guardan frases). */
@Serializable
data class Problema(
    val galeria: String,
    val archivo: String?,
    val tipo: TipoProblema,
    val detalle: String?,
)

data class ResultadoLectura(val galeria: Galeria, val problemas: List<Problema>)

data class CatalogoCargado(
    val galerias: List<Galeria>,
    val problemas: List<Problema>,
    val ultimaSincronizacionBuena: Long?,
    val ultimoIntento: Long? = null,
    val ultimoIntentoOk: Boolean? = null,
    val problemaUltimoIntento: Problema? = null,
    val grupos: List<Grupo> = emptyList(),
)
