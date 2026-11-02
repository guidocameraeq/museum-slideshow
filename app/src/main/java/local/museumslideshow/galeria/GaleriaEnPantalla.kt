package local.museumslideshow.galeria

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
import local.museumslideshow.catalogo.CatalogoCargado
import local.museumslideshow.sync.Sincronizacion

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
) {
    val ctx = LocalContext.current.applicationContext
    val resolver = LocalFontFamilyResolver.current
    val scope = rememberCoroutineScope()
    val salir by rememberUpdatedState(alSalir)

    val controlador = remember {
        val textMeasurer = TextMeasurer(resolver, Density(1f, 1f), LayoutDirection.Ltr, 8)
        val composicion = ComposicionTexto(textMeasurer, FuentesTexto(), formatosDe(ctx), ctx.getString(R.string.aviso_ok))
        val medidor = MedidorTextoCompose(composicion)
        ControladorGaleria(scope, ConstructorEscena(ctx, Sincronizacion.dirBase(ctx), medidor, composicion)) { salir() }
    }
    val control = remember { ControlGaleria({ controlador.estadoControl() }, { controlador.alAccion(it) }) }
    val pintor = remember { PintorInterfaz() }
    val foco = remember { FocusRequester() }

    LaunchedEffect(controlador) { controlador.correr() }
    LaunchedEffect(catalogo, preferencias) { controlador.configurar(catalogo, preferencias) }
    LaunchedEffect(salto) { if (salto != null) controlador.saltarA(salto.indice) }
    LaunchedEffect(Unit) { try { foco.requestFocus() } catch (e: Exception) { /* todavía no está listo */ } }
    DisposableEffect(controlador) { onDispose { controlador.liberarTodo() } }

    Box(
        modifier
            .fillMaxSize()
            .onPreviewKeyEvent { ev ->
                val n = ev.nativeKeyEvent
                control.alTecla(n.action, n.keyCode, n.repeatCount, n.downTime, n.eventTime, n.isLongPress)
            }
            .focusRequester(foco)
            .focusable(),
    ) {
        // Dos superficies 4K apiladas, las dos detrás de la ventana: B (con overlay) queda arriba de A.
        AndroidView(
            factory = { c -> SurfaceView(c).also { controlador.superficie.enlazar(0, it) } },
            update = { controlador.superficie.fijarCalidad(preferencias.calidad) },
            modifier = Modifier.fillMaxSize(),
        )
        AndroidView(
            factory = { c ->
                SurfaceView(c).also {
                    it.setZOrderMediaOverlay(true)
                    controlador.superficie.enlazar(1, it)
                }
            },
            modifier = Modifier.fillMaxSize(),
        )
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
    }
}
