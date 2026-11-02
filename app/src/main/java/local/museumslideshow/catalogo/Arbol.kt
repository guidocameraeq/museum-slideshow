package local.museumslideshow.catalogo

import java.text.Normalizer
import local.museumslideshow.ajustes.EstadoGrupo
import local.museumslideshow.ajustes.Preferencias
import local.museumslideshow.ajustes.estaTildada
import local.museumslideshow.ajustes.tildar

/** Lógica pura del árbol de carpetas (grupos y galerías) para tildar y buscar por id o por nombre. */
class Arbol(val grupos: List<Grupo>, val galerias: List<Galeria>) {

    constructor(cat: CatalogoCargado) : this(cat.grupos, cat.galerias)

    /** ¿`id` es una carpeta con subcarpetas? */
    fun esGrupo(id: String) = grupos.any { it.driveId == id }

    /** Los ids de grupo que cuelgan de `id` (él incluido). */
    private fun gruposDesde(id: String): Set<String> {
        val salida = LinkedHashSet<String>()
        salida += id
        var cambio = true
        while (cambio) {
            cambio = false
            for (g in grupos) if (g.padreId in salida && salida.add(g.driveId)) cambio = true
        }
        return salida
    }

    /** Todas las galerías de un nodo: si es una galería, ella; si es un grupo, las de adentro (anidadas incluidas). */
    fun galeriasDe(id: String): List<Galeria> {
        val dentro = gruposDesde(id)
        return galerias.filter { it.driveId == id || it.padreId in dentro }
    }

    /** Las obras de un nodo (galería o grupo). */
    fun cantidadObras(id: String): Int = galeriasDe(id).sumOf { it.obras.size }

    /** Qué tan tildado está un grupo (o una galería, que cuenta como un grupo de una). */
    fun estadoGrupo(id: String, p: Preferencias): EstadoGrupo = Tildes.estado(galeriasDe(id).map { p.estaTildada(it.driveId) })

    /** Tilda o destilda un nodo entero (todas sus galerías, las anidadas incluidas). */
    fun tildar(p: Preferencias, id: String, valor: Boolean): Preferencias = p.tildar(galeriasDe(id).map { it.driveId }, valor)

    /** Los nodos (grupos y galerías) cuyo id es `texto` o cuyo nombre lo es, sin distinguir mayúsculas ni tildes. */
    fun resolver(texto: String): List<String> {
        val t = texto.trim()
        if (t.isEmpty()) return emptyList()
        val porId = (grupos.map { it.driveId } + galerias.map { it.driveId }).filter { it == t }.distinct()
        if (porId.isNotEmpty()) return porId
        val n = Tildes.normalizar(t)
        return (grupos.filter { Tildes.normalizar(it.titulo) == n }.map { it.driveId } +
            galerias.filter { Tildes.normalizar(it.titulo) == n }.map { it.driveId }).distinct()
    }

    // ------------------------------------------------------------------------------ Para la pantalla Galerías

    /** Los hijos directos de un nodo (`null` = la carpeta raíz): grupos y galerías sueltas juntos, por nombre. */
    fun hijos(padreId: String?): List<NodoArbol> {
        val deGrupos = grupos.filter { it.padreId == padreId }.map { NodoArbol(it.driveId, it.titulo, true, it.padreId) }
        val ids = deGrupos.map { it.id }.toSet()
        // Una carpeta con imágenes y subcarpetas figura en las dos listas: manda el grupo (sus propias obras entran en él).
        val sueltas = galerias.filter { it.padreId == padreId && it.driveId !in ids && !esGrupo(it.driveId) }
            .map { NodoArbol(it.driveId, it.titulo, false, it.padreId) }
        return (deGrupos + sueltas).sortedWith { a, b -> String.CASE_INSENSITIVE_ORDER.compare(a.titulo, b.titulo) }
    }

