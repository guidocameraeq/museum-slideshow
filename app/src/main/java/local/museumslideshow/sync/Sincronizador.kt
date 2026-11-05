package local.museumslideshow.sync

import android.util.Log
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import local.museumslideshow.Registro
import local.museumslideshow.catalogo.ArchivoIdx
import local.museumslideshow.catalogo.Catalogo
import local.museumslideshow.catalogo.GaleriaIdx
import local.museumslideshow.catalogo.GrupoIdx
import local.museumslideshow.catalogo.ImagenLocal
import local.museumslideshow.catalogo.IndiceLocal
import local.museumslideshow.catalogo.LectorGaleria
import local.museumslideshow.catalogo.Problema
import local.museumslideshow.catalogo.TapaIdx
import local.museumslideshow.catalogo.TipoProblema

/** `bajadas` = archivos que se bajaron (galeria.json y tapas); las obras las baja la reserva, no la sincronización. */
data class ResultadoSync(
    val ok: Boolean,
    val bajadas: Int,
    val borradas: Int,
    val salteadas: Int,
    val problemas: List<Problema>,
    /** Si Google pidió una pausa: hasta cuándo no se le pide nada a Drive. En ese caso no hay problemas por archivo. */
    val pausaHastaMs: Long? = null,
    /**
     * Cuántos `galeria.json` no se pudieron actualizar porque Google tenía en pausa las descargas: quedó la ficha vieja (si había)
     * y se reintenta sola. Si es > 0 también viene `pausaHastaMs` (hasta cuándo), y `ok` puede ser true.
     */
    val fichasEsperando: Int = 0,
)

/**
 * Sincroniza el CATÁLOGO con Drive: recorre la carpeta raíz como un árbol (una carpeta con imágenes es una galería, una con
 * subcarpetas es un grupo, puede ser las dos cosas), baja cada `galeria.json` y la tapa de cada galería, y anota en el índice
 * todas las obras con las medidas que da Drive. Las obras en sí NO se bajan acá: lo hace la reserva. Lo que ya no existe en
 * Drive (o cambió) se borra de la tele.
 */
