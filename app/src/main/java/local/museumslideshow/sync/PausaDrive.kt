package local.museumslideshow.sync

import android.content.Context
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Por qué Google pidió parar (solo para el registro: nunca lleva URLs). */
enum class MotivoPausa(val etiqueta: String) {
    DEMASIADOS_PEDIDOS("429"),
    CONSULTAS_AUTOMATICAS("consultas_automaticas"),
    LIMITE_DE_PEDIDOS("limite_de_pedidos"),
    CUOTA("cuota"),
    CUOTA_DE_DESCARGA("cuota_de_descarga"),
}

/** Drive está en pausa: no se hizo (ni se hace) ningún pedido más hasta `hastaMs`. Nunca lleva la URL. */
class PausaDriveException(val hastaMs: Long, val motivo: MotivoPausa?) : Exception("Drive pidió una pausa")

/** Decide si una respuesta de error de Drive es "pará un rato" o un error normal de ese archivo. Función pura. */
object ClasificadorRespuestaDrive {
    private val RAZONES_DE_PAUSA = mapOf(
        "ratelimitexceeded" to MotivoPausa.LIMITE_DE_PEDIDOS,
        "userratelimitexceeded" to MotivoPausa.LIMITE_DE_PEDIDOS,
        "dailylimitexceeded" to MotivoPausa.CUOTA,
        "quotaexceeded" to MotivoPausa.CUOTA,
        "downloadquotaexceeded" to MotivoPausa.CUOTA_DE_DESCARGA,
    )
    private val RAZON = Regex("\"reason\"\\s*:\\s*\"([^\"]+)\"")

    /** `cuerpo` es el texto de la respuesta (puede ser null o cortado). Devuelve el motivo si hay que pausar, si no null. */
    fun clasificar(codigoHttp: Int, cuerpo: String?): MotivoPausa? {
        if (codigoHttp == 429) return MotivoPausa.DEMASIADOS_PEDIDOS
        if (codigoHttp != 403 || cuerpo.isNullOrBlank()) return null
        val t = cuerpo.trim()
        if (t.startsWith("<")) {
            val m = t.lowercase()
            return if ("automated queries" in m || "sorry" in m) MotivoPausa.CONSULTAS_AUTOMATICAS else null
        }
        for (r in RAZON.findAll(t)) {
            RAZONES_DE_PAUSA[r.groupValues[1].lowercase()]?.let { return it }
        }
        return null
    }
}

/**
 * "No le pidas nada a Drive hasta T", guardado en un archivo para que lo respeten la app, el protector, el Worker y la
 * reserva (y sobreviva a un reinicio). La espera es 30 min la primera vez y se duplica en pausas seguidas hasta 6 h;
 * una sincronización buena la vuelve a 30 min.
 */
class PausaDrive(private val archivo: File, private val ahora: () -> Long = System::currentTimeMillis) {
    private var cargado = false
    private var hasta = 0L
    private var espera = 0L

    /** Hasta cuándo dura la pausa si sigue vigente; null si no hay. */
    @Synchronized
    fun hastaActivo(ahoraMs: Long = ahora()): Long? {
        cargar()
        return hasta.takeIf { it > ahoraMs }
    }

    /**
     * Anota una pausa nueva y devuelve hasta cuándo. Si ya hay una vigente no la alarga ni duplica la espera
     * (dos pedidos en vuelo que reciben el mismo aviso cuentan una sola vez).
     */
    @Synchronized
    fun registrar(ahoraMs: Long = ahora()): Long {
        cargar()
        if (hasta > ahoraMs) return hasta
        espera = if (espera <= 0L) ESPERA_INICIAL_MS else minOf(espera * 2, ESPERA_MAXIMA_MS)
        hasta = ahoraMs + espera
        guardar()
        return hasta
    }

