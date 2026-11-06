package local.museumslideshow.galeria

import android.view.KeyEvent
import android.view.SurfaceView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.key
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.platform.LocalFontFamilyResolver
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.viewinterop.AndroidView
import local.museumslideshow.R
import local.museumslideshow.acomodo.Constantes
import local.museumslideshow.ajustes.Preferencias
import local.museumslideshow.ajustes.Tema
import local.museumslideshow.ajustes.estaTildada
import local.museumslideshow.reserva.GestorReserva
import androidx.compose.runtime.collectAsState
import local.museumslideshow.pantallas.CierreAutomatico
import local.museumslideshow.pantallas.ContextoPanel
import local.museumslideshow.pantallas.DatosPanel
import local.museumslideshow.pantallas.EstadoPanel
import local.museumslideshow.pantallas.EventoPanel
import local.museumslideshow.pantallas.IndicadorSincronizando
import local.museumslideshow.pantallas.ListaGaleriasPanel
import local.museumslideshow.pantallas.NavegacionPanel
import local.museumslideshow.pantallas.NivelPanel
import local.museumslideshow.pantallas.PanelLateral
import local.museumslideshow.pantallas.PantallaPreparando
import local.museumslideshow.pantallas.Tecla
import local.museumslideshow.pantallas.teclaDe
import local.museumslideshow.catalogo.Arbol
import kotlinx.coroutines.delay
import android.os.SystemClock
import androidx.compose.runtime.mutableFloatStateOf
import local.museumslideshow.catalogo.CatalogoCargado
import local.museumslideshow.sync.Sincronizacion

/** Lo que la Barra le pide a la app (la galería no sabe de ajustes guardados ni de sincronización). */
class AccionesApp(
    val cambiar: ((Preferencias) -> Preferencias) -> Unit,
    val sincronizar: () -> Unit,
    val abrirAjustes: () -> Unit,
)

/** Un pedido de saltar a una escena (cada instancia es un pedido nuevo, aunque el índice se repita). */
class SaltoEscena(val indice: Int)

/**
 * La galería en pantalla: la misma pieza para la app y para el protector. Dos capas con una sola medida:
 * una superficie 4K detrás (la obra, la pared) y esta interfaz transparente encima (marco, sombra, textos).
 * Maneja las teclas del control; las de volumen nunca las consume. `alSalir` se llama con Atrás cuando no hay nada abierto.
 */
