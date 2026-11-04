package local.museumslideshow.sync

import java.io.File

data class ArchivoDrive(
    val id: String,
    val nombre: String,
    val mimeType: String,
    val md5: String?,
    val modificado: String?,
    val tamano: Long?,
    /** Medidas de la imagen según Drive (`imageMediaMetadata`); null si no las dio. `rotacion` en grados. */
    val ancho: Int? = null,
    val alto: Int? = null,
    val rotacion: Int? = null,
) {
    val esCarpeta: Boolean get() = mimeType == MIME_CARPETA

    /** Las medidas como se ve la imagen: con rotación de 90 o 270 grados el ancho y el alto se intercambian. Null si Drive no las dio. */
    fun medidasVistas(): Medidas? {
        val a = ancho ?: return null
        val h = alto ?: return null
        if (a <= 0 || h <= 0) return null
        val r = Math.floorMod(rotacion ?: 0, 360)
        return if (r == 90 || r == 270) Medidas(h, a) else Medidas(a, h)
    }

    companion object {
        const val MIME_CARPETA = "application/vnd.google-apps.folder"
    }
}

/** Error de Drive. Jamás lleva la URL (que trae la clave): solo el código HTTP y el ID. */
class ErrorDrive(val codigoHttp: Int?, val archivoId: String, val pausa: MotivoPausa? = null) :
    Exception("Drive: HTTP ${codigoHttp ?: "sin respuesta"} (id $archivoId)")

/** El archivo pasó el tope de bytes permitido mientras se bajaba. */
class ArchivoExcedeTope(val archivoId: String) : Exception("Archivo más grande que el tope (id $archivoId)")

interface DriveApi {
    /** Falso si no hay clave configurada. */
    val disponible: Boolean get() = true

    fun listar(carpetaId: String): List<ArchivoDrive>

    /** Baja el archivo a `destino`; si pasa `maxBytes` corta y lanza [ArchivoExcedeTope]. */
    fun bajar(archivoId: String, destino: File, maxBytes: Long)

    /**
     * Baja la COPIA que Drive guarda de la imagen (el `thumbnailLink` se pide fresco justo antes, y se baja a `lado` px de lado
     * largo). Si la obra no tiene copia, o la copia falla, lanza [ErrorDrive]; por defecto: "no hay copia".
     */
    fun bajarCopia(archivoId: String, lado: Int, destino: File, maxBytes: Long) {
        throw ErrorDrive(404, archivoId)
    }
}
