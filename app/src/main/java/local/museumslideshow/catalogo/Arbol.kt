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
}

object Tildes {
    fun normalizar(s: String): String =
        Normalizer.normalize(s.trim(), Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "").lowercase()

    fun estado(tildes: List<Boolean>): EstadoGrupo = when {
        tildes.isEmpty() || tildes.none { it } -> EstadoGrupo.NINGUNA
        tildes.all { it } -> EstadoGrupo.TODAS
        else -> EstadoGrupo.ALGUNAS
    }
}
