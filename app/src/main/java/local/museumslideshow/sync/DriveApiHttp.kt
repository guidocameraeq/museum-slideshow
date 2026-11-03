package local.museumslideshow.sync

import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.longOrNull

/**
 * Drive API v3 con clave de API (carpeta pública, sin login). La clave va en la URL, así que ninguna
 * excepción que salga de acá lleva el mensaje original de la red: solo [ErrorDrive].
 */
class DriveApiHttp(
    private val clave: String,
    private val esperar: (Long) -> Unit = { Thread.sleep(it) },
) : DriveApi {
    override val disponible: Boolean get() = clave.isNotBlank()

    override fun listar(carpetaId: String): List<ArchivoDrive> {
        val salida = ArrayList<ArchivoDrive>()
        var pagina: String? = null
        do {
            val q = URLEncoder.encode("'$carpetaId' in parents and trashed=false", "UTF-8")
            val campos = URLEncoder.encode("nextPageToken,files(id,name,mimeType,md5Checksum,modifiedTime,size,imageMediaMetadata(width,height,rotation))", "UTF-8")
            val url = StringBuilder("$BASE?q=$q&fields=$campos&pageSize=1000")
            if (pagina != null) url.append("&pageToken=").append(URLEncoder.encode(pagina, "UTF-8"))
            url.append("&key=").append(URLEncoder.encode(clave, "UTF-8"))
            val texto = conReintentos(carpetaId) { pedirTexto(url.toString(), carpetaId) }
            val raiz = try {
                Json.parseToJsonElement(texto).jsonObject
            } catch (e: Exception) {
                throw ErrorDrive(null, carpetaId)
            }
            (raiz["files"] as? JsonArray)?.forEach { el ->
                val o = el as? JsonObject ?: return@forEach
                val id = cadena(o, "id") ?: return@forEach
                val meta = o["imageMediaMetadata"] as? JsonObject
                salida += ArchivoDrive(
                    id = id,
                    nombre = cadena(o, "name") ?: id,
                    mimeType = cadena(o, "mimeType") ?: "",
                    md5 = cadena(o, "md5Checksum"),
                    modificado = cadena(o, "modifiedTime"),
                    tamano = (o["size"] as? JsonPrimitive)?.longOrNull,
                    ancho = entero(meta, "width"),
                    alto = entero(meta, "height"),
                    rotacion = entero(meta, "rotation"),
                )
            }
            pagina = cadena(raiz, "nextPageToken")
        } while (pagina != null)
        return salida
    }

    override fun bajar(archivoId: String, destino: File, maxBytes: Long) {
        val url = "$BASE/${URLEncoder.encode(archivoId, "UTF-8")}?alt=media&key=${URLEncoder.encode(clave, "UTF-8")}"
        conReintentos(archivoId) {
            var con: HttpURLConnection? = null
            try {
                con = abrir(url)
                val codigo = con.responseCode
                if (codigo != 200) throw errorDe(con, codigo, archivoId)
                val declarado = con.contentLengthLong
                if (declarado > maxBytes) throw ArchivoExcedeTope(archivoId)
                con.inputStream.use { entrada ->
                    FileOutputStream(destino).use { salida ->
                        val buf = ByteArray(64 * 1024)
                        var total = 0L
                        while (true) {
                            val n = entrada.read(buf)
                            if (n < 0) break
                            total += n
                            if (total > maxBytes) throw ArchivoExcedeTope(archivoId)
                            salida.write(buf, 0, n)
                        }
                    }
                }
            } catch (e: IOException) {
                destino.delete()
                throw ErrorDrive(null, archivoId)
            } catch (e: ArchivoExcedeTope) {
                destino.delete()
                throw e
            } catch (e: ErrorDrive) {
                destino.delete()
                throw e
            } finally {
                con?.disconnect()
            }
        }
    }

    private fun pedirTexto(url: String, id: String): String {
        var con: HttpURLConnection? = null
        try {
            con = abrir(url)
            val codigo = con.responseCode
            if (codigo != 200) throw errorDe(con, codigo, id)
            return con.inputStream.use { it.readBytes().toString(Charsets.UTF_8) }
        } catch (e: IOException) {
            throw ErrorDrive(null, id)
        } finally {
            con?.disconnect()
        }
    }

    /** El error de una respuesta no 200: si Google pidió una pausa lo dice (mirando el cuerpo); nada del cuerpo se guarda. */
    private fun errorDe(con: HttpURLConnection, codigo: Int, id: String): ErrorDrive {
        val cuerpo = if (codigo == 403) {
            try {
                con.errorStream?.use { it.readNBytes(16 * 1024).toString(Charsets.UTF_8) }
            } catch (e: Exception) {
                null
            }
        } else null
        return ErrorDrive(codigo, id, ClasificadorRespuestaDrive.clasificar(codigo, cuerpo))
    }

    private fun abrir(url: String): HttpURLConnection {
        val con = URL(url).openConnection() as HttpURLConnection
        con.connectTimeout = 15_000
        con.readTimeout = 30_000
        con.requestMethod = "GET"
        return con
    }

    /** 3 intentos con espera 1 s, 2 s, 4 s; solo ante error de red, 429 o 5xx. */
    private fun <T> conReintentos(id: String, bloque: () -> T): T {
        var espera = 1000L
        var intento = 1
        while (true) {
            try {
                return bloque()
            } catch (e: ErrorDrive) {
                if (e.pausa != null) throw e // Google pidió parar: reintentar empeora el bloqueo
                val reintentable = e.codigoHttp == null || e.codigoHttp == 429 || e.codigoHttp in 500..599
                if (!reintentable || intento >= INTENTOS) throw e
                esperar(espera)
                espera *= 2
                intento++
            }
        }
    }

    private fun entero(o: JsonObject?, k: String): Int? = (o?.get(k) as? JsonPrimitive)?.longOrNull?.takeIf { it in Int.MIN_VALUE..Int.MAX_VALUE }?.toInt()

    private fun cadena(o: JsonObject, k: String): String? = (o[k] as? JsonPrimitive)?.takeIf { it.isString }?.content

    companion object {
        private const val BASE = "https://www.googleapis.com/drive/v3/files"
        private const val INTENTOS = 3
    }
}
