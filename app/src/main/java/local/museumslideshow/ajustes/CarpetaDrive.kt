package local.museumslideshow.ajustes

/** La carpeta de Drive se puede dar como ID o como link; acá se saca el ID (lógica pura). */
object CarpetaDrive {
    private val ID = Regex("^[A-Za-z0-9_-]{10,200}$")
    private val EN_LINK = Regex("/folders/([A-Za-z0-9_-]{10,200})")
    private val EN_PARAMETRO = Regex("[?&]id=([A-Za-z0-9_-]{10,200})")

    /** El ID de un link de Drive (`.../folders/<ID>?...`, `...open?id=<ID>`) o el texto mismo si ya es un ID. Null si no sirve. */
    fun extraerId(texto: String?): String? {
        val t = texto?.trim().orEmpty()
        if (t.isEmpty()) return null
        EN_LINK.find(t)?.let { return it.groupValues[1] }
        EN_PARAMETRO.find(t)?.let { return it.groupValues[1] }
        return t.takeIf { ID.matches(it) }
    }

    /** Para mostrar en pantalla sin ocupar medio renglón: `1AbCdE…wXyZ`. */
    fun abreviar(id: String): String =
        if (id.length <= 14) id else id.take(6) + "…" + id.takeLast(4)
}