@Composable
fun GaleriaEnPantalla(
    catalogo: CatalogoCargado,
    preferencias: Preferencias,
    alSalir: () -> Unit,
    modifier: Modifier = Modifier,
    salto: SaltoEscena? = null,
    /** Si es null no hay Barra (Abajo no hace nada). */
    app: AccionesApp? = null,
    sincronizando: Boolean = false,
    mensajeSync: String? = null,
    /** false mientras otra pantalla (Ajustes, Estado) está encima: el pase se pausa y las teclas no son de la galería. */
    teclasActivas: Boolean = true,
    /** La reserva de obras (null = sin reserva: todas las obras cuentan como bajadas). */
    reserva: GestorReserva? = null,
) {
    val ctx = LocalContext.current.applicationContext
    val resolver = LocalFontFamilyResolver.current
    val scope = rememberCoroutineScope()
    val salir by rememberUpdatedState(alSalir)

    val controlador = remember {
        val textMeasurer = TextMeasurer(resolver, Density(1f, 1f), LayoutDirection.Ltr, 8)
        val composicion = ComposicionTexto(textMeasurer, FuentesTexto(), formatosDe(ctx), ctx.getString(R.string.aviso_ok))
        val medidor = MedidorTextoCompose(composicion)
        ControladorGaleria(scope, ConstructorEscena(ctx, Sincronizacion.dirBase(ctx), medidor, composicion), { salir() }, reserva)
    }
    val control = remember { ControlGaleria({ controlador.estadoControl() }, { controlador.alAccion(it) }) }
    val pintor = remember { PintorInterfaz() }
    val foco = remember { FocusRequester() }

    LaunchedEffect(controlador) { controlador.correr() }
    LaunchedEffect(salto) { if (salto != null) controlador.saltarA(salto.indice) }
    // Cada vez que la reserva termina una obra, la galería reintenta (por si estaba esperando la primera).
    val versionReserva = reserva?.estado?.collectAsState()?.value?.version ?: 0
    LaunchedEffect(versionReserva) { controlador.reintentar() }
    LaunchedEffect(teclasActivas) { if (teclasActivas) try { foco.requestFocus() } catch (e: Exception) { /* todavía no está listo */ } }

    // La Barra: el panel de la izquierda. Su estado (menú / sublista / foco) es puro: ver NavegacionPanel.
    controlador.barraDisponible = app != null
    var panel by remember { mutableStateOf(EstadoPanel()) }
    val tildadas = remember(catalogo, preferencias.seleccionGalerias, preferencias.galeriasNuevas) {
        catalogo.galerias.filter { preferencias.estaTildada(it.driveId) }
    }
    val arbol = remember(catalogo) { Arbol(catalogo) }
    val items = remember(tildadas, arbol) { ListaGaleriasPanel.armar(tildadas) { arbol.titulo(it) } }
    val puesta = ListaGaleriasPanel.posicionElegida(items, preferencias.galeria)

    // Vista en vivo: con el foco sobre un tema, la galería se arma con ese tema SIN guardarlo (solo al mover el foco).
    // Al elegirlo se mantiene hasta que el ajuste guardado llegue (para que no parpadee el tema viejo).
    val temaEnPrueba = if (controlador.barraAbierta) panel.temaEnPrueba else null
    var temaElegido by remember { mutableStateOf<Tema?>(null) }
    LaunchedEffect(preferencias.tema) { temaElegido = null }
    val prefsGaleria = remember(preferencias, temaEnPrueba, temaElegido) {
        val t = temaEnPrueba ?: temaElegido
        if (t != null && t != preferencias.tema) preferencias.copy(tema = t) else preferencias
    }
    LaunchedEffect(catalogo, prefsGaleria) { controlador.configurar(catalogo, prefsGaleria) }

    val contexto = ContextoPanel(items, puesta, preferencias.tema)
    val contextoActual by rememberUpdatedState(contexto)
    val appActual by rememberUpdatedState(app)
    val teclasActual by rememberUpdatedState(teclasActivas)
    val prefsActual by rememberUpdatedState(preferencias)

    // Se cierra sola si no se toca ninguna tecla (cualquiera, menos el volumen, reinicia la cuenta).
    val limiteMs = preferencias.cierreBarra.segundos?.let { it * 1000L }
    val cierre = remember(limiteMs) { CierreAutomatico(limiteMs) { SystemClock.uptimeMillis() } }
    val cierreActual by rememberUpdatedState(cierre)
    var restante by remember { mutableFloatStateOf(1f) }

    fun cerrarPanel() {
        panel = EstadoPanel()          // el tema en prueba se descarta
        controlador.cerrarBarra()
    }
    LaunchedEffect(controlador.barraAbierta, cierre) {
        if (!controlador.barraAbierta) return@LaunchedEffect
        panel = EstadoPanel()
        cierre.reiniciar()
        while (true) {
            restante = cierre.restante() ?: 1f
            if (cierre.vencido()) { cerrarPanel(); break }
            delay(50)
        }
    }
    LaunchedEffect(controlador.barraAbierta, teclasActivas) { controlador.suspender(controlador.barraAbierta || !teclasActivas) }

    fun alEventoPanel(e: EventoPanel) {
        val a = appActual ?: return
        when (e) {
            EventoPanel.Cerrar -> cerrarPanel()
            is EventoPanel.ElegirGaleria -> { a.cambiar { it.copy(galeria = e.id) }; cerrarPanel() }
            is EventoPanel.ElegirTema -> {
                if (e.tema != prefsActual.tema) { temaElegido = e.tema; a.cambiar { it.copy(tema = e.tema) } }
                cerrarPanel()
            }
            EventoPanel.AlternarPantallaCompleta -> controlador.alternarPantallaCompleta()
            EventoPanel.AlternarExplicacion -> controlador.alternarExplicacionDesdeBarra()
            EventoPanel.AbrirAjustes -> { cerrarPanel(); a.abrirAjustes() }
            EventoPanel.Sincronizar -> a.sincronizar()
        }
    }

    /**
     * Las teclas con el panel abierto: las maneja la navegación pura; las de volumen nunca se consumen. Las flechas se repiten
     * solo en la lista de galerías (la de temas rearma la escena en cada paso).
     */
    fun teclaEnBarra(action: Int, keyCode: Int, repeatCount: Int): Boolean {
        if (esTeclaVolumen(keyCode)) return false
        if (action == KeyEvent.ACTION_DOWN) cierreActual.reiniciar()
        val tecla = teclaDe(keyCode) ?: return false
        if (action == KeyEvent.ACTION_DOWN) {
            val repite = repeatCount > 0
            val vertical = tecla == Tecla.ARRIBA || tecla == Tecla.ABAJO
            if (repite && !(vertical && panel.nivel == NivelPanel.GALERIAS)) return true
            val paso = NavegacionPanel.paso(panel, tecla, contextoActual)
            panel = paso.estado
            paso.evento?.let { alEventoPanel(it) }
        }
        return true
    }
    DisposableEffect(controlador) { onDispose { controlador.liberarTodo() } }

    Box(
        modifier
            .fillMaxSize()
            .onPreviewKeyEvent { ev ->
                val n = ev.nativeKeyEvent
                if (!teclasActual) false
                else if (controlador.barraAbierta) teclaEnBarra(n.action, n.keyCode, n.repeatCount)
                else control.alTecla(n.action, n.keyCode, n.repeatCount, n.downTime, n.eventTime, n.isLongPress)
            }
            .focusRequester(foco)
            .focusable(),
    ) {
        // Dos superficies 4K apiladas, las dos detrás de la ventana: B (con overlay) queda arriba de A.
        val calidadVista = preferencias.calidad
        key(calidadVista) {   // al cambiar la Calidad se recrean las dos vistas, con el tamaño nuevo desde el arranque
        AndroidView(
            factory = { c -> SurfaceView(c).also { controlador.superficie.enlazar(0, it, calidadVista) } },
            modifier = Modifier.fillMaxSize(),
        )
        AndroidView(
            factory = { c ->
                SurfaceView(c).also {
                    it.setZOrderMediaOverlay(true)
                    controlador.superficie.enlazar(1, it, calidadVista)
                }
            },
            modifier = Modifier.fillMaxSize(),
        )
        }
        // La interfaz: cada escena en su propia capa (se dibuja una vez y en el fundido solo cambia su alpha).
        for (e in listOfNotNull(controlador.anterior, controlador.actual)) {
            key(e) {
                Canvas(
                    Modifier.fillMaxSize().graphicsLayer {
                        alpha = if (e === controlador.anterior) 1f - controlador.avance
                        else if (controlador.anterior != null) controlador.avance else 1f
                        compositingStrategy = CompositingStrategy.Offscreen
                    },
                ) {
                    val k = size.width / Constantes.ANCHO
                    val esActual = e === controlador.actual
                    val elegida = controlador.elegida
                    val marca = controlador.marcaElegida
                    scale(k, k, Offset.Zero) { pintor.dibujar(this, e, 1f, if (esActual) elegida else 0, esActual && marca) }
                }
            }
        }
        if (controlador.preparando && controlador.actual == null) PantallaPreparando()
        if (sincronizando && !controlador.barraAbierta) IndicadorSincronizando(Modifier.align(Alignment.TopEnd))
        if (controlador.barraAbierta) {
            PanelLateral(
                DatosPanel(
                    estado = panel,
                    items = items,
                    galeriaPuesta = puesta,
                    tema = preferencias.tema,
                    pantallaCompleta = controlador.pantallaCompleta,
                    explicacion = controlador.explicacionVisible,
                    sincronizando = sincronizando,
                    mensaje = mensajeSync,
                    conCierre = limiteMs != null,
                    restante = { restante },
                ),
                Modifier.align(Alignment.CenterStart),
            )
        }
    }
}
