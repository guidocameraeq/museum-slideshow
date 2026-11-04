package local.museumslideshow

import android.content.Context
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import local.museumslideshow.ajustes.AlmacenAjustes
import local.museumslideshow.ajustes.Calidad
import local.museumslideshow.ajustes.Preferencias
import local.museumslideshow.ajustes.SincronizarCada
import local.museumslideshow.catalogo.Catalogo
import local.museumslideshow.catalogo.CatalogoCargado
import local.museumslideshow.galeria.ConstantesGaleria
import local.museumslideshow.galeria.Pase
import local.museumslideshow.protector.Interruptor
import local.museumslideshow.reserva.DescargadorDrive
import local.museumslideshow.reserva.GestorReserva
import local.museumslideshow.reserva.ObraRef
import local.museumslideshow.reserva.ParametrosReserva
import local.museumslideshow.sync.PausaDrive
import local.museumslideshow.sync.ProcesadorImagenAndroid
import local.museumslideshow.sync.ReglaSincronizacion
import local.museumslideshow.sync.ResultadoSync
import local.museumslideshow.sync.Sincronizacion
import local.museumslideshow.sync.SincronizarWorker
import local.museumslideshow.sync.UrlCopia
import local.museumslideshow.sync.medirImagen
import local.museumslideshow.temas.RegistroTemas

/**
 * Lo que la app y el protector comparten, una sola vez por proceso: los ajustes, el catálogo cargado, la sincronización y
 * la reserva de obras (una sola [GestorReserva]: dos escribiendo la misma carpeta se pisarían). Vive mientras viva el proceso.
 * El estado se lee desde la composición (son estados de Compose); todo se toca desde el hilo principal.
 */
class MotorApp private constructor(context: Context) {
    private val ctx = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    val almacen: AlmacenAjustes = AlmacenAjustes.de(ctx)

    /** null hasta que se leen los ajustes guardados (un instante al abrir). */
    var preferencias by mutableStateOf<Preferencias?>(null)
        private set
    var catalogo by mutableStateOf<CatalogoCargado?>(null)
        private set
    var sincronizando by mutableStateOf(false)
        private set
    var mensajeSync by mutableStateOf<String?>(null)
        private set

    /** El interruptor "Usar como protector / Volver al anterior". */
    val interruptor: Interruptor by lazy { Interruptor(ctx) }

    /** La reserva de obras: vive con el proceso (sigue bajando aunque no haya galería abierta). */
    private val scopeReserva = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    val reserva: GestorReserva by lazy {
        val dir = Sincronizacion.dirBase(ctx)
        GestorReserva(
            scope = scopeReserva,
            dirBase = dir,
            descargador = DescargadorDrive(Sincronizacion.crearApi(ctx) { preferencias ?: Preferencias() }, dir, ProcesadorImagenAndroid(),
                lado = { if ((preferencias ?: Preferencias()).calidad == Calidad.HD1080) UrlCopia.LADO_1080 else UrlCopia.LADO_4K },
                orden = { (preferencias ?: Preferencias()).caminoObras },
            ),
            enPausa = { PausaDrive.de(ctx).todosEnPausa() },
            parametros = {
                val p = preferencias ?: Preferencias()
                ParametrosReserva(p.reservaObras, p.espacioMaximo.bytes)
            },
            registrar = { Log.i(Registro.TAG, Registro.limpiar(it)) },
        )
    }
    private var claveReserva: List<Any?>? = null

    /** Quiénes tienen una galería abierta (la app, el protector): ellos le dicen a la reserva dónde va el pase. */
    private val galeriasAbiertas = HashSet<Any>()

    private var iniciado = false
    private var arranque: Job? = null
    private var cadaProgramada: SincronizarCada? = null
    private var carpetaVista: String? = null
    private var primeraLectura = true
    private var repetirSync = false

    /** Quien abre o cierra su galería lo avisa acá (`dueno` = la Activity o el protector). */
    fun galeriaAbierta(dueno: Any, abierta: Boolean) {
        if (abierta) galeriasAbiertas.add(dueno) else galeriasAbiertas.remove(dueno)
    }

    /**
     * Con ninguna galería abierta nadie le dice a la reserva dónde va el pase: se lo calcula acá, con el mismo orden y la misma
     * mezcla que la galería (empieza en la primera obra, que es donde abre). Con una galería abierta lo hace ella.
     */
    fun pedirReservaSinGaleria() {
        if (galeriasAbiertas.isNotEmpty()) return
        val cat = catalogo ?: return
        val p = preferencias ?: return
        val pase = Pase.armar(cat.galerias, p, RegistroTemas.de(p.tema), Pase.SEMILLA_SESION)
        val extras = Pase.obrasSinMedidas(cat.galerias, p).take(ConstantesGaleria.MAX_SIN_MEDIDAS).map { ObraRef(it.driveId, it.archivo) }
        if (pase == null) {
            if (extras.isNotEmpty()) reserva.actualizar(emptyList(), 0, extras)
            return
        }
        reserva.actualizar(pase.ordenDeObras().map { ObraRef(it.driveId, it.archivo) }, 0, extras)
    }

