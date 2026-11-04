package local.museumslideshow.reserva

import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import local.museumslideshow.Registro
import local.museumslideshow.catalogo.Catalogo
import local.museumslideshow.ajustes.CaminoObras
import local.museumslideshow.sync.ArchivoExcedeTope
import local.museumslideshow.sync.BajadorConRespaldo
import local.museumslideshow.sync.UrlCopia
import local.museumslideshow.sync.DriveApi
import local.museumslideshow.sync.ErrorDrive
import local.museumslideshow.sync.PausaDriveException
import local.museumslideshow.sync.ProcesadorImagen
import local.museumslideshow.sync.Sincronizador

/** Lo que la galería (o el protector) le dice a la reserva y le pregunta. */
interface EnlaceReserva {
    /** Si la copia de esta obra está en la tele ahora. */
    fun estaBajada(id: String): Boolean

    /** El pase cambió o se movió: `orden` son TODAS las obras del pase en su orden y `posicion` la que está en pantalla. */
    fun actualizar(orden: List<ObraRef>, posicion: Int, extras: List<ObraRef> = emptyList())
}

/** Cuántas obras guardar y cuánto espacio hay como máximo (se leen de los ajustes cada vez). */
data class ParametrosReserva(val n: Int, val topeBytes: Long)

sealed interface ResultadoBajada {
    /** `via` = por quÃ© camino bajÃ³ ("copia s3840" / "original"), solo para el registro. */
    data class Lista(val ms: Long, val via: String? = null) : ResultadoBajada
    data class Falla(val error: ErrorReserva, val detalle: String?) : ResultadoBajada
}

/** Baja UNA obra y la deja lista en la tele. Bloqueante. */
fun interface DescargadorObras {
    fun bajar(obra: ObraRef): ResultadoBajada
}

