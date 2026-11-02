package local.museumslideshow

/** Etiqueta única del registro. Nunca se loguea la clave de Drive ni URLs con `key=`. */
object Registro {
    const val TAG = "MuseumSlideshow"

    private val CLAVE = Regex("key=[^&\\s\"']*", RegexOption.IGNORE_CASE)

    /** Reemplaza `key=<lo que sea>` por `key=***`. Todo texto de error que se loguee pasa por acá. */
    fun limpiar(texto: String): String = CLAVE.replace(texto, "key=***")
}