    /**
     * Arranca (una sola vez por proceso): sigue a los ajustes guardados, vigila la reserva y carga el catálogo.
     * Cambiar un ajuste (Barra, Ajustes o adb) se ve en ese momento.
     */
    fun iniciar() {
        if (iniciado) return
        iniciado = true
        scope.launch {
            almacen.flow.collect { p ->
                preferencias = p
                Log.i(Registro.TAG, Registro.limpiar("Preferencias efectivas: $p"))
                alCambiarAjustes(p)
                val clave = listOf(p.seleccionGalerias, p.galeriasNuevas, p.orden, p.galeria, p.pared, p.tema, p.reservaObras, p.espacioMaximo)
                if (clave != claveReserva) {
                    claveReserva = clave
                    pedirReservaSinGaleria()
                }
            }
        }
        // Si Drive no dio las medidas de alguna obra, la reserva la baja para poder medirla: cuando llega, se recarga el catálogo.
        scope.launch {
            reserva.estado.map { it.version }.distinctUntilChanged().collect {
                val hay = catalogo?.galerias?.any { g -> g.obras.any { o -> !o.conMedidas } } == true
                if (hay) {
                    delay(800)
                    recargar()
                }
            }
        }
        arranque = scope.launch { recargar() }
    }

    /** Un ajuste cambió (o se leyeron al abrir): reprograma la sincronización o sincroniza si cambió la carpeta. */
    private fun alCambiarAjustes(p: Preferencias) {
        if (p.sincronizarCada != cadaProgramada) {
            cadaProgramada = p.sincronizarCada
            try {
                SincronizarWorker.programar(ctx, p.sincronizarCada)
            } catch (e: Exception) {
                Log.w(Registro.TAG, "No se pudo programar la sincronización (${e.javaClass.simpleName})")
            }
        }
        if (!primeraLectura && p.carpetaDrive != carpetaVista) {
            Log.i(Registro.TAG, "Cambió la carpeta de Drive: se sincroniza")
            sincronizarAhora()
        }
        carpetaVista = p.carpetaDrive
        primeraLectura = false
    }

    fun cambiarAjustes(transform: (Preferencias) -> Preferencias) {
        scope.launch { almacen.cambiar(transform) }
    }

    /** Sincroniza ya (al abrir, desde la Barra o al cambiar la carpeta). Si ya hay una en curso, pide otra al terminar. */
    fun sincronizarAhora() {
        if (sincronizando) {
            repetirSync = true
            return
        }
        sincronizando = true
        mensajeSync = null
        scope.launch {
            try {
                arranque?.join()
                do {
                    repetirSync = false
                    val r = withContext(Dispatchers.IO) { Sincronizacion.ejecutar(ctx) }
                    // Recarga el catálogo sin cortar la escena: la galería conserva la obra actual si sigue existiendo.
                    recargar()
                    mensajeSync = mensajeDe(r)
                } while (repetirSync)
            } finally {
                sincronizando = false
            }
            val m = mensajeSync
            delay(MENSAJE_MS)
            if (mensajeSync == m) mensajeSync = null
        }
    }

    /** Al arrancar el protector: sincroniza solo si toca según "Sincronizar cada" y la última sincronización buena. */
    fun sincronizarSiToca() {
        scope.launch {
            arranque?.join()
            if (sincronizando) return@launch
            PausaDrive.de(ctx).hastaActivo()?.let {
                Log.i(Registro.TAG, "Protector: Drive en pausa hasta ${PausaDrive.horaTexto(it)}; no se sincroniza")
                return@launch
            }
            val cada = try { almacen.flow.first().sincronizarCada } catch (e: Exception) { return@launch }
            val toca = ReglaSincronizacion.tocaAlArrancar(cada, catalogo?.ultimaSincronizacionBuena, System.currentTimeMillis())
            Log.i(Registro.TAG, "Protector: ¿toca sincronizar? $toca (cada=$cada)")
            if (toca) sincronizarAhora()
        }
    }

    private fun mensajeDe(r: ResultadoSync): String = when {
        r.pausaHastaMs != null -> ctx.getString(
            if (preferencias?.sincronizarCada?.horas == null) R.string.sync_pausa_mano else R.string.sync_pausa,
            PausaDrive.horaTexto(r.pausaHastaMs),
        )
        !r.ok -> ctx.getString(R.string.sync_fallo)
        r.problemas.isNotEmpty() -> ctx.getString(R.string.sync_con_avisos)
        r.bajadas + r.borradas == 0 -> ctx.getString(R.string.sync_al_dia)
        else -> ctx.getString(
            R.string.sync_listo,
            ctx.resources.getQuantityString(R.plurals.cantidad_bajadas, r.bajadas, r.bajadas),
            ctx.resources.getQuantityString(R.plurals.cantidad_borradas, r.borradas, r.borradas),
        )
    }

    /** Vuelve a leer el catálogo del disco (y le avisa a la reserva). */
    suspend fun recargar() {
        catalogo = withContext(Dispatchers.IO) { Catalogo.cargar(Sincronizacion.dirBase(ctx), ::medirImagen) }
        reserva.releer()
        pedirReservaSinGaleria()
    }

    /** El catálogo ya cargado o, si todavía no hay, uno leído del disco ahora (para los extras de prueba por adb). */
    suspend fun catalogoAhora(): CatalogoCargado =
        catalogo ?: withContext(Dispatchers.IO) { Catalogo.cargar(Sincronizacion.dirBase(ctx), ::medirImagen) }

    companion object {
        /** Cuánto queda en la Barra el resultado de la última sincronización. */
        private const val MENSAJE_MS = 8000L

        @Volatile private var instancia: MotorApp? = null

        fun de(context: Context): MotorApp =
            instancia ?: synchronized(this) {
                instancia ?: MotorApp(context.applicationContext).also { instancia = it }
            }
    }
}