/** Baja la obra de Drive (la copia de Drive o el original, segÃºn el ajuste), la pasa por el procesador (copia 4K + copia chica) y borra el original: igual que siempre. */
class DescargadorDrive(
    private val api: DriveApi,
    private val dirBase: File,
    private val procesador: ProcesadorImagen,
    private val ahora: () -> Long = System::currentTimeMillis,
    /** Lado largo de la copia que se pide (3840 con calidad 4K, 1920 con 1080). */
    private val lado: () -> Int = { UrlCopia.LADO_4K },
    orden: () -> CaminoObras = { CaminoObras.COPIA_PRIMERO },
) : DescargadorObras {
    private val bajador = BajadorConRespaldo(api, orden)

    override fun bajar(obra: ObraRef): ResultadoBajada {
        val t0 = ahora()
        val tmp = File(dirBase, TMP).also { it.mkdirs() }
        val original = File(tmp, "${obra.id}.orig")
        val copia = File(tmp, "${obra.id}.jpg")
        val chica = File(tmp, "${obra.id}.chica.jpg")
        try {
            val via = try {
                bajador.bajar(obra.id, lado(), original, Sincronizador.MAX_IMAGEN)
            } catch (e: ArchivoExcedeTope) {
                return ResultadoBajada.Falla(ErrorReserva.MUY_GRANDE, null)
            } catch (e: PausaDriveException) {
                // Con un solo camino en pausa solo espera ESTA obra (no tiene el otro camino); con los dos, se corta la tanda.
                return ResultadoBajada.Falla(if (e.todosEnPausa) ErrorReserva.PAUSA else ErrorReserva.PAUSA_ESTA_OBRA, null)
            } catch (e: ErrorDrive) {
                return if (e.codigoHttp == null) ResultadoBajada.Falla(ErrorReserva.SIN_INTERNET, null)
                else ResultadoBajada.Falla(ErrorReserva.DRIVE, e.codigoHttp.toString())
            }
            val medidas = procesador.procesar(original, copia, chica)
            if (medidas == null || !copia.isFile || !chica.isFile) return ResultadoBajada.Falla(ErrorReserva.NO_DECODIFICA, null)
            mover(chica, Catalogo.archivoObraChica(dirBase, obra.id))
            mover(copia, Catalogo.archivoObra(dirBase, obra.id)) // la copia grande va al final: es la que dice "esta obra esta lista"
            return ResultadoBajada.Lista(ahora() - t0, via.etiqueta)
        } finally {
            original.delete(); copia.delete(); chica.delete()
        }
    }

    private fun mover(origen: File, destino: File) {
        destino.parentFile?.mkdirs()
        try {
            Files.move(origen.toPath(), destino.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        } catch (e: Exception) {
            Files.move(origen.toPath(), destino.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
    }

    companion object {
        /** Carpeta de trabajo de la reserva (la sincronización limpia la suya, `tmp`, sin pisar esta). */
        const val TMP = "tmp-reserva"
    }
}

class PedidoReserva(
    val orden: List<ObraRef>,
    val posicion: Int,
    val parametros: ParametrosReserva,
    /** Obras que no entran al pase por no tener medidas: se bajan igual para poder medirlas. */
    val extras: List<ObraRef> = emptyList(),
)

/**
 * Mantiene la reserva: con cada pedido calcula la ventana ([Reserva.planificar]), borra lo que quedó afuera, y si toca una
 * tanda baja lo que falta de a varias obras a la vez. Todo en segundo plano. Un pedido nuevo corta la tanda en curso (la
 * obra que ya se está bajando termina) y empieza otra con la ventana nueva.
 */
class GestorReserva(
    private val scope: CoroutineScope,
    private val dirBase: File,
    private val descargador: DescargadorObras,
    private val parametros: () -> ParametrosReserva,
    private val registrar: (String) -> Unit = {},
    /** De a una (Drive se enoja si se le baja de golpe); el respiro entre descargas lo pone la API ([local.museumslideshow.sync.DriveApiConPausa]). */
    private val paralelo: Int = 1,
    /** Si Google pidió una pausa: no se baja nada (la galería sigue con lo guardado). */
    private val enPausa: () -> Boolean = { false },
    iniciar: Boolean = true,
) : EnlaceReserva {

    private val _estado = MutableStateFlow(EstadoReserva())
    val estado: StateFlow<EstadoReserva> = _estado

    private val pedidos = Channel<PedidoReserva>(Channel.CONFLATED)
    @Volatile private var generacion = 0
    @Volatile private var ventana = VentanaReserva(emptyList(), emptyList())
    private var ultimaLinea = ""

    init {
        File(dirBase, DescargadorDrive.TMP).deleteRecursively()
        refrescar(0)
        if (iniciar) scope.launch { for (p in pedidos) procesar(p, generacion) }
    }

    override fun estaBajada(id: String): Boolean = Catalogo.archivoObra(dirBase, id).isFile

    override fun actualizar(orden: List<ObraRef>, posicion: Int, extras: List<ObraRef>) {
        generacion++
        pedidos.trySend(PedidoReserva(orden, posicion, parametros(), extras))
    }

    /** Vuelve a mirar el disco y publicar los números (por ejemplo después de una sincronización, que también borra). */
    fun releer() = refrescar(_estado.value.bajando)

    /** Los ids que tienen copia grande guardada (se mira el disco: la sincronización también borra). */
    fun bajadasEnDisco(): Set<String> {
        val salida = LinkedHashSet<String>()
        File(dirBase, "obras").listFiles()?.forEach { f ->
            val n = f.name
            if (n.endsWith(".jpg") && !n.endsWith(".chica.jpg")) salida += n.removeSuffix(".jpg")
        }
        return salida
    }

    private fun bytesObras(): Long = File(dirBase, "obras").listFiles()?.sumOf { it.length() } ?: 0L

    private fun bytesTodo(): Long = carpeta(dirBase)

    private fun carpeta(f: File): Long = if (f.isFile) f.length() else (f.listFiles()?.sumOf { carpeta(it) } ?: 0L)

    private fun borrarCopia(id: String) {
        Catalogo.archivoObra(dirBase, id).delete()
        Catalogo.archivoObraChica(dirBase, id).delete()
    }

    /** Un pedido entero: borra, y si toca, baja. `gen` = la generación del pedido (si cambia, se corta la tanda). */
    suspend fun procesar(p: PedidoReserva, gen: Int = generacion) {
        val ids = p.orden.map { it.id }
        if (ids.isEmpty() && p.extras.isEmpty()) return // sin obras en el pase no se toca nada (puede ser un catalogo que todavia no cargo)
        val par = p.parametros
        val plan = Reserva.planificar(ids, p.posicion, par.n, bajadasEnDisco(), extras = p.extras.map { it.id })
        ventana = plan.ventana
        for (id in plan.aBorrar) borrarCopia(id)
        _estado.update { it.copy(conPedido = true) }
        refrescar(0)
        if (plan.aBajar.isEmpty()) return
        if (enPausa()) {
            registrar("Reserva: Drive pidió una pausa; no se baja nada hasta que termine")
            return
        }

        val guardadas = bajadasEnDisco()
        val estimado = if (guardadas.isEmpty()) Reserva.BYTES_POR_OBRA_ESTIMADO else (bytesObras() / guardadas.size).coerceAtLeast(1L)
        val permitidas = Reserva.recortarPorEspacio(plan.aBajar, bytesTodo(), par.topeBytes, estimado)
        val sinEspacio = AtomicBoolean(permitidas.size < plan.aBajar.size)
        if (sinEspacio.get()) {
            registrar("Reserva: no entra todo en el espacio máximo; se bajan ${permitidas.size} de ${plan.aBajar.size}")
        }
        val nombres = (p.orden + p.extras).associate { it.id to it.nombre }
        val pendientes = AtomicInteger(permitidas.size)
        val fallas = AtomicInteger(0)
        refrescar(pendientes.get())
        val corte = AtomicBoolean(false)
        // La bajada es bloqueante: siempre en el dispatcher de entrada/salida, de a varias obras a la vez.
        withContext(Dispatchers.IO) {
            val turnos = Semaphore(paralelo)
            for (id in permitidas) {
                launch {
                    turnos.withPermit {
                        try {
                            bajarUna(id, nombres[id] ?: id, par, gen, corte, sinEspacio, fallas)
                        } finally {
                            refrescar(pendientes.decrementAndGet())
                        }
                    }
                }
            }
        }
        // Al terminar la tanda: el aviso de espacio se queda si faltó lugar; si todo salió bien se limpian los errores.
        _estado.update { s ->
            when {
                sinEspacio.get() -> s.copy(ultimoError = ErrorReserva.ESPACIO_LLENO, detalleError = null)
                fallas.get() == 0 && generacion == gen -> s.copy(ultimoError = null, detalleError = null, sinInternet = false)
                else -> s
            }
        }
        refrescar(0)
    }

    private fun bajarUna(
        id: String,
        nombre: String,
        par: ParametrosReserva,
        gen: Int,
        corte: AtomicBoolean,
        sinEspacio: AtomicBoolean,
        fallas: AtomicInteger,
    ) {
        if (corte.get() || generacion != gen) return
        if (bytesTodo() >= par.topeBytes) {
            corte.set(true)
            sinEspacio.set(true)
            return
        }
        if (enPausa()) {
            corte.set(true)
            return
        }
        when (val r = descargador.bajar(ObraRef(id, nombre))) {
            is ResultadoBajada.Lista -> {
                registrar("Reserva: obra lista ${Registro.limpiar(nombre)} en ${r.ms} ms" + (r.via?.let { " (por $it)" } ?: ""))
                _estado.update { it.copy(version = it.version + 1, sinInternet = false) }
            }
            is ResultadoBajada.Falla -> {
                if (r.error == ErrorReserva.PAUSA_ESTA_OBRA) {
                    // Esta obra espera a que termine la pausa de un camino; las demÃ¡s siguen (sin marcar error).
                    registrar("Reserva: ${Registro.limpiar(nombre)} espera a que termine la pausa de Drive")
                    return
                }
                if (r.error == ErrorReserva.PAUSA) {
                    corte.set(true)
                    registrar("Reserva: Drive pidió una pausa; se corta la tanda")
                    return
                }
                fallas.incrementAndGet()
                val extra = r.detalle?.let { " $it" } ?: ""
                registrar("Reserva: no se pudo bajar ${Registro.limpiar(nombre)} (${r.error}$extra)")
                _estado.update { it.copy(ultimoError = r.error, detalleError = r.detalle, sinInternet = r.error == ErrorReserva.SIN_INTERNET) }
                if (r.error == ErrorReserva.SIN_INTERNET) corte.set(true)
            }
        }
    }

    /** Recalcula los números desde el disco y, si cambiaron, los publica y los anota en el registro. */
    @Synchronized
    private fun refrescar(bajando: Int) {
        val enDisco = bajadasEnDisco()
        val vent = ventana
        val listas = vent.adelante.count { it in enDisco }
        val vistas = vent.vistas.count { it in enDisco }
        val bytes = bytesObras()
        val b = bajando.coerceAtLeast(0)
        _estado.update {
            it.copy(
                listasPorDelante = listas, porDelante = vent.adelante.size, vistasGuardadas = vistas,
                obrasGuardadas = enDisco.size, bytes = bytes, bajando = b,
            )
        }
        val mb = bytes / (1024 * 1024)
        val linea = "Reserva: listas $listas de ${vent.adelante.size} por delante, $vistas vistas guardadas, $mb MB; bajando $b"
        if (linea != ultimaLinea) {
            ultimaLinea = linea
            registrar(linea)
        }
    }
}
