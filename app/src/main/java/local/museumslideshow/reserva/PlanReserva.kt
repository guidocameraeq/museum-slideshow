package local.museumslideshow.reserva

/** Una obra del pase: su id de Drive y su nombre (para el registro). */
data class ObraRef(val id: String, val nombre: String)

/**
 * Qué obras debe tener la tele: las próximas `adelante` (empezando por la que está en pantalla) y las últimas `vistas`
 * (para volver con Izquierda). Todo en el orden del pase, que da la vuelta al llegar al final.
 */
class VentanaReserva(val adelante: List<String>, val vistas: List<String>) {
    val todas: Set<String> = LinkedHashSet<String>().also { it.addAll(adelante); it.addAll(vistas) }
}

/** Lo que hay que hacer ahora con la reserva. */
data class PlanReserva(
    val ventana: VentanaReserva,
    /** Cuántas de las obras de adelante ya están bajadas. */
    val listasPorDelante: Int,
    /** Cuántas de las vistas siguen guardadas. */
    val vistasGuardadas: Int,
    /** Qué bajar, en orden (vacío si todavía no toca una tanda). */
    val aBajar: List<String>,
    /**
     * Lo bajado que quedó fuera de la ventana (y no es extra), de la más vieja a la más nueva. No se borra por estar afuera:
     * solo se sacan, de a una y de la más vieja, cuando hace falta lugar.
     */
    val sobrantes: List<String>,
    /** ¿Toca una tanda? (quedan menos de la mitad por delante, o falta una de las dos próximas). */
    val tanda: Boolean,
)

/** Lógica pura de la reserva de obras (sin Android ni disco): se prueba sin la tele. */
object Reserva {
    /** Cuántas obras ya vistas se guardan para volver atrás. */
    const val VISTAS = 10

    /** Bytes que se calcula que pesa una copia cuando todavía no hay ninguna bajada para medir. */
    const val BYTES_POR_OBRA_ESTIMADO = 6L * 1024 * 1024

    fun ventana(orden: List<String>, posicion: Int, n: Int, vistas: Int = VISTAS): VentanaReserva {
        val ids = orden.distinct()
        if (ids.isEmpty() || n <= 0) return VentanaReserva(emptyList(), emptyList())
        val total = ids.size
        val pos = Math.floorMod(posicion, total)
        val cuantasAdelante = minOf(n, total)
        val adelante = (0 until cuantasAdelante).map { ids[(pos + it) % total] }
        val cuantasVistas = minOf(vistas, total - cuantasAdelante).coerceAtLeast(0)
        val atras = (1..cuantasVistas).map { ids[Math.floorMod(pos - it, total)] }
        return VentanaReserva(adelante, atras)
    }

    /**
     * Con la posición actual y lo que hay bajado: qué bajar y qué borrar. Cuando quedan menos de N/2 bajadas por delante
     * (o una de las dos próximas no está) toca una tanda que completa hasta N; si no, no se baja nada. Lo bajado que quedó
     * fuera de la ventana se devuelve como `sobrantes`, ordenado por `fechas` (id -> lastModified; sin fecha = la más vieja).
     */
    fun planificar(
        orden: List<String>,
        posicion: Int,
        n: Int,
        bajadas: Set<String>,
        vistas: Int = VISTAS,
        extras: List<String> = emptyList(),
        fechas: Map<String, Long> = emptyMap(),
    ): PlanReserva {
        val v = ventana(orden, posicion, n, vistas)
        val listas = v.adelante.count { it in bajadas }
        val vistasGuardadas = v.vistas.count { it in bajadas }
        val tanda = v.adelante.isNotEmpty() && (listas * 2 < v.adelante.size || v.adelante.take(2).any { it !in bajadas })
        val deLaTanda = if (tanda) v.adelante.filter { it !in bajadas } else emptyList()
        // Las obras sin medidas (Drive no las dio) se bajan siempre que falten: sin la copia nunca entran al pase.
        val aBajar = deLaTanda + extras.filter { it !in bajadas && it !in deLaTanda }
        val sobrantes = bajadas.filter { it !in v.todas && it !in extras }
            .sortedWith(compareBy<String>({ fechas[it] ?: 0L }, { it }))
        return PlanReserva(v, listas, vistasGuardadas, aBajar, sobrantes, tanda)
    }

    /**
     * Cuántas de las obras a bajar entran en el espacio que queda (`estimadoPorObra` = lo que pesa una copia). Siempre las
     * primeras. Los `bytesSobrantes` (lo que ocupan las copias de afuera de la ventana) cuentan como espacio que se puede liberar.
     */
    fun recortarPorEspacio(
        aBajar: List<String>, usadoBytes: Long, topeBytes: Long, estimadoPorObra: Long, bytesSobrantes: Long = 0L,
    ): List<String> {
        val libre = topeBytes - (usadoBytes - bytesSobrantes.coerceAtLeast(0L))
        if (libre <= 0) return emptyList()
        val unidad = estimadoPorObra.coerceAtLeast(1L)
        val caben = ((libre + unidad - 1) / unidad).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
        return aBajar.take(caben)
    }
}
