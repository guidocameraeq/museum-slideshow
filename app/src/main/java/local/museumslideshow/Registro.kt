package local.museumslideshow

/** Etiqueta única del registro. Nunca se loguea la clave de Drive ni URLs con `key=`. */
object Registro {
    const val TAG = "MuseumSlideshow"

    private val CLAVE = Regex("key=[^&\\s\"']*", RegexOption.IGNORE_CASE)

    /** Una clave de API de Google suelta (todas empiezan con `AIza`), por si aparece fuera de una URL. */
    private val CLAVE_SUELTA = Regex("AIza[0-9A-Za-z_-]+")

    /** Reemplaza `key=<lo que sea>` por `key=***` y una clave suelta por `AIza***`. Todo texto que se loguee pasa por acá. */
    fun limpiar(texto: String): String = CLAVE_SUELTA.replace(CLAVE.replace(texto, "key=***"), "AIza***")
}
