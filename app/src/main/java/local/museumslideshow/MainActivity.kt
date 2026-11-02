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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
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
import local.museumslideshow.catalogo.Catalogo
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
            destino == PantallaApp.INICIO -> { galeriaAbierta = false; salto = null }
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
            texto["escena"]?.toIntOrNull()?.let {
                salto = SaltoEscena(it)
                Log.i(Registro.TAG, "Salto a la escena $it")
            }
        }
        PantallaApp.deIntent(texto)?.let { irA(it) }
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
        catalogo = withContext(Dispatchers.IO) { Catalogo.cargar(Sincronizacion.dirBase(applicationContext)) }
    }

    @Composable
    private fun Contenido() {
        val p = preferencias
        if (p == null) {
            Box(Modifier.fillMaxSize().background(Color.Black))
            return
        }
        val cat = catalogo
        val hayObras = (cat?.galerias?.sumOf { it.obras.size } ?: 0) > 0
        val todos = remember(cat) { ListaAjustes.construir(cat?.galerias.orEmpty().map { GaleriaOpcion(it.driveId, it.titulo) }) }
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
                    )
                } else {
                    PantallaSinObras(
                        SinObras.motivo(sincronizando, cat),
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
                            sync = if (grupo == GrupoAjustes.CONEXION) DatosSync(sincronizando, mensajeSync, cat?.ultimaSincronizacionBuena) else null,
                            alSincronizar = { sincronizarAhora() },
                        )
                    }
                    PantallaApp.PROTECTOR -> PantallaProtector(activa = true, alVolver = volver)
                    PantallaApp.ESTADO -> PantallaEstado(cat, p, sincronizando, alVolver = volver)
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
