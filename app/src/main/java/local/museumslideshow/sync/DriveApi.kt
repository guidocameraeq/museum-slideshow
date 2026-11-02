package local.museumslideshow.sync

import java.io.File

data class ArchivoDrive(
    val id: String,
    val nombre: String,
    val mimeType: String,
    val md5: String?,
    val modificado: String?,
    val tamano: Long?,
) {
    val esCarpeta: Boolean get() = mimeType == MIME_CARPETA

    companion object {
        const val MIME_CARPETA = "application/vnd.google-apps.folder"
    }
}

/** Error de Drive. Jamás lleva la URL (que trae la clave): solo el código HTTP y el ID. */
class ErrorDrive(val codigoHttp: Int?, val archivoId: String) :
    Exception("Drive: HTTP ${codigoHttp ?: "sin respuesta"} (id $archivoId)")

/** El archivo pasó el tope de bytes permitido mientras se bajaba. */
class ArchivoExcedeTope(val archivoId: String) : Exception("Archivo más grande que el tope (id $archivoId)")

interface DriveApi {
    /** Falso si no hay clave configurada. */
    val disponible: Boolean get() = true

    fun listar(carpetaId: String): List<ArchivoDrive>

    /** Baja el archivo a `destino`; si pasa `maxBytes` corta y lanza [ArchivoExcedeTope]. */
    fun bajar(archivoId: String, destino: File, maxBytes: Long)
}
