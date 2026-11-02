package local.museumslideshow.sync

import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import local.museumslideshow.Registro
import local.museumslideshow.catalogo.ArchivoIdx
import local.museumslideshow.catalogo.Catalogo
import local.museumslideshow.catalogo.GaleriaIdx
import local.museumslideshow.catalogo.IndiceLocal
import local.museumslideshow.catalogo.Problema
import local.museumslideshow.catalogo.TipoProblema

data class ResultadoSync(
    val ok: Boolean,
    val bajadas: Int,
    val borradas: Int,
    val salteadas: Int,
    val problemas: List<Problema>,
)

class Sincronizador(
    private val api: DriveApi,
    private val dirBase: File,
    private val procesador: ProcesadorImagen,
    private val topeBytes: Long,
    private val ahora: () -> Long,
) {
    private var bajadas = 0
    private var borradas = 0
    private var salteadas = 0
    private val problemas = ArrayList<Problema>()
    private var huboErrorRed = false
    private var espacioLlenoAvisado = false
    private var usadoBytes = 0L

    /** Un solo sincronizador a la vez en todo el proceso (la app y el Worker no se pisan). */
    fun sincronizar(carpetaId: String): ResultadoSync = synchronized(CANDADO) {
        bajadas = 0; borradas = 0; salteadas = 0; problemas.clear(); huboErrorRed = false; espacioLlenoAvisado = false

        if (!api.disponible || carpetaId.isBlank()) {
            problemas += Problema("", null, TipoProblema.SIN_CLAVE, if (carpetaId.isBlank()) "carpeta" else "clave")
            return ResultadoSync(false, 0, 0, 0, problemas.toList())
        }
        dirBase.mkdirs()
        val tmp = File(dirBase, "tmp")
        tmp.deleteRecursively()
        tmp.mkdirs()
        File(dirBase, "obras").mkdirs()
        File(dirBase, "json").mkdirs()

        val viejo = Catalogo.leerIndice(dirBase)

        val raiz = try {
            api.listar(carpetaId)
        } catch (e: ErrorDrive) {
            problemas += Problema("", null, TipoProblema.ERROR_RED, codigo(e))
            return ResultadoSync(false, 0, 0, 0, problemas.toList())
        }

        // 1. Qué hay en Drive (galería -> lista, o null si falló el listado de esa subcarpeta).
        val carpetas = raiz.filter { it.esCarpeta && idSeguro(it.id) }
        val listados = LinkedHashMap<ArchivoDrive, List<ArchivoDrive>?>()
        for (c in carpetas) {
            listados[c] = try {
                api.listar(c.id)
            } catch (e: ErrorDrive) {
                huboErrorRed = true
                problemas += Problema(c.nombre, null, TipoProblema.ERROR_RED, codigo(e))
                null
            }
        }

        // 2. Qué archivos locales quedan válidos; todo lo demás se borra ANTES de bajar.
        class Plan(val carpeta: ArchivoDrive, val json: ArchivoDrive?, val imagenes: List<ArchivoDrive>)

        val planes = ArrayList<Plan>()
        val conservar = HashMap<String, GaleriaIdx>() // galerías con el listado fallido: queda lo viejo
        for ((carpeta, archivos) in listados) {
            if (archivos == null) {
                viejo.galerias.firstOrNull { it.id == carpeta.id }?.let { conservar[carpeta.id] = it.copy(nombre = carpeta.nombre) }
                continue
            }
            var json: ArchivoDrive? = null
            val imagenes = ArrayList<ArchivoDrive>()
            for (a in archivos) {
                when {
                    a.esCarpeta -> {} // subcarpetas anidadas: se ignoran
                    !idSeguro(a.id) -> {}
                    a.nombre == "galeria.json" -> {
                        if (a.tamano != null && a.tamano > MAX_JSON) {
                            problemas += Problema(carpeta.nombre, a.nombre, TipoProblema.JSON_MUY_GRANDE, null)
                            salteadas++
                        } else if (json == null) json = a
                    }
                    a.mimeType.startsWith("image/") -> {
                        if (a.tamano != null && a.tamano > MAX_IMAGEN) {
                            problemas += Problema(carpeta.nombre, a.nombre, TipoProblema.IMAGEN_MUY_GRANDE, null)
                            salteadas++
                        } else imagenes += a
                    }
                    else -> {
                        problemas += Problema(carpeta.nombre, a.nombre, TipoProblema.NO_ES_IMAGEN, null)
                        salteadas++
                    }
                }
            }
            planes += Plan(carpeta, json, imagenes)
        }

        val idsImagenes = HashSet<String>()
        val idsJson = HashSet<String>()
        for (p in planes) {
            p.json?.let { idsJson += it.id }
            p.imagenes.forEach { idsImagenes += it.id }
        }
        for (g in conservar.values) {
            g.json?.let { idsJson += it.id }
            g.imagenes.forEach { idsImagenes += it.id }
        }
        // Todo lo que figuraba en el índice viejo y ya no es válido.
        for (g in viejo.galerias) {
            g.json?.let { if (it.id !in idsJson) borrarJson(it.id) }
            for (i in g.imagenes) if (i.id !in idsImagenes) borrarImagen(i.id)
        }
        borrarHuerfanos(idsImagenes, idsJson)

        // 3. Bajar lo nuevo o cambiado. Lo usado se mide una vez y se va sumando (medir en cada bajada es lento con miles de obras).
        usadoBytes = usado(dirBase)
        val galeriasNuevas = ArrayList<GaleriaIdx>()
        for (p in planes) {
            val vg = viejo.galerias.firstOrNull { it.id == p.carpeta.id }
            val json = p.json?.let { j -> sincronizarJson(p.carpeta.nombre, j, vg?.json) }
            val imagenes = p.imagenes.mapNotNull { i ->
                sincronizarImagen(p.carpeta.nombre, i, vg?.imagenes?.firstOrNull { it.id == i.id })
            }
            galeriasNuevas += GaleriaIdx(p.carpeta.id, p.carpeta.nombre, json, imagenes)
        }
        galeriasNuevas += conservar.values

        val buena = !huboErrorRed
        val indice = IndiceLocal(
            galerias = galeriasNuevas,
            problemas = problemas.toList(),
            ultimaSincronizacionBuena = if (buena) ahora() else viejo.ultimaSincronizacionBuena,
        )
        escribirIndice(indice)
        borrarHuerfanos(
            galeriasNuevas.flatMap { g -> g.imagenes.map { it.id } }.toSet(),
            galeriasNuevas.mapNotNull { it.json?.id }.toSet(),
        )
        tmp.deleteRecursively()
        return ResultadoSync(buena, bajadas, borradas, salteadas, problemas.toList())
    }

    private fun sincronizarJson(galeria: String, j: ArchivoDrive, viejo: ArchivoIdx?): ArchivoIdx? {
        val destino = Catalogo.archivoJson(dirBase, j.id)
        val nuevoIdx = ArchivoIdx(j.id, j.nombre, j.md5, j.modificado)
        if (viejo != null && destino.isFile && igual(viejo, j)) return nuevoIdx
        if (sinEspacio(galeria, j.nombre)) return viejo?.takeIf { destino.isFile }
        val tmpArchivo = File(File(dirBase, "tmp"), "${j.id}.json")
        try {
            api.bajar(j.id, tmpArchivo, MAX_JSON)
        } catch (e: ArchivoExcedeTope) {
            problemas += Problema(galeria, j.nombre, TipoProblema.JSON_MUY_GRANDE, null)
            salteadas++
            borrarJson(j.id)
            return null
        } catch (e: ErrorDrive) {
            return falloRed(galeria, j.nombre, e, viejo?.takeIf { destino.isFile })
        }
        usadoBytes += tmpArchivo.length() - (if (destino.isFile) destino.length() else 0L)
        mover(tmpArchivo, destino)
        bajadas++
        return nuevoIdx
    }

    private fun sincronizarImagen(galeria: String, i: ArchivoDrive, viejo: ArchivoIdx?): ArchivoIdx? {
        val destino = Catalogo.archivoObra(dirBase, i.id)
        val destinoChica = Catalogo.archivoObraChica(dirBase, i.id)
        if (viejo != null && destino.isFile && igual(viejo, i)) {
            return viejo.copy(nombre = i.nombre) // por si le cambiaron el nombre
        }
        val conservado = viejo?.takeIf { destino.isFile }
        if (sinEspacio(galeria, i.nombre)) return conservado
        val tmpDir = File(dirBase, "tmp")
        val original = File(tmpDir, "${i.id}.orig")
        val copia = File(tmpDir, "${i.id}.jpg")
        val chica = File(tmpDir, "${i.id}.chica.jpg")
        try {
            try {
                api.bajar(i.id, original, MAX_IMAGEN)
            } catch (e: ArchivoExcedeTope) {
                problemas += Problema(galeria, i.nombre, TipoProblema.IMAGEN_MUY_GRANDE, null)
                salteadas++
                borrarImagen(i.id)
                return null
            } catch (e: ErrorDrive) {
                return falloRed(galeria, i.nombre, e, conservado)
            }
            val medidas = procesador.procesar(original, copia, chica)
            if (medidas == null || !copia.isFile || !chica.isFile) {
                problemas += Problema(galeria, i.nombre, TipoProblema.IMAGEN_NO_DECODIFICA, null)
                salteadas++
                return conservado
            }
            usadoBytes += copia.length() + chica.length() -
                (if (destino.isFile) destino.length() else 0L) - (if (destinoChica.isFile) destinoChica.length() else 0L)
            mover(copia, destino)
            mover(chica, destinoChica)
            bajadas++
            return ArchivoIdx(i.id, i.nombre, i.md5, i.modificado, medidas.ancho, medidas.alto)
        } finally {
            original.delete(); copia.delete(); chica.delete() // solo se guarda la copia
        }
    }

    private fun falloRed(galeria: String, archivo: String, e: ErrorDrive, conservado: ArchivoIdx?): ArchivoIdx? {
        huboErrorRed = true
        problemas += Problema(galeria, archivo, TipoProblema.ERROR_RED, codigo(e))
        return conservado
    }

    private fun sinEspacio(galeria: String, archivo: String): Boolean {
        if (usadoBytes < topeBytes) return false
        if (!espacioLlenoAvisado) {
            espacioLlenoAvisado = true
            problemas += Problema(galeria, archivo, TipoProblema.ESPACIO_LLENO, null)
        }
        salteadas++
        return true
    }

    private fun usado(f: File): Long =
        if (f.isFile) f.length() else (f.listFiles()?.sumOf { usado(it) } ?: 0L)

    /** Mismo md5; si no hay md5, mismo `modificado`. */
    private fun igual(v: ArchivoIdx, n: ArchivoDrive): Boolean =
        if (n.md5 != null) v.md5 == n.md5 else (n.modificado != null && v.md5 == null && v.modificado == n.modificado)

    private fun borrarImagen(id: String) {
        var algo = false
        for (f in listOf(Catalogo.archivoObra(dirBase, id), Catalogo.archivoObraChica(dirBase, id))) {
            if (f.exists() && f.delete()) algo = true
        }
        if (algo) borradas++
    }

    private fun borrarJson(id: String) {
        val f = Catalogo.archivoJson(dirBase, id)
        if (f.exists() && f.delete()) borradas++
    }

    /** Archivos locales que ningún índice reconoce (restos de una falla a mitad de camino). */
    private fun borrarHuerfanos(idsImagenes: Set<String>, idsJson: Set<String>) {
        File(dirBase, "obras").listFiles()?.forEach { f ->
            val id = f.name.removeSuffix(".jpg").removeSuffix(".chica")
            if (id !in idsImagenes && f.delete()) borradas++
        }
        File(dirBase, "json").listFiles()?.forEach { f ->
            if (f.name.removeSuffix(".json") !in idsJson && f.delete()) borradas++
        }
    }

    private fun escribirIndice(indice: IndiceLocal) {
        val temporal = File(dirBase, "indice.json.tmp")
        temporal.writeText(Catalogo.JSON.encodeToString(IndiceLocal.serializer(), indice), Charsets.UTF_8)
        mover(temporal, Catalogo.archivoIndice(dirBase))
    }

    private fun mover(origen: File, destino: File) {
        destino.parentFile?.mkdirs()
        try {
            Files.move(origen.toPath(), destino.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        } catch (e: Exception) {
            Files.move(origen.toPath(), destino.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
    }

    /** Solo el código HTTP (o "red" si no hubo respuesta): nunca la URL ni el mensaje original. */
    private fun codigo(e: ErrorDrive): String = Registro.limpiar(e.codigoHttp?.toString() ?: "red")

    /** Los IDs de Drive son [A-Za-z0-9_-]; cualquier otra cosa no se usa jamás como nombre de archivo. */
    private fun idSeguro(id: String) = id.isNotEmpty() && id.length <= 200 && id.all { it.isLetterOrDigit() && it.code < 128 || it == '_' || it == '-' }

    companion object {
        private val CANDADO = Any()
        const val MAX_IMAGEN = 50L * 1024 * 1024
        const val MAX_JSON = 1L * 1024 * 1024
    }
}