    /** Una sincronización buena: se olvida la pausa y la próxima espera vuelve a 30 min. */
    @Synchronized
    fun reiniciar() {
        cargar()
        if (hasta == 0L && espera == 0L) return
        hasta = 0L
        espera = 0L
        guardar()
    }

    private fun cargar() {
        if (cargado) return
        cargado = true
        try {
            val partes = archivo.takeIf { it.isFile }?.readText()?.trim()?.split(';') ?: return
            hasta = partes.getOrNull(0)?.toLongOrNull()?.coerceAtLeast(0L) ?: 0L
            espera = partes.getOrNull(1)?.toLongOrNull()?.coerceIn(0L, ESPERA_MAXIMA_MS) ?: 0L
        } catch (e: Exception) {
            hasta = 0L
            espera = 0L
        }
    }

    private fun guardar() {
        try {
            archivo.parentFile?.mkdirs()
            archivo.writeText("$hasta;$espera")
        } catch (e: Exception) {
            // Sin poder guardar, la pausa vale mientras viva el proceso.
        }
    }

    companion object {
        const val ESPERA_INICIAL_MS = 30L * 60_000
        const val ESPERA_MAXIMA_MS = 6L * 60 * 60_000

        @Volatile private var instancia: PausaDrive? = null

        fun de(context: Context): PausaDrive =
            instancia ?: synchronized(this) {
                instancia ?: PausaDrive(File(context.applicationContext.filesDir, "pausa-drive.txt")).also { instancia = it }
            }

        /** "18:43". */
        fun horaTexto(ms: Long): String = SimpleDateFormat("HH:mm", Locale("es", "AR")).format(Date(ms))
    }
}

/** Para que las descargas salgan de a una, con un respiro entre una y otra, vengan de la sincronización o de la reserva. */
class TurnoDescargas {
    @Volatile var ultimaTerminada = 0L

    companion object {
        val GLOBAL = TurnoDescargas()
    }
}

/**
 * Envuelve a la API de Drive: respeta la pausa (con pausa vigente no sale ni un pedido), convierte el "pará un rato" de Google
 * en [PausaDriveException] y anota la pausa, y baja de a una con el ritmo elegido.
 */
class DriveApiConPausa(
    private val api: DriveApi,
    private val pausa: PausaDrive,
    private val ritmoMs: () -> Long = { 0L },
    private val registrar: (String) -> Unit = {},
    private val alPausar: (Long) -> Unit = {},
    private val ahora: () -> Long = System::currentTimeMillis,
    private val esperar: (Long) -> Unit = { Thread.sleep(it) },
    private val turno: TurnoDescargas = TurnoDescargas.GLOBAL,
) : DriveApi {
    override val disponible: Boolean get() = api.disponible

    override fun listar(carpetaId: String): List<ArchivoDrive> = conPausa { api.listar(carpetaId) }

    override fun bajar(archivoId: String, destino: File, maxBytes: Long) {
        synchronized(turno) {
            conPausa {
                val ritmo = ritmoMs()
                if (ritmo > 0) {
                    val falta = ritmo - (ahora() - turno.ultimaTerminada)
                    if (falta > 0) esperar(minOf(falta, ritmo))
                }
                try {
                    api.bajar(archivoId, destino, maxBytes)
                } finally {
                    turno.ultimaTerminada = ahora()
                }
            }
        }
    }

    private fun <T> conPausa(bloque: () -> T): T {
        pausa.hastaActivo(ahora())?.let { throw PausaDriveException(it, null) }
        try {
            return bloque()
        } catch (e: ErrorDrive) {
            val motivo = e.pausa ?: throw e
            val hasta = pausa.registrar(ahora())
            registrar("Drive pidió pausa hasta ${PausaDrive.horaTexto(hasta)} (motivo: ${motivo.etiqueta})")
            try { alPausar(hasta) } catch (x: Exception) { /* el reintento es un extra: la pausa ya está anotada */ }
            throw PausaDriveException(hasta, motivo)
        }
    }
}
