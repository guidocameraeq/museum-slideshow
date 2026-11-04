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

/**
 * Drive está en pausa por `camino`: no se hizo (ni se hace) ningún pedido más por ahí hasta `hastaMs`. Nunca lleva la URL.
 * `todosEnPausa` = no había otro camino para probar (los dos están en pausa); si es falso, solo esta obra espera.
 */
class PausaDriveException(
    val hastaMs: Long,
    val motivo: MotivoPausa?,
    val camino: CaminoDrive = CaminoDrive.ORIGINAL,
    val todosEnPausa: Boolean = true,
) : Exception("Drive pidió una pausa")

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
 * "No le pidas nada a Drive por este camino hasta T", guardado en un archivo para que lo respeten la app, el protector, el Worker
 * y la reserva (y sobreviva a un reinicio). Hay una pausa por camino ([CaminoDrive.ORIGINAL] y [CaminoDrive.COPIA]) y son
 * independientes. La espera es 30 min la primera vez y se duplica en pausas seguidas hasta 6 h; una bajada buena la vuelve a 30 min.
 *
 * Archivo: una línea por camino, `original=hasta;espera` y `copia=hasta;espera`. El formato viejo (`hasta;espera`, una sola línea,
 * de cuando había un solo camino) se lee como ORIGINAL.
 */
class PausaDrive(private val archivo: File, private val ahora: () -> Long = System::currentTimeMillis) {
    private class Estado { var hasta = 0L; var espera = 0L }

    private var cargado = false
    private val estados = CaminoDrive.values().associateWith { Estado() }

    /** Hasta cuándo dura la pausa de ese camino si sigue vigente; null si no hay. */
    @Synchronized
    fun hastaActivo(ahoraMs: Long = ahora(), camino: CaminoDrive = CaminoDrive.ORIGINAL): Long? {
        cargar()
        return estados.getValue(camino).hasta.takeIf { it > ahoraMs }
    }

    /** ¿Están en pausa los dos caminos a la vez? Ahí no se puede bajar nada. */
    @Synchronized
    fun todosEnPausa(ahoraMs: Long = ahora()): Boolean = CaminoDrive.values().all { hastaActivo(ahoraMs, it) != null }

    /**
     * Anota una pausa nueva de ese camino y devuelve hasta cuándo. Si ya hay una vigente no la alarga ni duplica la espera
     * (dos pedidos en vuelo que reciben el mismo aviso cuentan una sola vez).
     */
    @Synchronized
    fun registrar(ahoraMs: Long = ahora(), camino: CaminoDrive = CaminoDrive.ORIGINAL): Long {
        cargar()
        val e = estados.getValue(camino)
        if (e.hasta > ahoraMs) return e.hasta
        e.espera = if (e.espera <= 0L) ESPERA_INICIAL_MS else minOf(e.espera * 2, ESPERA_MAXIMA_MS)
        e.hasta = ahoraMs + e.espera
        guardar()
        return e.hasta
    }

    /** Una bajada buena por ese camino: se olvida su pausa y la próxima espera vuelve a 30 min. */
    @Synchronized
    fun reiniciar(camino: CaminoDrive = CaminoDrive.ORIGINAL) {
        cargar()
        val e = estados.getValue(camino)
        if (e.hasta == 0L && e.espera == 0L) return
        e.hasta = 0L
        e.espera = 0L
        guardar()
    }

    private fun cargar() {
        if (cargado) return
        cargado = true
        try {
            val texto = archivo.takeIf { it.isFile }?.readText()?.trim() ?: return
            if (texto.isEmpty()) return
            if ('=' !in texto) {
                leerEstado(estados.getValue(CaminoDrive.ORIGINAL), texto) // formato viejo: un solo camino
                return
            }
            for (linea in texto.lines()) {
                val kv = linea.trim().split('=', limit = 2)
                if (kv.size != 2) continue
                val camino = CaminoDrive.values().firstOrNull { it.clave == kv[0].trim() } ?: continue
                leerEstado(estados.getValue(camino), kv[1])
            }
        } catch (e: Exception) {
            estados.values.forEach { it.hasta = 0L; it.espera = 0L }
        }
    }

    private fun leerEstado(e: Estado, texto: String) {
        val partes = texto.trim().split(';')
        e.hasta = partes.getOrNull(0)?.trim()?.toLongOrNull()?.coerceAtLeast(0L) ?: 0L
        e.espera = partes.getOrNull(1)?.trim()?.toLongOrNull()?.coerceIn(0L, ESPERA_MAXIMA_MS) ?: 0L
    }

    private fun guardar() {
        try {
            archivo.parentFile?.mkdirs()
            archivo.writeText(CaminoDrive.values().joinToString("\n") { c -> estados.getValue(c).let { "${c.clave}=${it.hasta};${it.espera}" } })
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
 * Envuelve a la API de Drive: respeta la pausa de cada camino (con la pausa vigente no sale ni un pedido por ese camino),
 * convierte el "pará un rato" de Google en [PausaDriveException] y anota la pausa, y baja de a una con el ritmo elegido.
 * Listar y bajar el original son el camino ORIGINAL; bajar la copia (incluye pedir su `thumbnailLink`) es el camino COPIA.
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

    override fun listar(carpetaId: String): List<ArchivoDrive> = conPausa(CaminoDrive.ORIGINAL) { api.listar(carpetaId) }

    override fun bajar(archivoId: String, destino: File, maxBytes: Long) {
        conTurno(CaminoDrive.ORIGINAL) { api.bajar(archivoId, destino, maxBytes) }
    }

    override fun bajarCopia(archivoId: String, lado: Int, destino: File, maxBytes: Long) {
        conTurno(CaminoDrive.COPIA) {
            api.bajarCopia(archivoId, lado, destino, maxBytes)
            pausa.reiniciar(CaminoDrive.COPIA) // una copia bajada bien: la próxima pausa, si llega, vuelve a empezar en 30 min
        }
    }

    private fun conTurno(camino: CaminoDrive, bajada: () -> Unit) {
        synchronized(turno) {
            conPausa(camino) {
                val ritmo = ritmoMs()
                if (ritmo > 0) {
                    val falta = ritmo - (ahora() - turno.ultimaTerminada)
                    if (falta > 0) esperar(minOf(falta, ritmo))
                }
                try {
                    bajada()
                } finally {
                    turno.ultimaTerminada = ahora()
                }
            }
        }
    }

    private fun <T> conPausa(camino: CaminoDrive, bloque: () -> T): T {
        pausa.hastaActivo(ahora(), camino)?.let { throw PausaDriveException(it, null, camino) }
        try {
            return bloque()
        } catch (e: ErrorDrive) {
            val motivo = e.pausa ?: throw e
            val hasta = pausa.registrar(ahora(), camino)
            val de = if (camino == CaminoDrive.COPIA) " de la copia" else ""
            registrar("Drive pidió pausa$de hasta ${PausaDrive.horaTexto(hasta)} (motivo: ${motivo.etiqueta})")
            try { alPausar(hasta) } catch (x: Exception) { /* el reintento es un extra: la pausa ya está anotada */ }
            throw PausaDriveException(hasta, motivo, camino)
        }
    }
}
