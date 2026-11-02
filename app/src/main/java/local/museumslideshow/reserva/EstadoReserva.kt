package local.museumslideshow.reserva

/** Por qué falló la última bajada (Estado lo cuenta con una frase de strings.xml). */
enum class ErrorReserva { SIN_INTERNET, ESPACIO_LLENO, NO_DECODIFICA, MUY_GRANDE, DRIVE }

/** Lo que la reserva sabe de sí misma, para Inicio, Conexión y Estado. */
data class EstadoReserva(
    /** Cuántas de las próximas obras ya están bajadas. */
    val listasPorDelante: Int = 0,
    /** Cuántas son "las próximas" (N, o menos si la galería tiene menos obras). */
    val porDelante: Int = 0,
    /** Cuántas ya vistas se guardan para volver. */
    val vistasGuardadas: Int = 0,
    /** Cuántas copias hay guardadas en total. */
    val obrasGuardadas: Int = 0,
    /** Lo que ocupan las copias de las obras. */
    val bytes: Long = 0,
    /** Cuántas se están bajando o esperando turno ahora. */
    val bajando: Int = 0,
    /** Cuenta cada vez que una obra queda lista (la galería lo mira para reintentar si estaba "Preparando obras…"). */
    val version: Int = 0,
    /** La última bajada falló por falta de internet y todavía no se pudo de nuevo. */
    val sinInternet: Boolean = false,
    val ultimoError: ErrorReserva? = null,
    /** El código HTTP u otro dato corto del último error (nunca una URL). */
    val detalleError: String? = null,
    /** Ya hubo al menos un pedido de la galería (hasta entonces `porDelante` no se sabe). */
    val conPedido: Boolean = false,
)