    /** El nombre de un nodo, o null si no existe. */
    fun titulo(id: String): String? = grupos.firstOrNull { it.driveId == id }?.titulo ?: galerias.firstOrNull { it.driveId == id }?.titulo

    /** El grupo que contiene a `id`, o null si cuelga de la raíz. */
    fun padreDe(id: String): String? = grupos.firstOrNull { it.driveId == id }?.padreId ?: galerias.firstOrNull { it.driveId == id }?.padreId

    /** Los nodos por los que se pasa de la raíz hasta `id` (él incluido); vacío si no existe. */
    fun camino(id: String): List<String> {
        val salida = ArrayList<String>()
        var actual: String? = id
        var vueltas = 0
        while (actual != null && vueltas < 20) {
            if (titulo(actual) == null) return emptyList()
            salida.add(0, actual)
            actual = padreDe(actual)
            vueltas++
        }
        return salida
    }

    /** OK sobre un nodo: si no está todo tildado lo tilda todo; si ya lo está, lo destilda todo. */
    fun alternar(p: Preferencias, id: String): Preferencias = tildar(p, id, estadoGrupo(id, p) != EstadoGrupo.TODAS)

    /** Cuántas galerías tildadas hay en un nodo (`null` = todo el catálogo), de cuántas, y cuántas obras suman las tildadas. */
    fun resumen(p: Preferencias, id: String? = null): ResumenTildes {
        val lista = if (id == null) galerias else galeriasDe(id)
        val tildadas = lista.filter { p.estaTildada(it.driveId) }
        return ResumenTildes(tildadas.size, lista.size, tildadas.sumOf { it.obras.size })
    }

    /** Las galerías cuyas tapas arman el mosaico de un grupo: las primeras (con obras) de adentro, hasta `max`. */
    fun mosaico(id: String, max: Int = 4): List<Galeria> = galeriasDe(id).filter { it.obras.isNotEmpty() }.take(max)

    /** Las filas visibles de la vista Lista: el árbol con los grupos de `abiertos` desplegados. */
    fun filasLista(abiertos: Set<String>, p: Preferencias): List<FilaLista> {
        val salida = ArrayList<FilaLista>()
        fun agregar(padre: String?, nivel: Int, vistos: Set<String>) {
            for (n in hijos(padre)) {
                if (n.id in vistos) continue
                val abierto = n.esGrupo && n.id in abiertos
                val dentro = if (n.esGrupo) galeriasDe(n.id) else emptyList()
                salida += FilaLista(
                    n.id, n.titulo, nivel, n.esGrupo, abierto, cantidadObras(n.id), dentro.size, n.padreId, estadoGrupo(n.id, p),
                )
                if (abierto) agregar(n.id, nivel + 1, vistos + n.id)
            }
        }
        agregar(null, 0, emptySet())
        return salida
    }
}

/** Un nodo del árbol: una galería suelta o un grupo. */
data class NodoArbol(val id: String, val titulo: String, val esGrupo: Boolean, val padreId: String?)

/** Cuántas galerías están tildadas de cuántas, y las obras de las tildadas. */
data class ResumenTildes(val tildadas: Int, val total: Int, val obras: Int)

/** Una fila de la vista Lista. `galerias` = cuántas galerías tiene adentro (solo grupos). */
data class FilaLista(
    val id: String,
    val titulo: String,
    val nivel: Int,
    val esGrupo: Boolean,
    val abierto: Boolean,
    val obras: Int,
    val galerias: Int,
    val padreId: String?,
    val estado: EstadoGrupo,
)

object Tildes {
    fun normalizar(s: String): String =
        Normalizer.normalize(s.trim(), Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "").lowercase()

    fun estado(tildes: List<Boolean>): EstadoGrupo = when {
        tildes.isEmpty() || tildes.none { it } -> EstadoGrupo.NINGUNA
        tildes.all { it } -> EstadoGrupo.TODAS
        else -> EstadoGrupo.ALGUNAS
    }
}
