package local.museumslideshow.ajustes

/** Qué tan tildado está un grupo de galerías. */
enum class EstadoGrupo { TODAS, ALGUNAS, NINGUNA }

/** ¿La galería `id` entra al pase? Si no figura en la selección, vale lo que diga "Galerías nuevas". */
fun Preferencias.estaTildada(id: String): Boolean = seleccionGalerias[id] ?: (galeriasNuevas == GaleriasNuevas.TILDADAS)

/** Tilda o destilda esas galerías (guarda el valor explícito de cada una). */
fun Preferencias.tildar(ids: Collection<String>, valor: Boolean): Preferencias {
    if (ids.isEmpty()) return this
    val nueva = LinkedHashMap(seleccionGalerias)
    for (id in ids) nueva[id] = valor
    return copy(seleccionGalerias = nueva)
}
