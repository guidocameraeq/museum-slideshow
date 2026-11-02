package local.museumslideshow

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import local.museumslideshow.ajustes.AlmacenAjustes
import local.museumslideshow.ajustes.GaleriaOpcion
import local.museumslideshow.ajustes.GrupoAjustes
import local.museumslideshow.ajustes.ListaAjustes
import local.museumslideshow.ajustes.delGrupo
import local.museumslideshow.ajustes.Preferencias
import local.museumslideshow.ajustes.PreferenciasDesdeTexto
import local.museumslideshow.ajustes.SincronizarCada
import local.museumslideshow.ajustes.estaTildada
import local.museumslideshow.catalogo.Arbol
import local.museumslideshow.catalogo.Catalogo
import local.museumslideshow.galeria.ConstantesGaleria
import local.museumslideshow.galeria.Pase
import local.museumslideshow.reserva.DescargadorDrive
import local.museumslideshow.reserva.GestorReserva
import local.museumslideshow.reserva.ObraRef
import local.museumslideshow.reserva.ParametrosReserva
import local.museumslideshow.sync.DriveApiHttp
import local.museumslideshow.sync.ProcesadorImagenAndroid
import local.museumslideshow.sync.medirImagen
import local.museumslideshow.temas.RegistroTemas
import local.museumslideshow.catalogo.CatalogoCargado
import local.museumslideshow.galeria.AccionesApp
import local.museumslideshow.galeria.GaleriaEnPantalla
import local.museumslideshow.galeria.SaltoEscena
import local.museumslideshow.pantallas.DatosSync
import local.museumslideshow.pantallas.DestinoInicio
import local.museumslideshow.pantallas.PantallaAjustesGrupo
import local.museumslideshow.pantallas.PantallaApp
import local.museumslideshow.pantallas.PantallaGalerias
import local.museumslideshow.pantallas.PantallaInicio
import local.museumslideshow.pantallas.PantallaProtector
import local.museumslideshow.pantallas.ResumenInicio
import local.museumslideshow.pantallas.PantallaEstado
import local.museumslideshow.pantallas.PantallaSinObras
import local.museumslideshow.pantallas.SinObras
import local.museumslideshow.sync.ResultadoSync
import local.museumslideshow.sync.Sincronizacion
import local.museumslideshow.sync.SincronizarWorker

class MainActivity : ComponentActivity() {
    private var catalogo by mutableStateOf<CatalogoCargado?>(null)
    private var sincronizando by mutableStateOf(false)
    private var mensajeSync by mutableStateOf<String?>(null)

    /** null hasta que se leen los ajustes guardados (un instante al abrir). */
    private var preferencias by mutableStateOf<Preferencias?>(null)
    private var salto by mutableStateOf<SaltoEscena?>(null)

    /** La pantalla al frente (la Barra no cuenta: vive dentro de la galería). Al abrir la app es Inicio. */
    private var pantalla by mutableStateOf(PantallaApp.INICIO)

    /** A dónde vuelve Atrás desde las pantallas de ajustes, Protector, Galerías y Estado. */
    private var atras by mutableStateOf(PantallaApp.INICIO)

    /** La galería está armada (se entró con "Ver la galería"). Sale de Inicio => se libera. */
    private var galeriaAbierta by mutableStateOf(false)
    private var focoInicio by mutableIntStateOf(0)

    private val almacen by lazy { AlmacenAjustes.de(applicationContext) }

    /** La reserva de obras: vive con la app (sigue bajando aunque la galería no esté abierta). */
    private val scopeReserva = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val reserva: GestorReserva by lazy {
        val dir = Sincronizacion.dirBase(applicationContext)
        GestorReserva(
            scope = scopeReserva,
            dirBase = dir,
            descargador = DescargadorDrive(DriveApiHttp(BuildConfig.DRIVE_API_KEY), dir, ProcesadorImagenAndroid()),
            parametros = {
                val p = preferencias ?: Preferencias()
                ParametrosReserva(p.reservaObras, p.espacioMaximo.bytes)
            },
            registrar = { Log.i(Registro.TAG, Registro.limpiar(it)) },
        )
    }
    private var claveReserva: List<Any?>? = null

