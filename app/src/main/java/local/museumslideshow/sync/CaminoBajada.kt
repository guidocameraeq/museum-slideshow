package local.museumslideshow.sync

import java.io.File
import local.museumslideshow.ajustes.CaminoObras

/** Por dónde se le pide algo a Google: el ORIGINAL (la API: listado y `alt=media`, incluye los JSON) o la COPIA (`lh3`, la miniatura grande). */
enum class CaminoDrive(val clave: String) { ORIGINAL("original"), COPIA("copia") }

/** Cómo salió una bajada de obra, para el registro. Nunca lleva URLs. */
data class ViaBajada(val camino: CaminoDrive, val lado: Int?) {
    val etiqueta: String get() = if (camino == CaminoDrive.COPIA) "copia s$lado" else "original"
}

/** Arma la URL de la copia de Drive a partir del `thumbnailLink`. Función pura. */
object UrlCopia {
    /** El lado largo de la copia que se pide según la "Calidad de la obra". */
    const val LADO_4K = 3840
    const val LADO_1080 = 1920

    private val FINAL_DE_TAMANO = Regex("=[A-Za-z]\\d+[-A-Za-z0-9]*$")

    /** Cambia el tamaño del final (`=s220` -> `=s3840`; si no tiene, lo agrega). Null si el link no es de googleusercontent. */
    fun armar(thumbnailLink: String, lado: Int): String? {
        val enlace = thumbnailLink.trim()
        if (!enlace.startsWith("https://") || lado <= 0) return null
        val host = enlace.removePrefix("https://").substringBefore('/').substringBefore('?').substringBefore('#').lowercase()
        if (host != "googleusercontent.com" && !host.endsWith(".googleusercontent.com")) return null
        val corte = enlace.indexOfFirst { it == '?' || it == '#' }
        val ruta = if (corte < 0) enlace else enlace.substring(0, corte)
        val resto = if (corte < 0) "" else enlace.substring(corte)
        val nueva = if (FINAL_DE_TAMANO.containsMatchIn(ruta)) ruta.replace(FINAL_DE_TAMANO, "=s$lado") else "$ruta=s$lado"
        return nueva + resto
    }
}

/**
 * Baja una imagen probando los dos caminos en el orden elegido: el primero que anda gana; si falla con un error que no es de
 * red (no hay copia, 404, no era una imagen) o está en pausa, prueba el otro. Si ninguno anda y alguno estaba en pausa, avisa la
 * pausa (la obra espera, no es un error).
 */
class BajadorConRespaldo(
    private val api: DriveApi,
    private val orden: () -> CaminoObras = { CaminoObras.COPIA_PRIMERO },
) {
    fun bajar(archivoId: String, lado: Int, destino: File, maxBytes: Long): ViaBajada {
        val caminos = if (orden() == CaminoObras.ORIGINAL_PRIMERO) listOf(CaminoDrive.ORIGINAL, CaminoDrive.COPIA)
        else listOf(CaminoDrive.COPIA, CaminoDrive.ORIGINAL)
        val pausas = ArrayList<PausaDriveException>()
        var errorOriginal: ErrorDrive? = null
        var errorCopia: ErrorDrive? = null
        for (camino in caminos) {
            try {
                if (camino == CaminoDrive.ORIGINAL) {
                    api.bajar(archivoId, destino, maxBytes)
                    return ViaBajada(CaminoDrive.ORIGINAL, null)
                }
                api.bajarCopia(archivoId, lado, destino, maxBytes)
                return ViaBajada(CaminoDrive.COPIA, lado)
            } catch (e: PausaDriveException) {
                destino.delete()
                pausas += e
            } catch (e: ErrorDrive) {
                destino.delete()
                if (e.codigoHttp == null) throw e // sin internet: probar el otro camino no sirve
                if (camino == CaminoDrive.ORIGINAL) errorOriginal = e else errorCopia = e
            }
        }
        if (pausas.isNotEmpty()) {
            val p = pausas.maxByOrNull { it.hastaMs }!!
            throw PausaDriveException(p.hastaMs, p.motivo, p.camino, todosEnPausa = pausas.size >= 2)
        }
        throw errorOriginal ?: errorCopia ?: ErrorDrive(null, archivoId)
    }
}
