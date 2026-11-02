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
import local.museumslideshow.ajustes.ListaAjustes
import local.museumslideshow.ajustes.Preferencias
import local.museumslideshow.ajustes.PreferenciasDesdeTexto
import local.museumslideshow.ajustes.SincronizarCada
import local.museumslideshow.catalogo.Catalogo
import local.museumslideshow.catalogo.CatalogoCargado
import local.museumslideshow.galeria.AccionesApp
import local.museumslideshow.galeria.GaleriaEnPantalla
import local.museumslideshow.galeria.SaltoEscena
import local.museumslideshow.pantallas.PantallaAjustes
import local.museumslideshow.pantallas.PantallaEstado
import local.museumslideshow.pantallas.PantallaSinObras
import local.museumslideshow.pantallas.SinObras
import local.museumslideshow.sync.ResultadoSync
import local.museumslideshow.sync.Sincronizacion
import local.museumslideshow.sync.SincronizarWorker

/** Qué pantalla de la app está al frente. La Barra no cuenta: vive dentro de la galería. */
private enum class Pantalla { GALERIA, AJUSTES, ESTADO }

class MainActivity : ComponentActivity() {
    private var catalogo by mutableStateOf<CatalogoCargado?>(null)
    private var sincronizando by mutableStateOf(false)
    private var mensajeSync by mutableStateOf<String?>(null)

    /** null hasta que se leen los ajustes guardados (un instante al abrir). */
    private var preferencias by mutableStateOf<Preferencias?>(null)
    private var salto by mutableStateOf<SaltoEscena?>(null)
    private var pantalla by mutableStateOf(Pantalla.GALERIA)
    private var estadoDesdeAjustes by mutableStateOf(false)

    private val almacen by lazy { AlmacenAjustes.de(applicationContext) }
    private var cadaProgramada: SincronizarCada? = null
    private var carpetaVista: String? = null
    private var primeraLectura = true
    private var repetirSync = false

    private val acciones = AccionesApp(
        cambiar = { t -> cambiarAjustes(t) },
        sincronizar = { sincronizarAhora() },
        abrirAjustes = { pantalla = Pantalla.AJUSTES },
    )

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
     */
    private suspend fun aplicarIntent(i: Intent?) {
        val extras = i?.extras ?: return
        val texto = HashMap<String, String>()
        for (k in extras.keySet()) {
            @Suppress("DEPRECATION")
            extras.get(k)?.toString()?.let { texto[k] = it }
        }
        if (texto.isEmpty()) return
        val reset = texto.containsKey("reset")
        almacen.cambiar { actual -> PreferenciasDesdeTexto.aplicar(if (reset) Preferencias() else actual, texto) }
        texto["escena"]?.toIntOrNull()?.let {
            salto = SaltoEscena(it)
            Log.i(Registro.TAG, "Salto a la escena $it")
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
        val items = remember(cat) { ListaAjustes.construir(cat?.galerias.orEmpty().map { GaleriaOpcion(it.driveId, it.titulo) }) }
        val ajustesVisible = pantalla == Pantalla.AJUSTES || (pantalla == Pantalla.ESTADO && estadoDesdeAjustes)
        Box(Modifier.fillMaxSize()) {
            if (cat != null && hayObras) {
                GaleriaEnPantalla(
                    cat, p,
                    alSalir = { finish() },
                    salto = salto,
                    app = acciones,
                    sincronizando = sincronizando,
                    mensajeSync = mensajeSync,
                    teclasActivas = pantalla == Pantalla.GALERIA,
                )
            } else {
                PantallaSinObras(
                    SinObras.motivo(sincronizando, cat),
                    activa = pantalla == Pantalla.GALERIA,
                    alVerEstado = { abrirEstado(desdeAjustes = false) },
                )
            }
            if (ajustesVisible) {
                PantallaAjustes(
                    items, p,
                    activa = pantalla == Pantalla.AJUSTES,
                    cambiar = { t -> cambiarAjustes(t) },
                    alAbrirEstado = { abrirEstado(desdeAjustes = true) },
                    alVolver = { pantalla = Pantalla.GALERIA },
                )
            }
            if (pantalla == Pantalla.ESTADO) {
                PantallaEstado(
                    cat, p, sincronizando,
                    alVolver = { pantalla = if (estadoDesdeAjustes) Pantalla.AJUSTES else Pantalla.GALERIA },
                )
            }
        }
    }

    private fun abrirEstado(desdeAjustes: Boolean) {
        estadoDesdeAjustes = desdeAjustes
        pantalla = Pantalla.ESTADO
    }

    private companion object {
        /** Cuánto queda en la Barra el resultado de la última sincronización. */
        const val MENSAJE_MS = 8000L
    }
}
