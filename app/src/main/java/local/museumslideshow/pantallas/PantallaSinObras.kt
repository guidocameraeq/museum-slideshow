package local.museumslideshow.pantallas

import android.view.KeyEvent
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import local.museumslideshow.R
import local.museumslideshow.galeria.esTeclaVolumen

/**
 * Cuando no hay ninguna obra para mostrar: explica qué pasa y qué hacer, con un botón "Ver Estado". Nunca una pantalla negra.
 * `activa` = tiene el foco del control (false si Estado está encima).
 */
@Composable
fun PantallaSinObras(
    motivo: MotivoSinObras,
    activa: Boolean,
    /** null = sin botón (el protector no tiene a dónde ir). */
    alVerEstado: (() -> Unit)?,
    alVolver: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val verEstado by rememberUpdatedState(alVerEstado)
    val volver by rememberUpdatedState(alVolver)
    val activaActual by rememberUpdatedState(activa)
    val pedirFoco = remember { FocusRequester() }
    LaunchedEffect(activa) { if (activa) try { pedirFoco.requestFocus() } catch (e: Exception) { /* todavía no está listo */ } }
    val titulo = stringResource(
        when (motivo) {
            MotivoSinObras.SINCRONIZANDO -> R.string.vacio_sincronizando_titulo
            MotivoSinObras.SIN_CONEXION -> R.string.vacio_sin_conexion_titulo
            MotivoSinObras.SIN_CLAVE -> R.string.vacio_sin_clave_titulo
            MotivoSinObras.CARPETA_VACIA -> R.string.vacio_carpeta_vacia_titulo
            MotivoSinObras.NINGUNA_TILDADA -> R.string.vacio_ninguna_tildada_titulo
        },
    )
    val explicacion = stringResource(
        when (motivo) {
            MotivoSinObras.SINCRONIZANDO -> R.string.vacio_sincronizando_texto
            MotivoSinObras.SIN_CONEXION -> R.string.vacio_sin_conexion_texto
            MotivoSinObras.SIN_CLAVE -> R.string.vacio_sin_clave_texto
            MotivoSinObras.CARPETA_VACIA -> R.string.vacio_carpeta_vacia_texto
            MotivoSinObras.NINGUNA_TILDADA -> R.string.vacio_ninguna_tildada_texto
        },
    )
    Escenario1080 {
        Column(
            modifier
                .fillMaxSize()
                .background(Paleta.Fondo)
                .padding(120.dp)
                .focusRequester(pedirFoco)
                .focusable()
                .onPreviewKeyEvent { ev ->
                    val n = ev.nativeKeyEvent
                    if (!activaActual || esTeclaVolumen(n.keyCode)) return@onPreviewKeyEvent false
                    val tecla = teclaDe(n.keyCode)
                    if (tecla == Tecla.OK || tecla == Tecla.ATRAS) {
                        if (n.action == KeyEvent.ACTION_DOWN && n.repeatCount == 0) {
                            if (tecla == Tecla.OK) verEstado?.invoke() else volver()
                        }
                        true
                    } else false
                },
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            BasicText(stringResource(R.string.app_name), style = estiloTexto(32, Paleta.Dorado, 600))
            Box(Modifier.padding(top = 40.dp)) {
                BasicText(titulo, style = estiloTexto(60, Paleta.Texto, 600).copy(textAlign = TextAlign.Center))
            }
            Box(Modifier.padding(top = 24.dp).widthIn(max = 1200.dp)) {
                BasicText(explicacion, style = estiloTexto(32, Paleta.Tenue, 400).copy(textAlign = TextAlign.Center, lineHeight = androidx.compose.ui.unit.TextUnit(46f, androidx.compose.ui.unit.TextUnitType.Sp)))
            }
            if (alVerEstado != null) Box(Modifier.padding(top = 56.dp)) {
                Boton(stringResource(R.string.vacio_ver_estado), conFoco = activa)
            }
        }
    }
}

/** El indicador discreto de "Sincronizando…": una pastilla chica en el rincón de arriba a la derecha, sin tapar obra ni cartel. */
@Composable
fun IndicadorSincronizando(modifier: Modifier = Modifier) {
    Escenario1080 {
        Box(
            modifier
                .padding(top = 22.dp, end = 26.dp)
                .clip(RoundedCornerShape(50))
                .background(androidx.compose.ui.graphics.Color(0x8C000000))
                .padding(horizontal = 18.dp, vertical = 8.dp),
        ) {
            BasicText(stringResource(R.string.estado_sincronizando), style = estiloTexto(20, androidx.compose.ui.graphics.Color(0xCCFFFFFF), 500), maxLines = 1)
        }
    }
}

/** "Preparando obras…": la galería se abrió pero todavía no llegó la primera obra de la reserva. Nunca una pantalla negra a secas. */
@Composable
fun PantallaPreparando(modifier: Modifier = Modifier) {
    Escenario1080 {
        Column(
            modifier.fillMaxSize().background(Paleta.Fondo).padding(120.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            BasicText(stringResource(R.string.app_name), style = estiloTexto(32, Paleta.Dorado, 600))
            Box(Modifier.padding(top = 40.dp)) {
                BasicText(stringResource(R.string.preparando_titulo), style = estiloTexto(60, Paleta.Texto, 600).copy(textAlign = TextAlign.Center))
            }
            Box(Modifier.padding(top = 24.dp).widthIn(max = 1200.dp)) {
                BasicText(stringResource(R.string.preparando_texto), style = estiloTexto(32, Paleta.Tenue, 400).copy(textAlign = TextAlign.Center))
            }
        }
    }
}
