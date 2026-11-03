package local.museumslideshow.protector

/**
 * La lógica pura del interruptor "Usar como protector / Volver al anterior" (sin Android, para poder probarla).
 * Trabaja con el texto del ajuste `screensaver_components`: una lista de componentes separados por comas.
 * "Guardado" = lo que se guardó como "el anterior": null = no hay nada guardado; "" = el valor anterior estaba vacío.
 */
object PlanInterruptor {
    /** Protector de Google que se pone cuando no hay un anterior que restaurar. */
    const val AMBIENT = "com.google.android.apps.tv.dreamx/.service.Backdrop"

    /** Qué hacer al tocar "Usar como protector". */
    class PlanUsar(
        /** El valor que se escribe en el sistema. */
        val escribir: String,
        /** Si hay que guardar [anterior] como "el anterior" (false = ya era el nuestro: lo guardado no se pisa). */
        val guardarAnterior: Boolean,
        /** El valor que había (null se guarda como vacío). */
        val anterior: String,
    )

    /** Es el nuestro si alguna entrada de la lista es de nuestro paquete. */
    fun esNuestro(valor: String?, paquete: String): Boolean =
        valor.orEmpty().split(',').any { it.trim().substringBefore('/') == paquete }

    /** Usar: si el valor de hoy no es el nuestro se guarda como "el anterior"; si ya es el nuestro, no se pisa lo guardado. */
    fun usar(actual: String?, nuestro: String, paquete: String): PlanUsar =
        PlanUsar(nuestro, guardarAnterior = !esNuestro(actual, paquete), anterior = actual.orEmpty())

    /**
     * Volver: exactamente lo guardado. Si no hay nada guardado, o lo guardado estaba vacío (no hay protector que restaurar), o
     * por algún error lo guardado es el nuestro, se pone el Ambient de Google (el de fábrica de esta tele).
     */
    fun volver(guardado: String?, paquete: String): String =
        if (guardado.isNullOrBlank() || esNuestro(guardado, paquete)) AMBIENT else guardado
}
