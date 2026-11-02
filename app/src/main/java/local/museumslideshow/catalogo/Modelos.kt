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

/** `ancho`/`alto` son los de la copia que se guardó en la tele. */
data class Obra(
    val driveId: String,
    val archivo: String,
    val ancho: Int,
    val alto: Int,
    val ficha: Ficha?,
)

data class Galeria(
    val driveId: String,
    val titulo: String,
    val descripcion: String?,
    val obras: List<Obra>,
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
)