class Sincronizador(
    private val api: DriveApi,
    private val dirBase: File,
    private val procesador: ProcesadorImagen,
    /** Por dÃ³nde se baja la tapa (la copia de Drive o el original): se lee en cada uso. */
    caminoObras: () -> local.museumslideshow.ajustes.CaminoObras = { local.museumslideshow.ajustes.CaminoObras.COPIA_PRIMERO },
    private val registrar: (String) -> Unit = {},
    private val ahora: () -> Long,
) {
    private val bajador = BajadorConRespaldo(api, caminoObras)
    private var bajadas = 0
    private var borradas = 0
    private var salteadas = 0
    private val problemas = ArrayList<Problema>()
    private var huboErrorRed = false
    private var fichasEsperando = 0

    /** La pausa más larga que apareció a mitad de camino (ficha, tapa o subcarpeta que esperan); null si no hubo. */
    private var pausaHasta: Long? = null

    private class Plan(val carpeta: ArchivoDrive, val padreId: String?, val json: ArchivoDrive?, val imagenes: List<ArchivoDrive>)

    // Lo que va saliendo del recorrido.
    private val planes = ArrayList<Plan>()
    private val gruposNuevos = ArrayList<GrupoIdx>()
    private val conservadas = ArrayList<GaleriaIdx>()
    private val gruposConservados = ArrayList<GrupoIdx>()
    private var viejo = IndiceLocal()

    /** Un solo sincronizador a la vez en todo el proceso (la app y el Worker no se pisan). */
    fun sincronizar(carpetaId: String): ResultadoSync = synchronized(CANDADO) {
        try {
            sincronizarBloqueado(carpetaId)
        } catch (e: PausaDriveException) {
            // Se corta al instante: sin problemas por archivo y con el índice como estaba.
            File(dirBase, "tmp").deleteRecursively()
            ResultadoSync(false, bajadas, borradas, salteadas, emptyList(), e.hastaMs)
        }
    }

    private fun sincronizarBloqueado(carpetaId: String): ResultadoSync {
        bajadas = 0; borradas = 0; salteadas = 0; problemas.clear(); huboErrorRed = false
        fichasEsperando = 0; pausaHasta = null
        planes.clear(); gruposNuevos.clear(); conservadas.clear(); gruposConservados.clear()

        if (!api.disponible || carpetaId.isBlank()) {
            val p = Problema("", null, TipoProblema.SIN_CLAVE, if (carpetaId.isBlank()) "carpeta" else "clave")
            problemas += p
            registrarIntentoFallido(p)
            return ResultadoSync(false, 0, 0, 0, problemas.toList())
        }
        dirBase.mkdirs()
        val tmp = File(dirBase, "tmp")
        tmp.deleteRecursively()
        tmp.mkdirs()
        File(dirBase, "obras").mkdirs()
        File(dirBase, "json").mkdirs()
        File(dirBase, "tapas").mkdirs()

        viejo = Catalogo.leerIndice(dirBase)

        val raiz = try {
            api.listar(carpetaId)
        } catch (e: ErrorDrive) {
            val p = Problema("", null, TipoProblema.ERROR_RED, codigo(e))
            problemas += p
            registrarIntentoFallido(p)
            return ResultadoSync(false, 0, 0, 0, problemas.toList())
        }

        // 1. Qué hay en Drive: el árbol completo.
        for (c in raiz.filter { it.esCarpeta && idSeguro(it.id) }) explorar(c, null, 1)

        // 2. Qué archivos locales quedan válidos; todo lo demás se borra ANTES de bajar.
        val idsImagenes = HashSet<String>()
        val idsJson = HashSet<String>()
        val idsGalerias = HashSet<String>()
        for (p in planes) {
            idsGalerias += p.carpeta.id
            p.json?.let { idsJson += it.id }
            p.imagenes.forEach { idsImagenes += it.id }
        }
        for (g in conservadas) {
            idsGalerias += g.id
            g.json?.let { idsJson += it.id }
            g.imagenes.forEach { idsImagenes += it.id }
        }
        for (g in viejo.galerias) {
            g.json?.let { if (it.id !in idsJson) borrarJson(it.id) }
            for (i in g.imagenes) if (i.id !in idsImagenes) borrarImagen(i.id)
        }
        // Una obra que cambió en Drive: su copia vieja ya no vale (la reserva la baja de nuevo cuando le toque).
        for (p in planes) {
            val vg = viejo.galerias.firstOrNull { it.id == p.carpeta.id } ?: continue
            for (i in p.imagenes) {
                val vi = vg.imagenes.firstOrNull { it.id == i.id } ?: continue
                if (!igual(vi, i)) borrarImagen(i.id)
            }
        }
        borrarHuerfanos(idsImagenes, idsJson, idsGalerias)

        // 3. Bajar lo nuevo o cambiado (galeria.json y tapas) y armar el índice.
        val galeriasNuevas = LinkedHashMap<String, GaleriaIdx>()
        for (p in planes) {
            val vg = viejo.galerias.firstOrNull { it.id == p.carpeta.id }
            val json = p.json?.let { j -> sincronizarJson(p.carpeta.nombre, j, vg?.json) }
            val imagenes = p.imagenes.map { i -> archivoIdx(i, vg?.imagenes?.firstOrNull { it.id == i.id }) }
            val tapa = sincronizarTapa(p, vg, json, imagenes)
            galeriasNuevas[p.carpeta.id] = GaleriaIdx(p.carpeta.id, p.carpeta.nombre, json, imagenes, p.padreId, tapa)
        }
        for (g in conservadas) galeriasNuevas.putIfAbsent(g.id, g)
        val grupos = podar(
            (gruposNuevos + gruposConservados).associateBy { it.id }.values.toList(),
            galeriasNuevas.values.toList(),
        )

        val buena = !huboErrorRed
        val indice = IndiceLocal(
            galerias = galeriasNuevas.values.toList(),
            problemas = problemas.toList(),
            ultimaSincronizacionBuena = if (buena) ahora() else viejo.ultimaSincronizacionBuena,
            ultimoIntento = ahora(),
            ultimoIntentoOk = buena,
            problemaUltimoIntento = null,
            grupos = grupos,
        )
        escribirIndice(indice)
        borrarHuerfanos(
            galeriasNuevas.values.flatMap { g -> g.imagenes.map { it.id } }.toSet(),
            galeriasNuevas.values.mapNotNull { it.json?.id }.toSet(),
            galeriasNuevas.keys,
        )
        tmp.deleteRecursively()
        if (fichasEsperando > 0) registrar("Fichas esperando que Google libere las descargas: $fichasEsperando")
        return ResultadoSync(buena, bajadas, borradas, salteadas, problemas.toList(), pausaHasta, fichasEsperando)
    }

    /** Algo quedó esperando una pausa de Google: se recuerda la más larga para agendar el reintento. */
    private fun esperaPausa(e: PausaDriveException) {
        pausaHasta = maxOf(pausaHasta ?: 0L, e.hastaMs)
    }

    // ---------------------------------------------------------------- recorrido

    /** Lista una carpeta y, según lo que tenga, la anota como galería, como grupo o las dos cosas. */
    private fun explorar(carpeta: ArchivoDrive, padreId: String?, nivel: Int) {
        val archivos = try {
            api.listar(carpeta.id)
        } catch (e: PausaDriveException) {
            // El listado en pausa: esta subcarpeta queda como estaba y la sincronización no cuenta como buena.
            huboErrorRed = true
            esperaPausa(e)
            conservarSubarbol(carpeta, padreId)
            return
        } catch (e: ErrorDrive) {
            huboErrorRed = true
            problemas += Problema(carpeta.nombre, null, TipoProblema.ERROR_RED, codigo(e))
            conservarSubarbol(carpeta, padreId)
            return
        }
        var json: ArchivoDrive? = null
        val imagenes = ArrayList<ArchivoDrive>()
        val subcarpetas = ArrayList<ArchivoDrive>()
        for (a in archivos) {
            when {
                a.esCarpeta -> if (idSeguro(a.id)) subcarpetas += a
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
        if (imagenes.isNotEmpty()) planes += Plan(carpeta, padreId, json, imagenes)
        if (subcarpetas.isNotEmpty() && nivel < PROFUNDIDAD_MAX) {
            gruposNuevos += GrupoIdx(carpeta.id, carpeta.nombre, padreId)
            for (s in subcarpetas) explorar(s, carpeta.id, nivel + 1)
        }
    }

    /** No se pudo listar `carpeta`: queda lo que había de ella y de todo lo que colgaba de ella (con el nombre y lugar de ahora). */
    private fun conservarSubarbol(carpeta: ArchivoDrive, padreId: String?) {
        val ids = HashSet<String>()
        ids += carpeta.id
        var cambio = true
        while (cambio) {
            cambio = false
            for (g in viejo.grupos) if (g.padreId in ids && ids.add(g.id)) cambio = true
        }
        for (g in viejo.grupos) {
            if (g.id !in ids) continue
            gruposConservados += if (g.id == carpeta.id) g.copy(nombre = carpeta.nombre, padreId = padreId) else g
        }
        for (g in viejo.galerias) {
            if (g.id != carpeta.id && g.padreId !in ids) continue
            conservadas += if (g.id == carpeta.id) g.copy(nombre = carpeta.nombre, padreId = padreId) else g
        }
    }

    /** Un grupo sin ninguna galería adentro (ni él mismo lo es) no se anota. */
    private fun podar(grupos: List<GrupoIdx>, galerias: List<GaleriaIdx>): List<GrupoIdx> {
        val padres = grupos.associate { it.id to it.padreId }
        val conGaleria = HashSet<String>()
        for (g in galerias) {
            conGaleria += g.id
            var id: String? = g.padreId
            var vueltas = 0
            while (id != null && vueltas < 12) {
                conGaleria += id
                id = padres[id]
                vueltas++
            }
        }
        return grupos.filter { it.id in conGaleria }
    }

    // ---------------------------------------------------------------- archivos

    /** Las medidas son las de Drive (con la rotación aplicada); si no las dio, las que ya había de esa misma imagen. */
    private fun archivoIdx(i: ArchivoDrive, previo: ArchivoIdx?): ArchivoIdx {
        val m = i.medidasVistas()
        val (ancho, alto) = when {
            m != null -> m.ancho to m.alto
            previo != null && previo.ancho > 0 && previo.alto > 0 && igual(previo, i) -> previo.ancho to previo.alto
            else -> 0 to 0
        }
        return ArchivoIdx(i.id, i.nombre, i.md5, i.modificado, ancho, alto)
    }

    private fun sincronizarJson(galeria: String, j: ArchivoDrive, viejo: ArchivoIdx?): ArchivoIdx? {
        val destino = Catalogo.archivoJson(dirBase, j.id)
        val nuevoIdx = ArchivoIdx(j.id, j.nombre, j.md5, j.modificado)
        if (viejo != null && destino.isFile && igual(viejo, j)) return nuevoIdx
        val tmpArchivo = File(File(dirBase, "tmp"), "${j.id}.json")
        try {
            api.bajar(j.id, tmpArchivo, MAX_JSON)
        } catch (e: ArchivoExcedeTope) {
            problemas += Problema(galeria, j.nombre, TipoProblema.JSON_MUY_GRANDE, null)
            salteadas++
            borrarJson(j.id)
            return null
        } catch (e: PausaDriveException) {
            // Google tiene en pausa las descargas: queda la ficha vieja (con su md5 viejo, así se reintenta) y se sigue.
            tmpArchivo.delete()
            fichasEsperando++
            esperaPausa(e)
            return viejo?.takeIf { destino.isFile }
        } catch (e: ErrorDrive) {
            return falloRed(galeria, j.nombre, e, viejo?.takeIf { destino.isFile })
        }
        mover(tmpArchivo, destino)
        bajadas++
        return nuevoIdx
    }

    /**
     * La tapa de la galería: la primera obra según el orden de su `galeria.json` (o la primera por nombre), en ~480 px.
     * Solo se rehace si cambió esa obra. Si la obra ya está bajada en la tele se achica esa copia (no se baja de nuevo).
     */
    private fun sincronizarTapa(p: Plan, vg: GaleriaIdx?, json: ArchivoIdx?, imagenes: List<ArchivoIdx>): TapaIdx? {
        val galeriaId = p.carpeta.id
        val destino = Catalogo.archivoTapa(dirBase, galeriaId)
        val texto = json?.let {
            try { Catalogo.archivoJson(dirBase, it.id).takeIf { f -> f.isFile }?.readText(Charsets.UTF_8) } catch (e: Exception) { null }
        }
        val lectura = LectorGaleria.leer(galeriaId, p.carpeta.nombre, texto, imagenes.map { ImagenLocal(it.id, it.nombre, it.ancho, it.alto) })
        val primera = lectura.galeria.obras.firstOrNull()?.driveId?.let { id -> p.imagenes.firstOrNull { it.id == id } }
        if (primera == null) {
            if (destino.exists()) destino.delete()
            return null
        }
        val nueva = TapaIdx(primera.id, primera.md5, primera.modificado)
        val conservada = vg?.tapa?.takeIf { destino.isFile }
        if (conservada == nueva) return nueva

        val tmpDir = File(dirBase, "tmp")
        val original = File(tmpDir, "${primera.id}.orig")
        val tapa = File(tmpDir, "tapa-$galeriaId.jpg")
        try {
            val copiaLocal = Catalogo.archivoObra(dirBase, primera.id)
            val fuente: File
            if (copiaLocal.isFile) {
                fuente = copiaLocal
            } else {
                try {
                    val via = bajador.bajar(primera.id, LADO_TAPA, original, MAX_IMAGEN)
                    registrar("Tapa de ${Registro.limpiar(p.carpeta.nombre)}: ${via.etiqueta}")
                } catch (e: ArchivoExcedeTope) {
                    problemas += Problema(p.carpeta.nombre, primera.nombre, TipoProblema.IMAGEN_MUY_GRANDE, null)
                    salteadas++
                    return conservada
                } catch (e: PausaDriveException) {
                    esperaPausa(e) // la tapa nueva espera: queda la anterior y se reintenta
                    return conservada
                } catch (e: ErrorDrive) {
                    huboErrorRed = true
                    problemas += Problema(p.carpeta.nombre, primera.nombre, TipoProblema.ERROR_RED, codigo(e))
                    return conservada
                }
                fuente = original
            }
            if (!procesador.tapa(fuente, tapa, LADO_TAPA) || !tapa.isFile) {
                problemas += Problema(p.carpeta.nombre, primera.nombre, TipoProblema.IMAGEN_NO_DECODIFICA, null)
                salteadas++
                return conservada
            }
            mover(tapa, destino)
            bajadas++
            return nueva
        } finally {
            original.delete(); tapa.delete()
        }
    }

    private fun falloRed(galeria: String, archivo: String, e: ErrorDrive, conservado: ArchivoIdx?): ArchivoIdx? {
        huboErrorRed = true
        problemas += Problema(galeria, archivo, TipoProblema.ERROR_RED, codigo(e))
        return conservado
    }

    /** Mismo md5; si no hay md5, mismo `modificado`; si Drive no da ninguno de los dos se asume igual. */
    private fun igual(v: ArchivoIdx, n: ArchivoDrive): Boolean = when {
        n.md5 != null -> v.md5 == n.md5
        n.modificado != null -> v.md5 == null && v.modificado == n.modificado
        else -> true
    }

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

    /** Archivos locales que ningún índice reconoce (restos de una falla a mitad de camino, o de lo que se borró en Drive). */
    private fun borrarHuerfanos(idsImagenes: Set<String>, idsJson: Set<String>, idsGalerias: Set<String>) {
        File(dirBase, "obras").listFiles()?.forEach { f ->
            val id = f.name.removeSuffix(".jpg").removeSuffix(".chica")
            if (id !in idsImagenes && f.delete()) borradas++
        }
        File(dirBase, "json").listFiles()?.forEach { f ->
            if (f.name.removeSuffix(".json") !in idsJson && f.delete()) borradas++
        }
        File(dirBase, "tapas").listFiles()?.forEach { f ->
            if (f.name.removeSuffix(".jpg") !in idsGalerias && f.delete()) borradas++
        }
    }

    /**
     * El intento falló antes de empezar (no se pudo listar la carpeta raíz, o falta la clave): se anota SOLO el intento y
     * su problema; las galerías, los problemas de antes y la hora de la última sincronización buena quedan como estaban.
     */
    private fun registrarIntentoFallido(p: Problema) {
        try {
            dirBase.mkdirs()
            val previo = Catalogo.leerIndice(dirBase)
            escribirIndice(previo.copy(ultimoIntento = ahora(), ultimoIntentoOk = false, problemaUltimoIntento = p))
        } catch (e: Exception) {
            Log.w(Registro.TAG, "No se pudo anotar el intento fallido (${e.javaClass.simpleName})")
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

    companion object {
        private val CANDADO = Any()
        const val MAX_IMAGEN = 50L * 1024 * 1024
        const val MAX_JSON = 1L * 1024 * 1024

        /** Cuántos niveles de carpetas se recorren (la carpeta raíz no cuenta). */
        const val PROFUNDIDAD_MAX = 4

        /** Lado largo de la tapa de cada galería. */
        const val LADO_TAPA = 480
    }
}

/** Los IDs de Drive son [A-Za-z0-9_-]; cualquier otra cosa no se usa jamás como nombre de archivo. */
internal fun idSeguro(id: String) = id.isNotEmpty() && id.length <= 200 && id.all { it.isLetterOrDigit() && it.code < 128 || it == '_' || it == '-' }