    /**
     * Con la galería cerrada nadie le dice a la reserva dónde va el pase: se lo calcula acá, con el mismo orden y la misma
     * mezcla que la galería (empieza en la primera obra, que es donde abre). Con la galería abierta lo hace ella.
     */
    private fun pedirReservaSinGaleria() {
        if (galeriaAbierta) return
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

    override fun onDestroy() {
        super.onDestroy()
        scopeReserva.cancel()
    }
    private var cadaProgramada: SincronizarCada? = null
    private var carpetaVista: String? = null
    private var primeraLectura = true
    private var repetirSync = false

    private val acciones = AccionesApp(
        cambiar = { t -> cambiarAjustes(t) },
        sincronizar = { sincronizarAhora() },
        // El botón "Ajustes" de la Barra abre Apariencia (lo que se toca mirando la obra); Atrás vuelve a la galería.
        abrirAjustes = { irA(PantallaApp.APARIENCIA, atrasA = PantallaApp.GALERIA) },
    )

    /** Cambia de pantalla. La galería se arma al entrar y se suelta al volver a Inicio; los ajustes abiertos desde ella la dejan viva. */
    private fun irA(destino: PantallaApp, atrasA: PantallaApp = PantallaApp.INICIO) {
        when {
            destino == PantallaApp.GALERIA -> galeriaAbierta = true
            destino == PantallaApp.INICIO -> { galeriaAbierta = false; salto = null; pedirReservaSinGaleria() }
            atrasA != PantallaApp.GALERIA -> galeriaAbierta = false
        }
        atras = atrasA
        pantalla = destino
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.i(Registro.TAG, "Arranca la app (versión ${BuildConfig.VERSION_NAME})")
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContent { Contenido() }

        lifecycleScope.launch {
            aplicarIntent(intent)
            // La pantalla sigue a los ajustes guardados: cambiar uno (Barra, Ajustes o adb) se ve en ese momento.
            launch {
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
            launch {
                reserva.estado.map { it.version }.distinctUntilChanged().collect {
                    val hay = catalogo?.galerias?.any { g -> g.obras.any { o -> !o.conMedidas } } == true
                    if (hay) {
                        delay(800)
                        recargar()
                    }
                }
            }
            recargar()
            sincronizarAhora()
        }
    }

    /** Para probar por adb sin reiniciar: `am start -n local.museumslideshow/.MainActivity --es tema sala-oscura`. */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        lifecycleScope.launch { aplicarIntent(intent) }
    }

    /**
     * Los extras del intent son cambios de ajustes (mismas claves que [PreferenciasDesdeTexto]) y se GUARDAN;
     * `reset` vuelve todo a los valores por defecto (también guardado). `escena` = índice (desde 0) donde arrancar.
     * `pantalla` = inicio, galeria, galerias, apariencia, pase, conexion, protector o estado: abre esa pantalla directo.
     * Sin ningún extra (abrir desde Google TV) abre Inicio.
     */
    private suspend fun aplicarIntent(i: Intent?) {
        val extras = i?.extras
        val texto = HashMap<String, String>()
        if (extras != null) {
            for (k in extras.keySet()) {
                @Suppress("DEPRECATION")
                extras.get(k)?.toString()?.let { texto[k] = it }
            }
        }
        if (texto.isNotEmpty()) {
            val reset = texto.containsKey("reset")
            almacen.cambiar { actual -> PreferenciasDesdeTexto.aplicar(if (reset) Preferencias() else actual, texto) }
            aplicarTildes(texto)
            texto["escena"]?.toIntOrNull()?.let {
                salto = SaltoEscena(it)
                Log.i(Registro.TAG, "Salto a la escena $it")
            }
        }
        PantallaApp.deIntent(texto)?.let { irA(it) }
    }

    /** Extras `tildar` y `destildar` (id o nombre de carpeta, sin importar mayúsculas ni tildes): para probar antes de la pantalla Galerías. */
    private suspend fun aplicarTildes(texto: Map<String, String>) {
        val pedidos = listOfNotNull(texto["tildar"]?.let { it to true }, texto["destildar"]?.let { it to false })
        if (pedidos.isEmpty()) return
        val cat = catalogo ?: withContext(Dispatchers.IO) { Catalogo.cargar(Sincronizacion.dirBase(applicationContext), ::medirImagen) }
        val arbol = Arbol(cat)
        almacen.cambiar { actual ->
            var p = actual
            for ((nombre, valor) in pedidos) {
                val ids = arbol.resolver(nombre)
                Log.i(Registro.TAG, Registro.limpiar("Tildes: ${if (valor) "tildar" else "destildar"} '$nombre' -> ${ids.size} carpetas"))
                for (id in ids) p = arbol.tildar(p, id, valor)
            }
            p
        }
    }

    /** Un ajuste cambió (o se leyeron al abrir): reprograma la sincronización o sincroniza si cambió la carpeta. */
    private fun alCambiarAjustes(p: Preferencias) {
        if (p.sincronizarCada != cadaProgramada) {
            cadaProgramada = p.sincronizarCada
            try {
                SincronizarWorker.programar(applicationContext, p.sincronizarCada)
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

    private fun cambiarAjustes(transform: (Preferencias) -> Preferencias) {
        lifecycleScope.launch { almacen.cambiar(transform) }
    }

    /** Sincroniza ya (al abrir, desde la Barra o al cambiar la carpeta). Si ya hay una en curso, pide otra al terminar. */
    private fun sincronizarAhora() {
        if (sincronizando) {
            repetirSync = true
            return
        }
        sincronizando = true
        mensajeSync = null
        lifecycleScope.launch {
            try {
                do {
                    repetirSync = false
                    val r = withContext(Dispatchers.IO) { Sincronizacion.ejecutar(applicationContext) }
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

    private fun mensajeDe(r: ResultadoSync): String = when {
        !r.ok -> getString(R.string.sync_fallo)
        r.problemas.isNotEmpty() -> getString(R.string.sync_con_avisos)
        r.bajadas + r.borradas == 0 -> getString(R.string.sync_al_dia)
        else -> getString(
            R.string.sync_listo,
            resources.getQuantityString(R.plurals.cantidad_bajadas, r.bajadas, r.bajadas),
            resources.getQuantityString(R.plurals.cantidad_borradas, r.borradas, r.borradas),
        )
    }

    private suspend fun recargar() {
        catalogo = withContext(Dispatchers.IO) { Catalogo.cargar(Sincronizacion.dirBase(applicationContext), ::medirImagen) }
        reserva.releer()
        pedirReservaSinGaleria()
    }

    @Composable
    private fun Contenido() {
        val p = preferencias
        if (p == null) {
            Box(Modifier.fillMaxSize().background(Color.Black))
            return
        }
        val cat = catalogo
        val estadoReserva by reserva.estado.collectAsState()
        val tildadas = cat?.galerias.orEmpty().filter { p.estaTildada(it.driveId) }
        val hayObras = tildadas.sumOf { it.obras.size } > 0
        val todos = remember(cat, p.seleccionGalerias, p.galeriasNuevas) { ListaAjustes.construir(tildadas.map { GaleriaOpcion(it.driveId, it.titulo) }) }
        val volver = { irA(atras) }
        Box(Modifier.fillMaxSize()) {
            if (galeriaAbierta) {
                if (cat != null && hayObras) {
                    GaleriaEnPantalla(
                        cat, p,
                        alSalir = { irA(PantallaApp.INICIO) },
                        salto = salto,
                        app = acciones,
                        sincronizando = sincronizando,
                        mensajeSync = mensajeSync,
                        teclasActivas = pantalla == PantallaApp.GALERIA,
                        reserva = reserva,
                    )
                } else {
                    PantallaSinObras(
                        SinObras.motivo(sincronizando, cat, hayTildadas = tildadas.isNotEmpty()),
                        activa = pantalla == PantallaApp.GALERIA,
                        alVerEstado = { irA(PantallaApp.ESTADO, atrasA = PantallaApp.GALERIA) },
                        alVolver = { irA(PantallaApp.INICIO) },
                    )
                }
            }
            key(pantalla) {
                when (pantalla) {
                    PantallaApp.INICIO -> PantallaInicio(
                        cat, p, focoInicio,
                        alCambiarFoco = { focoInicio = it },
                        alAbrir = { destino -> abrirDesdeInicio(destino) },
                        alSalir = { finish() },
                        reserva = estadoReserva,
                    )
                    PantallaApp.GALERIA -> Unit
                    PantallaApp.GALERIAS -> PantallaGalerias(activa = true, alVolver = volver)
                    PantallaApp.APARIENCIA, PantallaApp.PASE, PantallaApp.CONEXION -> {
                        val grupo = when (pantalla) {
                            PantallaApp.APARIENCIA -> GrupoAjustes.APARIENCIA
                            PantallaApp.PASE -> GrupoAjustes.PASE
                            else -> GrupoAjustes.CONEXION
                        }
                        PantallaAjustesGrupo(
                            grupo, todos.delGrupo(grupo), p,
                            activa = true,
                            cambiar = { t -> cambiarAjustes(t) },
                            alVolver = volver,
                            obraPrevia = ResumenInicio.de(cat, p).tapa,
                            galeriaPrevia = ResumenInicio.de(cat, p).tapaGaleriaId,
                            reserva = estadoReserva,
                            sync = if (grupo == GrupoAjustes.CONEXION) DatosSync(sincronizando, mensajeSync, cat?.ultimaSincronizacionBuena) else null,
                            alSincronizar = { sincronizarAhora() },
                        )
                    }
                    PantallaApp.PROTECTOR -> PantallaProtector(activa = true, alVolver = volver)
                    PantallaApp.ESTADO -> PantallaEstado(cat, p, sincronizando, alVolver = volver, reserva = estadoReserva)
                }
            }
        }
    }

    private fun abrirDesdeInicio(destino: DestinoInicio) = irA(
        when (destino) {
            DestinoInicio.VER_GALERIA -> PantallaApp.GALERIA
            DestinoInicio.GALERIAS -> PantallaApp.GALERIAS
            DestinoInicio.APARIENCIA -> PantallaApp.APARIENCIA
            DestinoInicio.PASE -> PantallaApp.PASE
            DestinoInicio.CONEXION -> PantallaApp.CONEXION
            DestinoInicio.PROTECTOR -> PantallaApp.PROTECTOR
            DestinoInicio.ESTADO -> PantallaApp.ESTADO
        },
    )

    private companion object {
        /** Cuánto queda en la Barra el resultado de la última sincronización. */
        const val MENSAJE_MS = 8000L
    }
}
