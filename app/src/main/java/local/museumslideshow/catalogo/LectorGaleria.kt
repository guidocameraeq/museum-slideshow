package local.museumslideshow.catalogo

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import local.museumslideshow.Registro

/** Lógica pura (sin Android): arma una Galería a partir del JSON y de las imágenes que hay en la tele. */
object LectorGaleria {
    private const val FORMATO_ACTUAL = 1
    private val ORDEN_NOMBRE: Comparator<ImagenLocal> =
        Comparator<ImagenLocal> { a, b -> String.CASE_INSENSITIVE_ORDER.compare(a.nombre, b.nombre) }
            .thenComparing { a, b -> a.nombre.compareTo(b.nombre) }

    fun leer(driveId: String, nombreCarpeta: String, jsonTexto: String?, imagenes: List<ImagenLocal>): ResultadoLectura {
        val problemas = ArrayList<Problema>()
        val porNombre = LinkedHashMap<String, ImagenLocal>()
        for (img in imagenes) porNombre.putIfAbsent(img.nombre, img)

        fun sinFicha(lista: Collection<ImagenLocal>) =
            lista.sortedWith(ORDEN_NOMBRE).map { Obra(it.driveId, it.nombre, it.ancho, it.alto, null) }

        fun soloImagenes() = Galeria(driveId, nombreCarpeta, null, sinFicha(porNombre.values))

        if (jsonTexto == null) return ResultadoLectura(soloImagenes(), problemas)

        val raiz: JsonElement = try {
            Json.parseToJsonElement(jsonTexto)
        } catch (e: Exception) {
            problemas += Problema(nombreCarpeta, "galeria.json", TipoProblema.JSON_INVALIDO, sanear(e.message))
            return ResultadoLectura(soloImagenes(), problemas)
        }
        if (raiz !is JsonObject) {
            problemas += Problema(nombreCarpeta, "galeria.json", TipoProblema.JSON_INVALIDO, "la raíz no es un objeto")
            return ResultadoLectura(soloImagenes(), problemas)
        }
        val formato = (raiz["formato"] as? JsonPrimitive)?.takeIf { !it.isString }?.doubleOrNull
        if (formato == null) {
            problemas += Problema(nombreCarpeta, "galeria.json", TipoProblema.JSON_SIN_FORMATO, null)
            return ResultadoLectura(soloImagenes(), problemas)
        }
        if (formato > FORMATO_ACTUAL) {
            problemas += Problema(nombreCarpeta, "galeria.json", TipoProblema.FORMATO_NUEVO, formato.toInt().toString())
        }

        val titulo = texto(raiz["titulo"]) ?: nombreCarpeta
        val descripcion = texto(raiz["descripcion"])
        val obras = ArrayList<Obra>()
        val usadas = HashSet<String>()
        val ids = HashSet<String>()
        val lista = raiz["obras"] as? JsonArray ?: JsonArray(emptyList())
        for ((i, el) in lista.withIndex()) {
            val o = el as? JsonObject
            val archivo = (o?.get("archivo") as? JsonPrimitive)?.takeIf { it.isString }?.content
            if (o == null || archivo == null) {
                problemas += Problema(titulo, null, TipoProblema.OBRA_SIN_ARCHIVO, "#${i + 1}")
                continue
            }
            if (archivo in usadas) {
                problemas += Problema(titulo, archivo, TipoProblema.ARCHIVO_REPETIDO, null)
                continue
            }
            val img = porNombre[archivo]
            if (img == null) {
                problemas += Problema(titulo, archivo, TipoProblema.ARCHIVO_FALTANTE, null)
                continue
            }
            usadas += archivo
            val ficha = fichaDe(o)
            val id = ficha.id
            if (id != null && !ids.add(id)) {
                problemas += Problema(titulo, archivo, TipoProblema.ID_REPETIDO, id)
            }
            obras += Obra(img.driveId, archivo, img.ancho, img.alto, ficha)
        }
        obras += sinFicha(porNombre.values.filter { it.nombre !in usadas })
        return ResultadoLectura(Galeria(driveId, titulo, descripcion, obras), problemas)
    }

    private fun fichaDe(o: JsonObject) = Ficha(
        titulo = texto(o["titulo"]),
        autor = texto(o["autor"]),
        vidaAutor = texto(o["vida_autor"]),
        anio = texto(o["anio"]),
        movimiento = texto(o["movimiento"]),
        tecnica = texto(o["tecnica"]),
        medidas = texto(o["medidas"]),
        ubicacion = texto(o["ubicacion"]),
        forma = when (texto(o["forma"])?.lowercase()) {
            "redonda" -> Forma.REDONDA
            "ovalada" -> Forma.OVALADA
            else -> Forma.RECTANGULAR
        },
        explicacion = texto(o["explicacion"]),
        marco = texto(o["marco"]),
        credito = texto(o["credito"]),
        licencia = texto(o["licencia"]),
        fuente = texto(o["fuente"]),
        id = texto(o["id"]),
    )

    /** Solo vale un texto no vacío; cualquier otro tipo se ignora. */
    private fun texto(e: JsonElement?): String? =
        (e as? JsonPrimitive)?.takeIf { it.isString }?.content?.takeIf { it.isNotBlank() }

    private fun sanear(m: String?): String? = m?.let { Registro.limpiar(it).take(300) }
}
